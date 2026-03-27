param(
    # Preset execution modes:
    #   full          – lint + assemble + unit tests (no connected)
    #   fast          – unit tests only (skip lint/assemble/connected)
    #   android-gate  – unit tests + connected instrumented tests (skip lint/assemble)
    #   prod-gate     – full (lint + assemble) + connected instrumented tests
    [ValidateSet("full", "fast", "android-gate", "prod-gate")]
    [string]$Mode = "full",

    [ValidateRange(0, 5)]
    [int]$GradleMaxRetries = 2,

    [ValidateRange(1, 120)]
    [int]$GradleRetryDelaySeconds = 8,

    # Max Gradle parallel workers. Default 0 = let Gradle decide (number of CPU cores).
    [ValidateRange(0, 32)]
    [int]$MaxWorkers = 0,

    [switch]$PrintConfig,
    [switch]$ShowStepTiming,
    [switch]$SummaryAsJson,
    [string]$SummaryJsonPath,
    [switch]$FailOnSummaryWriteError,
    [switch]$SkipAndroidLint,
    [switch]$SkipAndroidAssemble,
    [switch]$RunConnectedAndroidTests,
    [string]$ConnectedDeviceId,
    [switch]$SkipAnimationToggle
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Write-Host "[AUTO-MAX] Repo root: $repoRoot"

# ── mode-derived defaults ─────────────────────────────────────────────────
$modeDefaultsSkipLint     = $Mode -in @("fast", "android-gate")
$modeDefaultsSkipAssemble = $Mode -in @("fast", "android-gate")
$modeDefaultsConnected    = $Mode -in @("android-gate", "prod-gate")

$effectiveSkipAndroidLint = if ($PSBoundParameters.ContainsKey("SkipAndroidLint")) {
    [bool]$SkipAndroidLint
} else {
    $modeDefaultsSkipLint
}
$effectiveSkipAndroidAssemble = if ($PSBoundParameters.ContainsKey("SkipAndroidAssemble")) {
    [bool]$SkipAndroidAssemble
} else {
    $modeDefaultsSkipAssemble
}
$effectiveRunConnected = if ($PSBoundParameters.ContainsKey("RunConnectedAndroidTests")) {
    [bool]$RunConnectedAndroidTests
} else {
    $modeDefaultsConnected
}

# ── Gradle worker flag (empty string = let Gradle choose) ─────────────────
$gradleWorkersArg = if ($MaxWorkers -gt 0) { "--max-workers=$MaxWorkers" } else { "" }

$stageStatus = [ordered]@{
    AndroidUnitTests = "pending"
    AndroidLint = "pending"
    AndroidAssemble = "pending"
    AndroidTestCompile = "pending"
    AndroidConnectedTests = "pending"
    BackendInstall = "pending"
    BackendTests = "pending"
}

$runStartedAtUtc = (Get-Date).ToUniversalTime()
$runStopwatch = [System.Diagnostics.Stopwatch]::StartNew()
$currentStage = $null
$failedStage = $null
$runOutcome = "failure"
$runExitCode = 1
$runErrorMessage = $null
$runErrorType = $null
$runErrorDetails = $null
$summaryWriteStatus = "not_requested"
$summaryFilePath = $null
$summaryWriteError = $null

if ($PrintConfig) {
    Write-Host "[AUTO-MAX] Config: Mode=$Mode | GradleMaxRetries=$GradleMaxRetries | GradleRetryDelaySeconds=$GradleRetryDelaySeconds | MaxWorkers=$MaxWorkers | ShowStepTiming=$ShowStepTiming | SummaryAsJson=$SummaryAsJson | SummaryJsonPath=$SummaryJsonPath | FailOnSummaryWriteError=$FailOnSummaryWriteError | SkipAndroidLint=$SkipAndroidLint | SkipAndroidAssemble=$SkipAndroidAssemble | EffectiveSkipAndroidLint=$effectiveSkipAndroidLint | EffectiveSkipAndroidAssemble=$effectiveSkipAndroidAssemble | EffectiveRunConnected=$effectiveRunConnected | ConnectedDeviceId=$ConnectedDeviceId | SkipAnimationToggle=$SkipAnimationToggle"
}

function Invoke-Step {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][scriptblock]$Action
    )

    Write-Host "`n[AUTO-MAX] >>> $Name"
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    $global:LASTEXITCODE = 0
    & $Action
    if ($LASTEXITCODE -ne 0) {
        throw "Step '$Name' failed with exit code $LASTEXITCODE"
    }
    $watch.Stop()
    if ($ShowStepTiming) {
        Write-Host "[AUTO-MAX]     ${Name} duration: $($watch.Elapsed.ToString())"
    }
    Write-Host "[AUTO-MAX] <<< $Name OK"
}

