<#
.SYNOPSIS
Run a single test file using the repository Pester settings in an isolated pwsh process.

.DESCRIPTION
Invoked by Invoke-PesterWithConfig.ps1. Loads the repo pester settings, overrides
Run.Path and TestResult.OutputPath for the single file, invokes Invoke-Pester and
emits a sentinel JSON line prefixed with __PesterResult__:: so the caller can
separate logs from the per-file metadata.
#>

param(
    [Parameter(Mandatory=$true)][string] $FilePath,
    [Parameter(Mandatory=$true)][string] $SettingsPath,
    [Parameter(Mandatory=$true)][string] $OutputDir
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'

try {
    Import-Module Pester -ErrorAction Stop
}
catch {
    Write-Error "Failed to import Pester: $_"
    Exit 2
}

if (-not (Test-Path -LiteralPath $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

$resolvedFilePath = (Resolve-Path -LiteralPath $FilePath).ProviderPath
$fileDirectory = Split-Path -Parent $resolvedFilePath
$fileName = [System.IO.Path]::GetFileNameWithoutExtension($resolvedFilePath)
$perFileOutput = Join-Path -Path $OutputDir -ChildPath ($fileName + '.test-results.xml')

$settings = Import-PowerShellDataFile -Path $SettingsPath
if (-not ($settings -is [System.Collections.IDictionary])) { throw 'Pester settings file must define a hashtable.' }
if (-not $settings.ContainsKey('Run')) { $settings['Run'] = @{} }
$settings.Run['Path'] = @($resolvedFilePath)
if (-not $settings.ContainsKey('Output')) { $settings['Output'] = @{} }
$settings.Output['Verbosity'] = 'Detailed'
if (-not $settings.ContainsKey('TestResult')) { $settings['TestResult'] = @{} }
$settings.TestResult['OutputPath'] = $perFileOutput

$configuration = New-PesterConfiguration -Hashtable $settings

Push-Location -Path $fileDirectory
try {
    Invoke-Pester -Configuration $configuration
}
finally {
    Pop-Location
}

$metadata = [pscustomobject]@{
    File = $resolvedFilePath
    OutputPath = $perFileOutput
}

Write-Output "__PesterResult__::$(ConvertTo-Json -Depth 4 -Compress -InputObject $metadata)"

Exit 0
