<#
.SYNOPSIS
    Returns the current Git branch name for a repository, with configurable behavior for
    detached `HEAD`.

.DESCRIPTION
    High-level convenience wrapper that:
    1) Ensures a usable Git command is available (or throws with a clear message),
    2) Resolves and validates the repository path (directory, and optionally a Git repo),
    3) Delegates to the internal helper `Script:Get-CurrentBranchNameInternal` to obtain:
        - the current branch name,
        - or, when `HEAD` is detached, a value determined by `-DetachedBehavior`:
            * 'Error' (default): throw an error,
            * 'Name'          : returns 'HEAD',
            * 'Sha'           : returns the short commit SHA of `HEAD`,
            * 'Null'          : returns $null.

.PARAMETER GitCommand
    Resolved Git command to invoke, e.g. `(Get-Command git)`. If omitted, the function
    attempts to locate `git` automatically. A custom CommandInfo can be injected to use a
    shim or wrapper.

.PARAMETER RepositoryPath
    Path to the repository root. Defaults to the current location. The path is resolved
    with `Convert-RepositoryPath` to a literal full path and validated as a directory;
    when `-EnsureGitRepository` is present, it is also validated to be a Git repository.

.PARAMETER DetachedBehavior
    How to behave when the repository is in a detached `HEAD` state:
    - `Error` (default): throw,
    - `Name`: return the literal string 'HEAD',
    - `Sha`: return the short commit SHA,
    - `Null`: return $null.

.PARAMETER EnsureGitRepository
    When provided, verifies that the resolved path is a Git repository before proceeding.

.OUTPUTS
    [string]
    Returns the branch name, `HEAD` (if `-DetachedBehavior Name`), or a short SHA (if
    `-DetachedBehavior Sha`). Returns `$null` when `-DetachedBehavior Null`. Throws on
    error (missing git; invalid path; detached with `-DetachedBehavior Error`).

.EXAMPLE
    # Get the current branch in the current directory; throw if HEAD is detached
    Get-CurrentBranchName

.EXAMPLE
    # Get the current branch for a specific repository path
    Get-CurrentBranchName -RepositoryPath 'C:\src\my-repo'

.EXAMPLE
    # Return 'HEAD' instead of throwing when detached
    Get-CurrentBranchName -DetachedBehavior Name

.EXAMPLE
    # Validate that the path is a Git repository before reading the branch
    Get-CurrentBranchName -RepositoryPath '/repos/app' -EnsureGitRepository

.LINK
    Convert-RepositoryPath
.LINK
    Script:Get-CurrentBranchNameInternal
