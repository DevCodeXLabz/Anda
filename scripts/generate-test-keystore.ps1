<#
Generates a test keystore in the project root and a matching keystore.properties
Usage (PowerShell):
    .\scripts\generate-test-keystore.ps1

Notes:
- Requires `keytool` available on PATH (JDK). If not in PATH, run from JDK bin folder or provide absolute keytool path.
- This script is intended for local development only. Do NOT commit the generated keystore or the real keystore.properties.
#>

param(
    [string]$KeystoreFile = "test-keystore.jks",
    [string]$StorePassword = "testpass",
    [string]$KeyAlias = "testkey",
    [string]$KeyPassword = "testpass",
    [string]$DName = 'CN=Test, OU=Dev, O=Example, L=City, S=State, C=BR'
)

function Fail($msg) {
    Write-Error $msg
    exit 1
}

$projectRoot = Get-Location
Set-Location $projectRoot

Write-Output "Project root: $projectRoot"

# Try to locate keytool: prefer PATH, otherwise look under JAVA_HOME\bin
$keytoolCmd = $null
if (Get-Command keytool -ErrorAction SilentlyContinue) {
    $keytoolCmd = "keytool"
} elseif ($env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME "bin\keytool.exe"
    if (Test-Path $candidate) { $keytoolCmd = $candidate }
}

$keystorePath = Join-Path $projectRoot $KeystoreFile
if (Test-Path $keystorePath) {
    Write-Output "Keystore already exists at $keystorePath - leaving as is."
} elseif ($keytoolCmd) {
    Write-Output "Generating keystore: $keystorePath using: $keytoolCmd"
    & $keytoolCmd -genkeypair -v -keystore $keystorePath -storepass $StorePassword -alias $KeyAlias -keyalg RSA -keysize 2048 -validity 3650 -dname $DName -keypass $KeyPassword
    if ($LASTEXITCODE -ne 0) {
        Fail "keytool failed to generate keystore. Exit code: $LASTEXITCODE"
    }
    Write-Output "Keystore generated: $keystorePath"
} else {
    Write-Warning "keytool not found in PATH or JAVA_HOME. Skipping keystore binary generation. You can still create keystore.properties and generate a keystore manually using your JDK's keytool."
}

$propsPath = Join-Path $projectRoot 'keystore.properties'
if (Test-Path $propsPath) {
    Write-Output "keystore.properties already exists at $propsPath - leaving as is."
} else {
    $content = @()
    $content += "storeFile=$KeystoreFile"
    $content += "storePassword=$StorePassword"
    $content += "keyAlias=$KeyAlias"
    $content += "keyPassword=$KeyPassword"
    $content | Out-File -FilePath $propsPath -Encoding UTF8
    Write-Output "Created keystore.properties at $propsPath"
}

Write-Output "Done. Remember: do NOT commit test-keystore.jks or keystore.properties to version control."

