param(
    [string]$SourcesPath = "docs/operations/normative-sources.json",
    [string]$StatePath = "docs/operations/normative-state.json",
    [string]$ChangelogPath = "docs/operations/normative-changelog.md",
    [int]$TimeoutSeconds = 25
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-ContentHashHex {
    param([string]$Text)

    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
        $hash = $sha.ComputeHash($bytes)
        return ([System.BitConverter]::ToString($hash)).Replace("-", "").ToLowerInvariant()
    }
    finally {
        $sha.Dispose()
    }
}

function Ensure-File {
    param([string]$Path, [string]$DefaultContent)

    $dir = Split-Path -Parent $Path
    if ($dir -and -not (Test-Path -LiteralPath $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
    if (-not (Test-Path -LiteralPath $Path)) {
        Set-Content -LiteralPath $Path -Value $DefaultContent -Encoding UTF8
    }
}

Ensure-File -Path $StatePath -DefaultContent "{}"
Ensure-File -Path $ChangelogPath -DefaultContent "# Changelog normativo automatizado`n`n"

if (-not (Test-Path -LiteralPath $SourcesPath)) {
    throw "Arquivo de fontes nao encontrado: $SourcesPath"
}

$sources = Get-Content -LiteralPath $SourcesPath -Raw | ConvertFrom-Json
if ($null -eq $sources -or $sources.Count -eq 0) {
    throw "Nenhuma fonte configurada em $SourcesPath"
}

$stateRaw = Get-Content -LiteralPath $StatePath -Raw
$stateMap = @{}
if (-not [string]::IsNullOrWhiteSpace($stateRaw)) {
    $loaded = $stateRaw | ConvertFrom-Json
    if ($loaded -ne $null) {
        $loaded.PSObject.Properties | ForEach-Object {
            $stateMap[$_.Name] = $_.Value
        }
    }
}

$now = Get-Date
$changes = @()

foreach ($source in $sources) {
    $id = [string]$source.id
    $url = [string]$source.url

    if ([string]::IsNullOrWhiteSpace($id) -or [string]::IsNullOrWhiteSpace($url)) {
        continue
    }

    try {
        $response = Invoke-WebRequest -Uri $url -TimeoutSec $TimeoutSeconds -UseBasicParsing
        $content = [string]$response.Content
        $hash = Get-ContentHashHex -Text $content
        $statusCode = [int]$response.StatusCode

        $previous = $stateMap[$id]
        $previousHash = if ($previous) { [string]$previous.hash } else { "" }

        if ($hash -ne $previousHash) {
            $changes += [pscustomobject]@{
                id = $id
                url = $url
                oldHash = $previousHash
                newHash = $hash
                status = "CHANGED"
                checkedAt = $now.ToString("s")
                httpStatus = $statusCode
            }
        }

        $stateMap[$id] = [pscustomobject]@{
            hash = $hash
            checkedAt = $now.ToString("s")
            httpStatus = $statusCode
            url = $url
        }
    }
    catch {
        $msg = $_.Exception.Message
        $changes += [pscustomobject]@{
            id = $id
            url = $url
            oldHash = ""
            newHash = ""
            status = "ERROR"
            checkedAt = $now.ToString("s")
            httpStatus = -1
            error = $msg
        }
        if (-not $stateMap.ContainsKey($id)) {
            $stateMap[$id] = [pscustomobject]@{
                hash = ""
                checkedAt = $now.ToString("s")
                httpStatus = -1
                url = $url
                error = $msg
            }
        }
    }
}

# Persist state
$stateMap | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $StatePath -Encoding UTF8

# Append changelog only when there is signal (changes or errors)
if ($changes.Count -gt 0) {
    $lines = @()
    $lines += "## " + $now.ToString("yyyy-MM-dd HH:mm:ss")
    foreach ($change in $changes) {
        if ($change.status -eq "CHANGED") {
            $lines += "- [CHANGED] ``$($change.id)`` | HTTP $($change.httpStatus) | $($change.url)"
            $lines += "  - old: $($change.oldHash)"
            $lines += "  - new: $($change.newHash)"
        }
        else {
            $lines += "- [ERROR] ``$($change.id)`` | $($change.url)"
            $lines += "  - $($change.error)"
        }
    }
    $lines += ""
    Add-Content -LiteralPath $ChangelogPath -Value ($lines -join "`n") -Encoding UTF8
}

Write-Output "Normative check completed. Signals: $($changes.Count)."

