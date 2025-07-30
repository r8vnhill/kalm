. $PSScriptRoot\Get-CommandOrElse.ps1

<#
.SYNOPSIS
    Checks whether a local Git branch exists.

.DESCRIPTION
    This function verifies the existence of a local Git branch using
    `git branch --list <branch>`.
    It delegates the actual logic to a shared internal helper and returns $true if the
    branch exists.

.OUTPUTS
    [bool] True if the branch exists locally; false otherwise.

.EXAMPLE
    Test-GitBranchExists -BranchName 'main'
#>
function Test-GitBranchExists {
    [CmdletBinding()]
    [OutputType([bool])]
    param (
        # The name of the local branch to check.
        [Parameter(Mandatory)]
        [string] $BranchName
    )

    # Delegate to the shared implementation with IsRemote=$false
    return Script:Test-GitBranchExistsImpl `
        -BranchName $BranchName `
        -Remote $null `
        -IsRemote:$false
}

<#
.SYNOPSIS
    Checks whether a remote Git branch exists.

.DESCRIPTION
    This function verifies the existence of a remote Git branch using
    `git branch -r --list <remote>/<branch>`.
    It delegates the actual logic to a shared internal helper and returns $true if the
    branch exists.

.OUTPUTS
    [bool] True if the branch exists on the specified remote; false otherwise.

.EXAMPLE
    Test-GitRemoteBranchExists -BranchName 'feature/login'

.EXAMPLE
    Test-GitRemoteBranchExists 'dev' -Remote 'upstream'
#>
function Test-GitRemoteBranchExists {
    [CmdletBinding()]
    [OutputType([bool])]
    param (
        # The name of the remote branch to check (without the remote prefix).
        [Parameter(Mandatory, Position = 0)]
        [ValidateNotNullOrWhiteSpace()]
        [string] $BranchName,

        # The name of the Git remote. Defaults to 'origin'.
        [ValidateNotNullOrWhiteSpace()]
        [string] $Remote = 'origin'
    )

    # Delegate to the shared implementation with IsRemote=$true
    return Script:Test-GitBranchExistsImpl `
        -BranchName $BranchName `
        -Remote $Remote `
        -IsRemote
}

<#
.SYNOPSIS
    Shared implementation for checking local or remote Git branch existence.

.DESCRIPTION
    Performs a `git branch [--list | -r --list]` command to determine if a given branch
    exists.
    Used internally by Test-GitBranchExists and Test-GitRemoteBranchExists.

.OUTPUTS
    [bool] True if the branch exists; false otherwise.
#>
function Script:Test-GitBranchExistsImpl {
    [CmdletBinding()]
    [OutputType([bool])]
    param (
        # The name of the branch to look for (local or remote).
        [Parameter(Mandatory)]
        [string] $BranchName,

        # If provided and IsRemote is $true, the name of the remote to prefix the branch
        # name with.
        [string] $Remote,

        # Boolean flag indicating whether to check remote branches (true) or local
        # branches (false).
        [switch] $IsRemote
    )

    try {
        # Ensure git is available in the environment
        $git = Get-CommandOrElse 'git' {
            throw '❌ Git is required to check for branches.'
        }

        # Construct the git command arguments
        if ($IsRemote) {
            $gitArgs = @('branch', '-r', '--list', "$Remote/$BranchName")
        } else {
            $gitArgs = @('branch', '--list', $BranchName)
        }

        Write-Verbose "Running: git $($gitArgs -join ' ')"

        # Execute the git command
        $output = & $git @gitArgs

        # Build strict match pattern to match branch name exactly (optional '*', whitespace ignored)
        $pattern = "^\s*(\*?\s*)?$([Regex]::Escape($BranchName))\s*$"

        # Return true if the output matches the pattern
        return $output -match $pattern
    } catch {
        Write-Verbose "Failed to check branch '$BranchName': $_"
        return $false
    }
}
