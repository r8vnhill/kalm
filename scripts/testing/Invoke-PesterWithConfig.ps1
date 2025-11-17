<#
.SYNOPSIS
    Run Pester tests using a repository-provided runsettings/configuration file.

.DESCRIPTION
    This helper centralizes running Pester for the repository using a stable configuration stored
    under `scripts/testing/pester.config.psd1`.

    It performs the following steps:
    - Imports the Pester module (requires Pester 5.x or newer)
    - Loads a PowerShell data file with Pester configuration
    - Constructs a `PesterConfiguration` object and invokes `Invoke-Pester` with it

    This script is intended to be used by developers and CI pipelines to ensure consistent Pester
    runs across machines and CI agents.

.NOTES
    - PowerShell 7.4+ is required (see repo scripts README for environment)
    - Pester 5.x is required for `New-PesterConfiguration` API; older Pester versions may not be
        compatible.

.EXAMPLE
    ```powershell
    # Run tests with the repository Pester configuration
    .\scripts\testing\Invoke-PesterWithConfig.ps1
    ```
#>

# Require a recent PowerShell runtime and enable strict mode
#Requires -Version 7.4

using module ..\lib\ScriptLogging.psm1
using module .\helpers\PesterHelpers.psm1

param()

Set-StrictMode -Version 3.0

$logger = [KalmScriptLogger]::Start('Invoke-PesterWithConfig', $null)
$logger.AddConsoleSink()
$logger.LogInfo('Starting Pester execution with repo configuration.', 'Startup')

Import-PesterModule -ModuleName 'Pester' -Logger $logger

$settingsPath = Resolve-PesterSettingsPath -ScriptRoot $PSScriptRoot

# Load configuration and execute tests. To avoid PowerShell class/type redefinition issues across
# test files (PowerShell classes are defined at parse-time per runspace), execute each discovered
# test file in a fresh pwsh process. This ensures modules that define classes (like ScriptLogging)
# load cleanly for each test file.
$settings = Import-PowerShellDataFile -Path $settingsPath

function Resolve-TestFiles {
    param([string[]] $Patterns)
    return $Patterns | ForEach-Object {
        try {
            Resolve-Path -Path $_ -ErrorAction Stop | ForEach-Object { $_.Path }
        }
        catch {
            # ignore missing patterns
        }
    } | Where-Object { $_ } | Sort-Object -Unique
}

function Select-PesterTestFiles {
    param(
        [string[]] $Files,
        [string[]] $ExcludePatterns = @('Invoke-GradleWithJdk')
    )

    return $Files | Where-Object {
        $excluded = $false
        foreach ($pattern in $ExcludePatterns) {
            if ($_ -match $pattern) {
                $excluded = $true
                break
            }
        }
        -not $excluded
    }
}

function Convert-PesterOutputToResult {
    param(
        [string[]] $Raw,
        [string] $File,
        [string] $OutputDir,
        [System.TimeSpan] $Duration,
        [datetime] $StartedAt,
        [datetime] $FinishedAt
    )

    $logLines = @()
    if ($Raw.Count -gt 1) {
        $logLines = $Raw[0..($Raw.Count - 2)] | ForEach-Object { $_.ToString() }
    }

    $jsonLine = $Raw[-1]
    if ($jsonLine -match '^__PesterResult__::(?<payload>\{.+\})$') {
        $result = ($matches['payload'] | ConvertFrom-Json -Depth 8)
    }
    else {
        $logLines = $Raw | ForEach-Object { $_.ToString() }
        $result = [pscustomobject]@{
            File       = $File
            OutputPath = Join-Path -Path $OutputDir -ChildPath (([System.IO.Path]::GetFileNameWithoutExtension($File)) + '.test-results.xml')
        }
    }

    $failedCount = 0
    foreach ($line in $logLines) {
        if ($line -match 'Failed:\s*(\d+)') {
            $failedCount = [int]$matches[1]
            break
        }
    }

    return [pscustomobject]@{
        File       = $result.File
        OutputPath = $result.OutputPath
        Output     = $logLines
        ExitCode   = if ($failedCount -gt 0) { 1 } else { 0 }
        Duration   = $Duration
        Success    = ($failedCount -eq 0)
        StartedAt  = $StartedAt
        FinishedAt = $FinishedAt
    }
}

function Invoke-IsolatedTest {
    param(
        [string] $File,
        [string] $SettingsPath,
        [string] $OutputDir,
        [KalmScriptLogger] $Logger
    )

    $Logger.LogDebug(('Invoking pwsh for {0}' -f $File), 'Execution')
    $scriptFile = Join-Path -Path $PSScriptRoot -ChildPath 'helpers\Invoke-PesterIsolated.ps1'
    $startTime = Get-Date
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $rawOutput = & pwsh -NoLogo -NoProfile -File $scriptFile -FilePath $File -SettingsPath $SettingsPath -OutputDir $OutputDir 2>&1
    $stopwatch.Stop()
    $finishTime = Get-Date

    if (-not $rawOutput) { return }
    return Convert-PesterOutputToResult -Raw $rawOutput -File $File -OutputDir $OutputDir `
        -Duration $stopwatch.Elapsed -StartedAt $startTime -FinishedAt $finishTime
}

$patterns = Get-PesterPatterns -Settings $settings
if (@($patterns).Count -eq 0) {
    Write-Error 'No test file patterns defined in Pester settings.'
    throw 'No patterns configured.'
}

$testFiles = Resolve-TestFiles -Patterns $patterns
$testFiles = Select-PesterTestFiles -Files $testFiles

if (@($testFiles).Count -eq 0) {
    Write-Error "No test files found for patterns: $($patterns -join ', ')"
    throw 'No tests to run.'
}

$logger.LogInfo(('Running {0} test file(s) in isolated runspaces.' -f $testFiles.Count), 'Execution')

$outDir = Join-Path -Path (Split-Path -Parent $settingsPath) -ChildPath '../../build/test-results/pester'
$outDir = [System.IO.Path]::GetFullPath($outDir)
if (-not (Test-Path -LiteralPath $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

$testFiles | ForEach-Object { Invoke-IsolatedTest -File $_ -SettingsPath $settingsPath -OutputDir $outDir -Logger $logger } |
    Write-Output

$logger.LogInfo('Pester isolated-run execution complete.', 'Summary')
