#Requires -Version 7.4

using module ..\..\lib\ScriptLogging.psm1

<#
.SYNOPSIS
    Aggregates the individual helper scripts used by the Pester harness.

.DESCRIPTION
    Dot-sources the specific helper scripts so callers can simply `using module`
    `PesterHelpers.psm1` and get `Import-PesterModule` / `Get-KalmRepoRoot`.
#>

. (Join-Path $PSScriptRoot 'Import-PesterModule.ps1')
. (Join-Path $PSScriptRoot 'Get-KalmRepoRoot.ps1')

Export-ModuleMember -Function Import-PesterModule, Get-KalmRepoRoot
