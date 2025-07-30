<#
.SYNOPSIS
    Resolves a repository path to a full filesystem path and (optionally) validates it.

.DESCRIPTION
    Converts an input path (or the current location by default) into a fully-resolved, literal filesystem path.
    Optionally ensures:
    - the path points to an existing directory (-EnsureDirectory), and/or
    - the path is a Git repository (-EnsureGitRepository), verified via `git rev-parse --git-dir`.

    If -EnsureGitRepository is specified, the function will use the provided -GitCommand (or attempt to locate `git` automatically) to validate the repository.

.PARAMETER GitCommand
    An explicit CommandInfo for `git` (e.g., (Get-Command git)).
    Only needed when -EnsureGitRepository is used.
    If omitted, the function will attempt to find `git`.

.PARAMETER RepositoryPath
    The candidate path to resolve.
    Accepts pipeline input (by value and by property name), and supports aliases 'Path' and 'RepoPath'.
    Defaults to the current location.

.PARAMETER EnsureDirectory
    When set, throws if the resolved path is not an existing directory.

.PARAMETER EnsureGitRepository
    When set, throws if the resolved path is not within a Git repository (no `.git` found).

.OUTPUTS
    [string] – The resolved literal filesystem path.

.EXAMPLE
    # Resolve the current directory (default) to a full path
    Convert-RepositoryPath

.EXAMPLE
    # Resolve a path and ensure it is an existing directory
    Convert-RepositoryPath -RepositoryPath './src' -EnsureDirectory

.EXAMPLE
    # Validate that the given path is a Git repository, using an explicit git
    $git = Get-Command git
    Convert-RepositoryPath -RepositoryPath 'C:\repos\app' -EnsureGitRepository -GitCommand $git

.EXAMPLE
    # Pipeline usage with property name binding
    [PSCustomObject]@{ RepoPath = '/home/user/project' } | Convert-RepositoryPath -EnsureDirectory
#>
function Convert-RepositoryPath {
    [CmdletBinding()]
    [OutputType([string])]
    param (
        [System.Management.Automation.CommandInfo] $GitCommand,

        [Parameter(ValueFromPipeline, ValueFromPipelineByPropertyName)]
        [Alias('Path', 'RepoPath')]
        [ValidateNotNullOrWhiteSpace()]
        [string] $RepositoryPath = (Get-Location).Path,

        [switch] $EnsureDirectory,

        [switch] $EnsureGitRepository
    )

    process {
        try {
            # Resolve to a literal, fully-qualified path (no wildcard expansion).
            $resolved = (Resolve-Path -LiteralPath $RepositoryPath -ErrorAction Stop).Path
        } catch {
            throw "❌ Repository path not found or inaccessible: $RepositoryPath"
        }

        if ($EnsureDirectory -and `
                -not (Test-Path -LiteralPath $resolved -PathType Container)) {
            throw "❌ Path is not a directory: $resolved"
        }

        # Optional Git repository check: uses `git rev-parse --git-dir`.
        if ($EnsureGitRepository) {
            # Prefer the provided Git command; otherwise attempt to locate git.
            if (-not $GitCommand) {
                $GitCommand = Get-Command git -ErrorAction SilentlyContinue
                if (-not $GitCommand) {
                    throw '❌ Git is required to validate the repository path.'
                }
            }

            # Run 'git -C <path> rev-parse --git-dir' to verify a repo context exists.
            $null = & $GitCommand -C $resolved rev-parse --git-dir 2>$null

            if ($LASTEXITCODE -ne 0) {
                throw "❌ '$resolved' is not a Git repository (no .git found)."
            }
        }

        # Emit the resolved path as the function result.
        $resolved
    }
}
