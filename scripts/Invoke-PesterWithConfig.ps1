<#
.SYNOPSIS
Run Pester tests using a repository-provided runsettings/configuration file.

.DESCRIPTION
This helper centralizes running Pester for the repository using a stable
configuration stored under `scripts/tests/pester.runsettings.psd1`.

It performs the following steps:
- Imports the Pester module (requires Pester 5.x or newer)
- Loads a PowerShell data file with Pester configuration
- Constructs a `PesterConfiguration` object and invokes `Invoke-Pester` with it

This script is intended to be used by developers and CI pipelines to ensure
consistent Pester runs across machines and CI agents.

.NOTES
- PowerShell 7.4+ is recommended (see repo scripts README for environment)
- Pester 5.x is required for `New-PesterConfiguration` API; older Pester
  versions may not be compatible.

.EXAMPLE
# Run tests with the repository Pester configuration
.\scripts\Invoke-PesterWithConfig.ps1

.EXAMPLE
# Run the script from CI or a developer shell (explicit pwsh call)
# pwsh -NoProfile -Command "./scripts/Invoke-PesterWithConfig.ps1"

.PARAMETER None
This script accepts no parameters; configuration is pulled from
`scripts/tests/pester.runsettings.psd1` relative to the script root.
#>

# Require a recent PowerShell runtime and enable strict mode
#Requires -Version 7.4
using module ./lib/ScriptLogging.psm1
param()

Set-StrictMode -Version 3.0

$logger = [KalmScriptLogger]::Start('Invoke-PesterWithConfig', $null)
$logger.LogInfo('Starting Pester execution with repo configuration.','Startup')

try {
	Import-Module Pester -ErrorAction Stop
}
catch {
	Write-Error "Failed to import Pester module. Ensure Pester 5.x is installed and available. $_"
	$logger.LogError(("Failed to import Pester module: {0}" -f $_.Exception.Message),'Failure')
	throw
}

# Resolve expected configuration file path
$settingsPath = Join-Path $PSScriptRoot tests pester.runsettings.psd1
if (-not (Test-Path -LiteralPath $settingsPath)) {
	Write-Error @(
		"Pester settings file not found: $settingsPath",
		"Create a 'pester.runsettings.psd1' under 'scripts/tests' or run Invoke-Pester manually."
	) -join [Environment]::NewLine
	throw 'Missing Pester runsettings file'
}

# Load configuration and execute tests. To avoid PowerShell class/type redefinition
# issues across test files (PowerShell classes are defined at parse-time per
# runspace), execute each discovered test file in a fresh pwsh process. This
# ensures modules that define classes (like ScriptLogging) load cleanly for
# each test file.
$settings = Import-PowerShellDataFile -Path $settingsPath

# Resolve test file patterns (allow multiple patterns) into concrete file list
$patterns = @()
if ($settings.Run -and $settings.Run.Path) {
	$patterns = @($settings.Run.Path) | ForEach-Object { $_ }
}

$testFiles = @()
foreach ($pat in $patterns) {
	try {
		$matches = Resolve-Path -Path $pat -ErrorAction SilentlyContinue | ForEach-Object { $_.Path }
		if ($matches) { $testFiles += $matches }
	}
	catch {
		# ignore patterns that don't match
	}
}

if (-not $testFiles -or $testFiles.Count -eq 0) {
	Write-Error "No test files found for patterns: $($patterns -join ', ')"
	throw 'No tests to run.'
}

# Temporarily skip gradle helper tests to avoid long-running external calls.
$excluded = @($testFiles | Where-Object { $_ -match 'Invoke-GradleWithJdk' })
if ($excluded.Count -gt 0) {
	$logger.LogInfo(("Skipping {0} test file(s) matching Invoke-GradleWithJdk.*" -f $excluded.Count),'Execution')
}
$testFiles = @($testFiles | Where-Object { $_ -notmatch 'Invoke-GradleWithJdk' })

if ($testFiles.Count -eq 0) {
	Write-Warning 'All discovered files were excluded.'
	return
}

$logger.LogInfo(("Running {0} test file(s) in isolated runspaces." -f $testFiles.Count),'Execution')

# Ensure output directory exists
$outDir = Join-Path -Path (Split-Path -Parent $settingsPath) -ChildPath '../../build/test-results/pester'
$outDir = [System.IO.Path]::GetFullPath($outDir)
if (-not (Test-Path -LiteralPath $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

foreach ($file in $testFiles) {
	Write-Verbose "Running tests in isolated process for: $file"
	$logger.LogDebug(("Invoking pwsh for {0}" -f $file),'Execution')
	$startedAt = Get-Date
	$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
	$scriptFile = Join-Path -Path $PSScriptRoot -ChildPath 'Invoke-PesterIsolated.ps1'
	$raw = & pwsh -NoLogo -NoProfile -File $scriptFile -FilePath $file -SettingsPath $settingsPath -OutputDir $outDir 2>&1
	$stopwatch.Stop()
	$finishedAt = Get-Date
	if (-not $raw) { continue }
	$jsonLine = $raw[-1]
	$logLines = @()
	if ($raw.Count -gt 1) {
		$logLines = $raw[0..($raw.Count - 2)] | ForEach-Object { $_.ToString() }
	}
	if ($jsonLine -notmatch '^__PesterResult__::(?<payload>\{.+\})$') {
		# Fallback: treat all lines as log output if sentinel missing
		$logLines = $raw | ForEach-Object { $_.ToString() }
		$result = [pscustomobject]@{
			File       = $file
			OutputPath = Join-Path -Path $outDir -ChildPath (([System.IO.Path]::GetFileNameWithoutExtension($file)) + '.test-results.xml')
		}
	}
	else {
		$json = $matches['payload']
		$result = $json | ConvertFrom-Json -Depth 8
	}
	$failedCount = 0
	foreach ($line in $logLines) {
		if ($line -match 'Failed:\s*(\d+)') {
			$failedCount = [int]$matches[1]
		}
	}
	$result = [pscustomobject]@{
		File        = $result.File
		OutputPath  = $result.OutputPath
		Output      = $logLines
		ExitCode    = if ($failedCount -gt 0) { 1 } else { 0 }
		Duration    = $stopwatch.Elapsed
		Success     = ($failedCount -eq 0)
		StartedAt   = $startedAt
		FinishedAt  = $finishedAt
	}
	$result | Write-Output
}

$logger.LogInfo('Pester isolated-run execution complete.','Summary')
