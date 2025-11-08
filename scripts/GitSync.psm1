#Requires -Version 7.4
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
$libDir = Join-Path -Path $PSScriptRoot -ChildPath 'lib'
. (Join-Path -Path $libDir -ChildPath 'DryRunState.ps1')
. (Join-Path -Path $libDir -ChildPath 'GitInvoke.ps1')
. (Join-Path -Path $libDir -ChildPath 'GitHelpers.ps1')
. (Join-Path -Path $libDir -ChildPath 'GitSubmodule.ps1')

# Export all functions to preserve original API
Export-ModuleMember -Function *
