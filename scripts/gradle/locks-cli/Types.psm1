#Requires -Version 7.4
Set-StrictMode -Version 3.0

<#
.SYNOPSIS
    Structured output describing a Locks CLI invocation.

.DESCRIPTION
    LocksCliInvocationResult is the pipeline output object produced by Invoke-LocksCli.
    It captures invocation mode, execution intent, exit code (when executed),
    and execution spec (when pass-through mode is used).

.NOTES
    Property semantics:
    - ExitCode: populated when command execution occurs.
    - ExecutionSpec: populated when -PassThru is used.
    - Mode: 'Batch' or 'PerItem'.
    - Executed: true when execution was intended (even if WhatIf/Confirm blocks actual execution).
#>
class LocksCliInvocationResult {
    [Nullable[int]] $ExitCode
    [object] $ExecutionSpec
    [string] $Mode
    [bool] $Executed

    LocksCliInvocationResult(
        [Nullable[int]] $exitCode,
        [object] $executionSpec,
        [string] $mode,
        [bool] $executed
    ) {
        $this.ExitCode = $exitCode
        $this.ExecutionSpec = $executionSpec
        $this.Mode = $mode
        $this.Executed = $executed
    }
}
