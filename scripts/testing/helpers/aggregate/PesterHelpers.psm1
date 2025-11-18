#Requires -Version 7.4

<#
.SYNOPSIS
    Aggregates individual helper scripts used by the Pester harness.

.DESCRIPTION
    Dot-sources the specific helper scripts so callers can simply `using module`
    `PesterHelpers.psm1` and get `Import-PesterModule`, `Get-KalmRepoRoot`, and
    `Resolve-PesterSettingsPath`.
#>

. (Join-Path $PSScriptRoot '..' 'module' 'Import-PesterModule.ps1')
. (Join-Path $PSScriptRoot '..' 'repo' 'Get-KalmRepoRoot.ps1')
Import-Module -Name (Join-Path $PSScriptRoot '..' 'settings' 'Resolve-PesterSettings.psm1') -Force
Import-Module -Name (Join-Path $PSScriptRoot '..' 'discovery' 'Discover-PesterTestFiles.psm1') -Force

$pesterHelperFunctions = @(
    'Import-PesterModule'
    'Get-KalmRepoRoot'
    'Resolve-PesterSettingsPath'
    'Get-PesterPatterns'
    'Get-PesterTestFiles'
    'Select-PesterTestFiles'
)

Export-ModuleMember -Function $pesterHelperFunctions
