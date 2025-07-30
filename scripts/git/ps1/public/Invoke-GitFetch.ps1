<#
.SYNOPSIS
    Fetches from a Git remote (or all remotes) with optional pruning, tags, and shallow
    depth.

.DESCRIPTION
    Builds and executes a `git fetch` command in a predictable, testable way. Supports:
    - Selecting a single remote (default: 'origin') or all remotes via parameter sets.
    - Common flags like `--prune`, `--tags`, and `--depth <n>` for shallow fetches.
    - Extra arguments for advanced scenarios.
    - `-WhatIf` / `-Confirm` through SupportsShouldProcess.
    - Verbose tracing of the composed command and the target.

.PARAMETER GitCommand
    The `git` command to use. Defaults to the first `git` found in the PATH. If not found,
    the function will throw an error.

.PARAMETER RepositoryPath
    The path to the repository root. Passed to `git` via `-C <path>` so the caller does
    not need to change the current location. Defaults to the current directory.

.PARAMETER Remote
    The name of the remote to fetch from (default 'origin'). Used only in the 'Remote'
    parameter set.

.PARAMETER All
    Fetch from all remotes (equivalent to `git fetch --all`). Used only in the 'All'
    parameter set and is mutually exclusive with -Remote.

.PARAMETER Prune
    Adds `--prune` so references removed on the remote are pruned locally.

.PARAMETER Tags
    Adds `--tags` to fetch all tags.

.PARAMETER Depth
    Adds `--depth <n>` to perform a shallow fetch, limiting the history depth to <n>.

.PARAMETER ExtraArgs
    Additional arguments appended verbatim to the composed `git fetch` command.

.PARAMETER PassThru
    Return the textual output from `git` (stdout/stderr merged). Without -PassThru, the
    function is silent on success and throws on failure.

.OUTPUTS
    [string]
    Only when -PassThru is specified; otherwise no output on success.

.EXAMPLE
    # Fetch from the default remote ('origin') and prune deleted branches
    Invoke-GitFetch -Prune -Verbose

.EXAMPLE
    # Fetch from all remotes, include tags, and use a shallow depth of 50
    Invoke-GitFetch -RepositoryPath 'C:\src\app' -All -Tags -Depth 50

.EXAMPLE
    # Preview the command without executing it
    Invoke-GitFetch -RepositoryPath '/repos/app' -WhatIf
#>
function Invoke-GitFetch {
    [CmdletBinding(SupportsShouldProcess, DefaultParameterSetName = 'Remote')]
    [OutputType([string])]
    [Alias('Git-Fetch')]
    param(
        [System.Management.Automation.CommandInfo] $GitCommand = (Get-Command git),

        [ValidateNotNullOrEmpty()]
        [string] $RepositoryPath = (Get-Location).Path,

        [Parameter(ParameterSetName = 'Remote')]
        [ValidateNotNullOrEmpty()]
        [string] $Remote = 'origin',

        [Parameter(ParameterSetName = 'All', Mandatory)]
        [switch] $All,

        [switch] $Prune,

        [switch] $Tags,

        [ValidateRange(1, [int]::MaxValue)]
        [int] $Depth,

        [string[]] $ExtraArgs,

        [switch] $PassThru
    )

    $RepositoryPath = Convert-RepositoryPath `
        -GitCommand $GitCommand `
        -RepositoryPath $RepositoryPath `
        -EnsureDirectory `
        -EnsureGitRepository

    # Use 'git -C <path>' to operate relative to the repository root without Set-Location.
    $gitArgs = @('-C', $RepositoryPath, 'fetch')

    if ($PSCmdlet.ParameterSetName -eq 'All') {
        $gitArgs += '--all'
        $target = 'all remotes'
    } else {
        $gitArgs += $Remote
        $target = $Remote
    }

    if ($Prune.IsPresent) { $gitArgs += '--prune' }
    if ($Tags.IsPresent) { $gitArgs += '--tags' }

    # Add shallow depth only if explicitly bound (so callers can pass 0/empty safely).
    if ($PSBoundParameters.ContainsKey('Depth')) {
        $gitArgs += @('--depth', $Depth)
    }

    # Allow advanced flags last so callers can extend/override behavior where supported.
    if ($ExtraArgs) { $gitArgs += $ExtraArgs }

    $cmdPreview = "git $($gitArgs -join ' ')"

    if (-not $PSCmdlet.ShouldProcess($RepositoryPath, $cmdPreview)) {
        return
    }

    Write-Verbose "📥 Fetching from '$target' in '$RepositoryPath'..."
    Write-Verbose "▶️  $cmdPreview"

    $output = & $GitCommand @gitArgs 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "❌ git fetch failed (exit $LASTEXITCODE). Command: $cmdPreview`n$output"
    }

    # Silent success unless the caller requested the raw output.
    if ($PassThru) { $output }
}
