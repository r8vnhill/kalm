<#
.SYNOPSIS
  Lightweight singleton for a process-wide "dry-run" flag.

.DESCRIPTION
  Provides a private, module-scoped store plus Set/Get helpers, avoiding true
  globals and expensive variable lookups. The flag is stored as an atomic 0/1.

.EXAMPLE
  Import-Module ./DryRunState.psm1
  Set-KalmDryRun $true
  if (Get-KalmDryRun) { 'would do X' } else { 'doing X' }
#>

# Private backing field (0 = false, 1 = true). Keep it module-scoped.
# Using int enables atomic updates via Interlocked.
$script:_KalmDryRun = 0

# Exported: set/get
function Set-KalmDryRun {
    [CmdletBinding()]
    [Diagnostics.CodeAnalysis.SuppressMessage('PSUseShouldProcessForStateChangingFunctions','',
        Justification='Toggles an internal config flag; no external side-effects.')]
    param(
        [Parameter(Mandatory)]
        [bool] $Value
    )
    # Atomic swap to handle potential parallel invocations safely.
    [void][System.Threading.Interlocked]::Exchange([ref]$script:_KalmDryRun, [int]$Value)
}

function Get-KalmDryRun {
    [CmdletBinding()]
    [OutputType([bool])]
    param()
    return [bool]$script:_KalmDryRun
}

# Internal test helper (not exported by default)
function Reset-KalmDryRun {
    [CmdletBinding(SupportsShouldProcess=$true)]
    param()
    if ($PSCmdlet.ShouldProcess('Kalm dry-run flag', 'Reset to false')) {
        [void][System.Threading.Interlocked]::Exchange([ref]$script:_KalmDryRun, 0)
    }
}

# Export the public helpers when module is imported
Export-ModuleMember -Function Set-KalmDryRun, Get-KalmDryRun