#>
function Get-CurrentBranchName {
    [CmdletBinding()]
    [OutputType([string])]
    param (
        [System.Management.Automation.CommandInfo]
        $GitCommand,

        [ValidateNotNullOrEmpty()]
        [string]
        $RepositoryPath = (Get-Location).Path,

        [ValidateSet('Error', 'Name', 'Sha', 'Null')]
        [string]
        $DetachedBehavior = 'Error',

        [switch] $EnsureGitRepository,

        [switch] $SkipGitCommandCheck
    )

    # Ensure we have a usable git command (prefer caller-provided; fall back to
    # auto-detection).
    if (-not $SkipGitCommandCheck -and -not $GitCommand) {
        $GitCommand = Get-Command git -ErrorAction SilentlyContinue
        if (-not $GitCommand) {
            throw '❌ Git is required to determine the current branch.'
        }
        if (-not $GitCommand) {
            throw '❌ Git is required to determine the current branch.'
        }
    }

    # Resolve and validate the repository path:
    # - Always ensure it resolves to a directory,
    # - Optionally verify it is a Git repository when -EnsureGitRepository is present.
    $RepositoryPath = Convert-RepositoryPath `
        -GitCommand $GitCommand `
        -RepositoryPath $RepositoryPath `
        -EnsureDirectory `
        -EnsureGitRepository:$EnsureGitRepository

    Write-Verbose "🔎 Determining current branch in '$RepositoryPath'..."

    Script:Get-CurrentBranchNameInternal `
        -GitCommand $GitCommand `
        -RepositoryPath $RepositoryPath `
        -DetachedBehavior $DetachedBehavior
}

<#
.SYNOPSIS
    Returns the current branch name for a repository, with configurable behavior for
    detached `HEAD`.

.DESCRIPTION
    Internal helper that queries Git for the current branch name using:
    `git -C <RepositoryPath> symbolic-ref --short -q HEAD`

    If the repository is in a detached `HEAD` state, behavior is controlled by
    `-DetachedBehavior`:
    - `Error` (default): throw an error.
    - `Name`: return the literal string `HEAD`.
    - `Sha`: resolve and return the short commit SHA of `HEAD`.
    - `Null`: return $null.

    This function is intended for internal use (script-scoped) and is typically called by
    higher-level commands that already validated the repository path (e.g., using
    `Convert-RepositoryPath -EnsureDirectory -EnsureGitRepository`).

.PARAMETER GitCommand
    A resolved git command (e.g., (`Get-Command git`)), allowing callers to inject a
    specific binary, wrapper, or shim.

.PARAMETER RepositoryPath
    Absolute or relative path to the repository root. Passed to git via `-C` so the caller
    does not need to change the current location.

.PARAMETER DetachedBehavior
    Controls what to return (or whether to error) when the repository is in a detached
    `HEAD` state.
    Allowed values: `Error`, `Name`, `Sha`, `Null`. Default is `Error`.

.OUTPUTS
    [string]
    Returns a trimmed branch name, `HEAD` (when `DetachedBehavior is `Name`), or a short
    SHA (when `DetachedBehavior is `Sha`). Returns `$null` when `DetachedBehavior is
    `Null`. Throws on failure when `DetachedBehavior is `Error` or when resolving the SHA
    fails.

.EXAMPLE
    # Get the current branch name, error if detached
    Script:Get-CurrentBranchNameInternal -GitCommand (Get-Command git) -RepositoryPath $pwd

.EXAMPLE
    # Get 'HEAD' when detached instead of throwing
    Script:Get-CurrentBranchNameInternal -GitCommand (Get-Command git) -RepositoryPath $pwd -DetachedBehavior Name

.EXAMPLE
    # Get the short commit SHA when detached
    Script:Get-CurrentBranchNameInternal -GitCommand (Get-Command git) -RepositoryPath $pwd -DetachedBehavior Sha
#>
function Script:Get-CurrentBranchNameInternal {
    [CmdletBinding()]
    [OutputType([string])]
    param (
        [Parameter(Mandatory)]
        [System.Management.Automation.CommandInfo] $GitCommand,

        [Parameter(Mandatory)]
        [ValidateNotNullOrEmpty()]
        [string] $RepositoryPath,

        [ValidateSet('Error', 'Name', 'Sha', 'Null')]
        [string]
        $DetachedBehavior = 'Error'
    )

    # Attempt to resolve the current branch via symbolic ref.
    # --short trims refs/heads/, -q suppresses errors for non-symbolic HEAD.
    Write-Verbose "🔎 Reading branch (symbolic-ref) in '$RepositoryPath'..."

    # Attempt to get the symbolic branch name
    $branch = & $GitCommand -C $RepositoryPath symbolic-ref --short -q HEAD 2>$null

    # Success path: exit code 0 and a non-empty branch name → return trimmed value.
    if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($branch)) {
        return $branch.Trim()
    }

    # Detached HEAD or failure to read a symbolic ref — follow the caller's policy.
    switch ($DetachedBehavior) {
        'Error' {
            # Default: be strict so callers don't accidentally operate on detached HEAD.
            throw '❌ Detached HEAD: no branch is currently checked out.'
        }
        'Name' {
            # Return a stable sentinel name for logging/prompt purposes.
            return 'HEAD'
        }
        'Sha' {
            # Resolve the short commit SHA of the current HEAD for precise identification.
            $sha = & $GitCommand -C $RepositoryPath rev-parse --short HEAD 2>$null
            if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($sha)) {
                return $sha.Trim()
            }
            # If even the SHA cannot be resolved, fail loudly with context.
            throw '❌ Detached HEAD and failed to resolve current commit SHA.'
        }
        'Null' {
            # Return $null so the caller can handle it explicitly (e.g., prompt or skip).
            return $null
        }
    }
}
