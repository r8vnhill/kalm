#Requires -Version 7.4
using module ..\lib\ScriptLogging.psm1
<#
.SYNOPSIS
Lightweight loader module for Git helper components.

.DESCRIPTION
This file keeps a small compatibility surface and loads the split components
from `scripts/lib/` so other scripts can continue to `Import-Module GitSync.psm1`.
#>

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'

# Dot-source component scripts (keep load order: low-level helpers first)
# The lib components live under scripts/lib; compute that path relative to this
# module which now resides in scripts/git.
$libDir = Join-Path -Path (Join-Path $PSScriptRoot '..') -ChildPath 'lib'
# Load DryRunState as a module (prefer module import over dot-sourcing)
$dryRunModule = Join-Path -Path $libDir -ChildPath 'DryRunState.psm1'
if (Test-Path $dryRunModule) {
    Import-Module $dryRunModule -Force
}
else {
    Throw "Missing module: $dryRunModule"
}
. (Join-Path -Path $libDir -ChildPath 'GitInvoke.ps1')
. (Join-Path -Path $libDir -ChildPath 'GitHelpers.ps1')
. (Join-Path -Path $libDir -ChildPath 'GitSubmodule.ps1')

# Export all functions to preserve original API
Export-ModuleMember -Function *