function Test-RetryableGradleFailure {
    param(
        [Parameter(Mandatory = $true)][string]$Output
    )

    $retryPatterns = @(
        "daemon disappeared unexpectedly",
        "there is insufficient memory for the java runtime environment",
        "out of memory error",
        "java heap space",
        "unable to create native thread"
    )

    $normalizedOutput = $Output.ToLowerInvariant()
    foreach ($pattern in $retryPatterns) {
        if ($normalizedOutput.Contains($pattern)) {
            return $true
        }
    }
    return $false
}

function Invoke-GradleStepWithRetry {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][scriptblock]$Action,
        [int]$MaxRetries = 2,
        [int]$RetryDelaySeconds = 8
    )

    $attempt = 0
    $attemptLimit = $MaxRetries + 1

    while ($attempt -lt $attemptLimit) {
        $attempt++
        Write-Host "`n[AUTO-MAX] >>> $Name (attempt $attempt/$attemptLimit)"
        $watch = [System.Diagnostics.Stopwatch]::StartNew()

        $global:LASTEXITCODE = 0
        $outputLines = @()
        & $Action 2>&1 | ForEach-Object {
            $line = $_.ToString()
            $outputLines += $line
            Write-Host $line
        }

        if ($LASTEXITCODE -eq 0) {
            $watch.Stop()
            if ($ShowStepTiming) {
                Write-Host "[AUTO-MAX]     ${Name} duration: $($watch.Elapsed.ToString())"
            }
            Write-Host "[AUTO-MAX] <<< $Name OK"
            return
        }

        $watch.Stop()

        $fullOutput = $outputLines -join "`n"
        $canRetry = ($attempt -lt $attemptLimit) -and (Test-RetryableGradleFailure -Output $fullOutput)
        if ($canRetry) {
            Write-Host "[AUTO-MAX] !!! Falha transitória detectada em '$Name' (exit $LASTEXITCODE). Nova tentativa em $RetryDelaySeconds s..."
            Start-Sleep -Seconds $RetryDelaySeconds
            continue
        }

        throw "Step '$Name' failed with exit code $LASTEXITCODE"
    }
}

function Clear-ConnectedArtifacts {
    $paths = @(
        (Join-Path $repoRoot "app\build\outputs\androidTest-results\connected"),
        (Join-Path $repoRoot "app\build\reports\androidTests\connected")
    )

    foreach ($p in $paths) {
        if (-not (Test-Path -LiteralPath $p)) {
            continue
        }

        $removed = $false
        for ($attempt = 1; $attempt -le 3; $attempt++) {
            try {
                Remove-Item -LiteralPath $p -Recurse -Force -ErrorAction Stop
                $removed = $true
                break
            } catch {
                if ($attempt -lt 3) {
                    Start-Sleep -Seconds 2
                }
            }
        }

        if (-not $removed) {
            Write-Warning "[AUTO-MAX] Nao foi possivel limpar artefatos connected em '$p'. O Gradle pode falhar se houver lock de arquivo."
        }
    }
}

function Set-DeviceAnimations {
    param(
        [Parameter(Mandatory = $true)][string]$DeviceId,
        [Parameter(Mandatory = $true)][string]$Value
    )

    $commands = @(
        "settings put global window_animation_scale $Value",
        "settings put global transition_animation_scale $Value",
        "settings put global animator_duration_scale $Value"
    )

    foreach ($cmd in $commands) {
        $output = & adb -s $DeviceId shell $cmd 2>&1 | Out-String
        if ($output -match "SecurityException|Permission denial|WRITE_SECURE_SETTINGS") {
            Write-Warning "[AUTO-MAX] Sem permissao para alterar animacoes no device '$DeviceId' (WRITE_SECURE_SETTINGS). Continuando sem alterar animacoes."
            return $false
        }
    }

    return $true
}

