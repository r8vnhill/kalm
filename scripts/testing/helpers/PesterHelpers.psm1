#Requires -Version 7.4

using module ..\..\lib\ScriptLogging.psm1

<#
.SYNOPSIS
    Aggregates individual helper scripts used by the Pester harness.

.DESCRIPTION
    Dot-sources the specific helper scripts so callers can simply `using module`
    `PesterHelpers.psm1` and get `Import-PesterModule`, `Get-KalmRepoRoot`, and
    `Resolve-PesterSettingsPath`.
#>

. (Join-Path $PSScriptRoot 'Import-PesterModule.ps1')
. (Join-Path $PSScriptRoot 'Get-KalmRepoRoot.ps1')
Import-Module -Name (Join-Path $PSScriptRoot 'Resolve-PesterSettings.psm1') -Force

$pesterHelperFunctions = @(
    'Import-PesterModule'
    'Get-KalmRepoRoot'
    'Resolve-PesterSettingsPath'
    'Get-PesterPatterns'
)

Export-ModuleMember -Function $pesterHelperFunctions
