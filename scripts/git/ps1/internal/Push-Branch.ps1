<#
.SYNOPSIS
Pushes the current branch to a remote using the provided git command.

.DESCRIPTION
Internal helper used by Invoke-GitSync to perform a safe, parameterized `git push`.
This function is intentionally NOT exported publicly to discourage pushing without a prior pull/sync step.
It assembles the git arguments in a predictable order, supports common switches like --force-with-lease and --follow-tags, and honors -WhatIf / -Confirm via SupportsShouldProcess.

.NOTES
- Scope: Internal-scoped and not exported; used internally by Invoke-GitSync. This is meant to discourage pushing without a prior pull/sync step.
- Error handling: If git exits with a non-zero code, the function throws including the assembled command and captured stderr/stdout to aid diagnostics.
- Telemetry: Use -Verbose to see the fully assembled git command and progress.

.PARAMETER GitCommand
A CommandInfo for the git executable (e.g., (Get-Command git)).
Accepting CommandInfo allows callers to inject a specific git (shim, wrapper, or a path-resolved binary).

.PARAMETER RepositoryPath
Path to the repository root.
Passed to git as `-C <path>` so the working directory does not need to be changed by the caller.

.PARAMETER CurrentBranch
The branch name to push (e.g., 'main' or 'feature/foo').

.PARAMETER PushRemote
The target remote (e.g., 'origin' or 'upstream').

.PARAMETER SetUpstream
Adds `-u` so future pushes may omit the explicit upstream.

.PARAMETER ForceWithLease
Adds `--force-with-lease` to force safely (refuses if remote advanced independently).

.PARAMETER FollowTags
Adds `--follow-tags` to push annotated tags that reference the pushed commits.

.PARAMETER Tags
Adds `--tags` to push all tags as well as the specified branch to the remote.

.PARAMETER ExtraArgs
Any additional arguments to append verbatim to the git push command.

.PARAMETER PassThru
When set, returns git's textual output (stdout/stderr merged) instead of being silent.

.OUTPUTS
[string]
Only when -PassThru is used; otherwise no output on success, exception on failure.

.EXAMPLE
# Preview push (no side effects) with verbose details
Push-Branch -GitCommand (Get-Command git) -RepositoryPath $pwd `
  -CurrentBranch 'main' -PushRemote 'origin' -SetUpstream -WhatIf -Verbose

.EXAMPLE
# Force-with-lease, following tags, return git output
Push-Branch -GitCommand (Get-Command git) -RepositoryPath 'C:\repo' `
  -CurrentBranch 'feature/foo' -PushRemote 'origin' -ForceWithLease -FollowTags -PassThru

.LINK
Invoke-GitSync
#>
function Push-Branch {
    [CmdletBinding(SupportsShouldProcess, ConfirmImpact = 'Medium')]
    [OutputType([string])]
    param (
        [Parameter(Mandatory)]
        [System.Management.Automation.CommandInfo] $GitCommand,

        [Parameter(Mandatory)]
        [ValidateNotNullOrEmpty()]
        [ValidateScript({
                if (-not (Test-Path -LiteralPath $_)) {
                    throw "RepositoryPath '$_' does not exist."
                }
                $true
            })]
        [string] $RepositoryPath,

        [Parameter(Mandatory)]
        [ValidateNotNullOrEmpty()]
        [string] $CurrentBranch,

        [Parameter(Mandatory)]
        [ValidateNotNullOrEmpty()]
        [string] $PushRemote,

        [switch] $SetUpstream,

        [switch] $ForceWithLease,

        [switch] $FollowTags,

        [switch] $Tags,

        [string[]] $ExtraArgs,

        [switch] $PassThru
    )

    # Build the git argument list deterministically.
    $gitArgs = @('-C', $RepositoryPath, 'push')

    if ($SetUpstream) { $gitArgs += '-u' }
    if ($ForceWithLease) { $gitArgs += '--force-with-lease' }
    if ($FollowTags) { $gitArgs += '--follow-tags' }
    if ($Tags) { $gitArgs += '--tags' }

    # Refspec: remote and the local branch to push.
    $gitArgs += @($PushRemote, $CurrentBranch)

    # Append any advanced/custom flags last so callers can extend behavior.
    if ($ExtraArgs) { $gitArgs += $ExtraArgs }

    $preview = "git $($gitArgs -join ' ')"

    if (-not $PSCmdlet.ShouldProcess($RepositoryPath, $preview)) {
        return
    }

    Write-Verbose "📦 Repository : $RepositoryPath"
    Write-Verbose "🌿 Branch     : $CurrentBranch"
    Write-Verbose "🌐 Remote     : $PushRemote"
    Write-Verbose "▶️  Command    : $preview"

    # Invoke git and capture combined stdout/stderr for diagnostics.
    $output = & $GitCommand @gitArgs 2>&1
    $exit = $LASTEXITCODE

    if ($exit -ne 0) {
        throw "❌ Push failed (exit $exit). Command: $preview`n$output"
    }

    Write-Verbose '✅ Push completed successfully.'
    if ($PassThru) { $output }
}