Push-Location $repoRoot
try {
    $currentStage = "AndroidUnitTests"
    Invoke-GradleStepWithRetry -Name "Android unit tests" -Action {
        $a = @(":app:testDebugUnitTest", "--no-daemon")
        if ($gradleWorkersArg) { $a += $gradleWorkersArg }
        & .\gradlew.bat @a
    } -MaxRetries $GradleMaxRetries -RetryDelaySeconds $GradleRetryDelaySeconds
    $stageStatus.AndroidUnitTests = "executed"
    $currentStage = $null

    if (-not $effectiveSkipAndroidLint) {
        $currentStage = "AndroidLint"
        Invoke-GradleStepWithRetry -Name "Android lint (debug)" -Action {
            & .\gradlew.bat :app:lintDebug --no-daemon --max-workers=1
        } -MaxRetries $GradleMaxRetries -RetryDelaySeconds $GradleRetryDelaySeconds

        Invoke-GradleStepWithRetry -Name "Android lint (release)" -Action {
            & .\gradlew.bat :app:lintRelease --no-daemon --max-workers=1
        } -MaxRetries $GradleMaxRetries -RetryDelaySeconds $GradleRetryDelaySeconds
        $stageStatus.AndroidLint = "executed"
        $currentStage = $null
    } else {
        Write-Host "[AUTO-MAX] --- Android lint skipped (Mode=$Mode / SkipAndroidLint)"
        $stageStatus.AndroidLint = "skipped"
    }

    if (-not $effectiveSkipAndroidAssemble) {
        $currentStage = "AndroidAssemble"
        Invoke-GradleStepWithRetry -Name "Android assemble (debug)" -Action {
            & .\gradlew.bat :app:assembleDebug --no-daemon --max-workers=1
        } -MaxRetries $GradleMaxRetries -RetryDelaySeconds $GradleRetryDelaySeconds

        Invoke-GradleStepWithRetry -Name "Android assemble (release)" -Action {
            & .\gradlew.bat :app:assembleRelease --no-daemon --max-workers=1
        } -MaxRetries $GradleMaxRetries -RetryDelaySeconds $GradleRetryDelaySeconds
        $stageStatus.AndroidAssemble = "executed"
        $currentStage = $null
    } else {
        Write-Host "[AUTO-MAX] --- Android assemble skipped (Mode=$Mode / SkipAndroidAssemble)"
        $stageStatus.AndroidAssemble = "skipped"
    }

    # Optional: run connected instrumented tests if mode or flag requests it
    if ($effectiveRunConnected) {
        $currentStage = "AndroidTestCompile"
        Invoke-GradleStepWithRetry -Name "Android test compile preflight" -Action {
            & .\gradlew.bat :app:compileDebugAndroidTestKotlin --no-daemon --max-workers=1
        } -MaxRetries $GradleMaxRetries -RetryDelaySeconds $GradleRetryDelaySeconds
        $stageStatus.AndroidTestCompile = "executed"
        $currentStage = $null

        $currentStage = "AndroidConnectedTests"

        Write-Host "[AUTO-MAX] Checking for connected ADB devices..."
        $adbOutput = & adb devices 2>&1
        $devices = $adbOutput | Select-String "\tdevice$" | ForEach-Object { ($_ -split '\t')[0] }

        if ($devices.Count -gt 0) {
            Write-Host "[AUTO-MAX] Connected device(s): $($devices -join ', ')"

            $selectedDevice = if (-not [string]::IsNullOrWhiteSpace($ConnectedDeviceId)) {
                if ($devices -notcontains $ConnectedDeviceId) {
                    throw "ConnectedDeviceId '$ConnectedDeviceId' nao esta disponivel entre os devices conectados: $($devices -join ', ')"
                }
                $ConnectedDeviceId
            } else {
                $devices[0]
            }

            Write-Host "[AUTO-MAX] Using device for connected tests: $selectedDevice"
            $previousAndroidSerial = $env:ANDROID_SERIAL
            $env:ANDROID_SERIAL = $selectedDevice

            Clear-ConnectedArtifacts

            $animationsChanged = $false
            if (-not $SkipAnimationToggle) {
                $animationsChanged = Set-DeviceAnimations -DeviceId $selectedDevice -Value 0
            } else {
                Write-Host "[AUTO-MAX] Animation toggle skipped (SkipAnimationToggle)."
            }

            try {
                Invoke-GradleStepWithRetry -Name "Android connected tests (debug)" -Action {
                    & .\gradlew.bat :app:connectedDebugAndroidTest --no-daemon --max-workers=1
                } -MaxRetries $GradleMaxRetries -RetryDelaySeconds $GradleRetryDelaySeconds
                $stageStatus.AndroidConnectedTests = "executed"
            } finally {
                if ($animationsChanged) {
                    [void](Set-DeviceAnimations -DeviceId $selectedDevice -Value 1)
                }
                $env:ANDROID_SERIAL = $previousAndroidSerial
            }

            $currentStage = $null
        } else {
            Write-Host "[AUTO-MAX] No ADB devices found; skipping connected Android tests."
            # AndroidTestCompile already ran (status = "executed"); only connected tests are skipped.
            $stageStatus.AndroidConnectedTests = "skipped"
            $currentStage = $null
        }
    } else {
        Write-Host "[AUTO-MAX] --- Connected Android tests skipped (Mode=$Mode / RunConnectedAndroidTests not active)"
        $stageStatus.AndroidTestCompile = "skipped"
        $stageStatus.AndroidConnectedTests = "skipped"
    }

    $currentStage = "BackendInstall"
    Invoke-Step -Name "Backend install" -Action {
        Push-Location "$repoRoot\backend"
        try {
            npm ci
        } finally {
            Pop-Location
        }
    }
    $stageStatus.BackendInstall = "executed"
    $currentStage = $null

    $currentStage = "BackendTests"
    Invoke-Step -Name "Backend tests" -Action {
        Push-Location "$repoRoot\backend"
        try {
            npm test
        } finally {
            Pop-Location
        }
    }
    $stageStatus.BackendTests = "executed"
    $currentStage = $null

    Write-Host "`n[AUTO-MAX] Pipeline concluido com sucesso."
    Write-Host "[AUTO-MAX] Resumo de execucao:"
    Write-Host "[AUTO-MAX]   Mode=$Mode | EffectiveSkipAndroidLint=$effectiveSkipAndroidLint | EffectiveSkipAndroidAssemble=$effectiveSkipAndroidAssemble"
    Write-Host "[AUTO-MAX]   AndroidUnitTests=$($stageStatus.AndroidUnitTests) | AndroidLint=$($stageStatus.AndroidLint) | AndroidAssemble=$($stageStatus.AndroidAssemble)"
    Write-Host "[AUTO-MAX]   BackendInstall=$($stageStatus.BackendInstall) | BackendTests=$($stageStatus.BackendTests)"

    $runOutcome = "success"
    $runExitCode = 0

    Write-Host "[AUTO-MAX] APK debug: app\build\outputs\apk\debug\"
    Write-Host "[AUTO-MAX] APK release: app\build\outputs\apk\release\"
    Write-Host "[AUTO-MAX] Lint reports: app\build\reports\lint-results-*.html"
}
catch {
    $failedStage = $currentStage
    if (-not [string]::IsNullOrWhiteSpace($failedStage) -and $stageStatus.Contains($failedStage)) {
        $stageStatus[$failedStage] = "failed"
    }

    $runOutcome = "failure"
    $runExitCode = if ($LASTEXITCODE -ne 0) { $LASTEXITCODE } else { 1 }
    $runErrorMessage = $_.Exception.Message
    $runErrorType = $_.Exception.GetType().FullName
    $runErrorDetails = $_.ScriptStackTrace
    throw
}
finally {
    Pop-Location

    $runStopwatch.Stop()
    $buildSummary = {
        $summary = [ordered]@{
            schemaVersion = "1.3"
            outcome = $runOutcome
            exitCode = $runExitCode
            mode = $Mode
            effectiveSkipAndroidLint = $effectiveSkipAndroidLint
            effectiveSkipAndroidAssemble = $effectiveSkipAndroidAssemble
            effectiveRunConnected = $effectiveRunConnected
            failedStage = $failedStage
            startedAtUtc = $runStartedAtUtc.ToString("o")
            finishedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
            durationMs = [int][Math]::Round($runStopwatch.Elapsed.TotalMilliseconds)
            summaryWriteStatus = $summaryWriteStatus
            summaryFilePath = $summaryFilePath
            summaryWriteError = $summaryWriteError
            stages = $stageStatus
        }
        if (-not [string]::IsNullOrWhiteSpace($runErrorMessage)) {
            $summary.error = [ordered]@{
                type = $runErrorType
                message = $runErrorMessage
                details = $runErrorDetails
            }
        }
        return $summary
    }

    $summary = & $buildSummary
    $summaryJson = $summary | ConvertTo-Json -Compress -Depth 8

    if (-not [string]::IsNullOrWhiteSpace($SummaryJsonPath)) {
        try {
            $targetPath = $SummaryJsonPath
            if (-not [System.IO.Path]::IsPathRooted($targetPath)) {
                $targetPath = Join-Path $repoRoot $targetPath
            }
            $targetPath = [System.IO.Path]::GetFullPath($targetPath)
            $summaryFilePath = $targetPath
            $targetDir = Split-Path -Parent $targetPath
            if (-not [string]::IsNullOrWhiteSpace($targetDir) -and -not (Test-Path -LiteralPath $targetDir)) {
                New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
            }

            $summaryWriteStatus = "written"
            $summaryWriteError = $null
            $summary = & $buildSummary
            $summaryJson = $summary | ConvertTo-Json -Compress -Depth 8
            Set-Content -LiteralPath $targetPath -Value $summaryJson -Encoding UTF8
            Write-Host "[AUTO-MAX] Summary JSON salvo em: $targetPath"
        } catch {
            $summaryWriteStatus = "write_failed"
            $summaryWriteErrorMessage = "Falha ao salvar Summary JSON em '$SummaryJsonPath': $($_.Exception.Message)"
            $summaryWriteError = $summaryWriteErrorMessage
            $summary = & $buildSummary
            $summaryJson = $summary | ConvertTo-Json -Compress -Depth 8
            Write-Host "[AUTO-MAX] WARNING: $summaryWriteErrorMessage"
            if ($FailOnSummaryWriteError -and $runOutcome -eq "success") {
                throw $summaryWriteErrorMessage
            }
        }
    }

    if ($SummaryAsJson) {
        Write-Output $summaryJson
    }
}

