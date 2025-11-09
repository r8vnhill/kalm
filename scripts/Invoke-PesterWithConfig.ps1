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
param()

Set-StrictMode -Version 3.0

try {
	Import-Module Pester -ErrorAction Stop
}
catch {
	Write-Error "Failed to import Pester module. Ensure Pester 5.x is installed and available. $_"
	throw
}

# Resolve expected configuration file path
$settingsPath = Join-Path $PSScriptRoot 'tests/pester.runsettings.psd1'
if (-not (Test-Path -LiteralPath $settingsPath)) {
	Write-Error "Pester settings file not found: $settingsPath`nCreate a 'pester.runsettings.psd1' under 'scripts/tests' or run Invoke-Pester manually."
	throw "Missing Pester runsettings file"
}

# Load configuration and execute tests
$settings = Import-PowerShellDataFile -Path $settingsPath
$config = New-PesterConfiguration -Hashtable $settings

Write-Verbose "Invoking Pester with configuration from: $settingsPath"
Invoke-Pester -Configuration $config
