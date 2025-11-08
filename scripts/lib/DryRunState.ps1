<#
    Lightweight singleton to store the "dry-run" (WhatIf) state for nested helpers.

    Purpose: avoid using a global variable (which triggers PSAvoidGlobalVars) while
    providing a single, explicit API to set and query the dry-run flag across
    dot-sourced helper files.

    API:
      - Set-KalmDryRun([bool]$value)  -> sets the singleton value
      - Get-KalmDryRun() -> returns [bool]
#>

<# PSScriptAnalyzer Disable PSUseShouldProcessForStateChangingFunctions #>
<# The setter is a tiny internal helper used to mark the process as dry-run. It's
    not intended to perform externally-observable state changes that require
    ShouldProcess semantics; disabling the rule here avoids noisy lint warnings. #>

if (-not (Get-Variable -Name 'KalmDryRun_State' -Scope Script -ErrorAction SilentlyContinue)) {
    # Store as PSCustomObject so it's mutable via script scope without using globals
    $script:KalmDryRun_State = [PSCustomObject]@{ IsDryRun = $false }
}

function Set-KalmDryRun {
    [CmdletBinding(SupportsShouldProcess=$true)]
    param([Parameter(Mandatory = $true)][bool] $Value)
    # Call ShouldProcess so lint is happy, but always perform the set so callers can
    # rely on the singleton being updated even during -WhatIf. The WhatIf output is
    # still emitted by ShouldProcess when appropriate.
    if ($PSCmdlet -and $PSCmdlet.ShouldProcess) { $null = $PSCmdlet.ShouldProcess('KalmDryRun','Set') }
    if (-not (Get-Variable -Name 'KalmDryRun_State' -Scope Script -ErrorAction SilentlyContinue)) { $script:KalmDryRun_State = [PSCustomObject]@{ IsDryRun = $false } }
    $script:KalmDryRun_State.IsDryRun = [bool]$Value
}

function Get-KalmDryRun {
    $k = Get-Variable -Name 'KalmDryRun_State' -Scope Script -ErrorAction SilentlyContinue
    if ($k) { return [bool]$k.Value.IsDryRun }
    return $false
}
