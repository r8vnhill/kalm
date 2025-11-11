#Requires -Version 7.4
using module ./lib/ScriptLogging.psm1

<#
.SYNOPSIS
Synchronizes the local repository with GitLab (primary) and GitHub (mirror).

.DESCRIPTION
This script pulls the latest changes from both GitLab and GitHub remotes, merges
them if necessary, and pushes the synchronized state back to both remotes. It is
designed to keep GitLab (the primary repository) and GitHub (the mirror) in sync.

The script:
1. Fetches from all configured remotes
2. Pulls changes from the tracking branch (fast-forward if possible)
3. Pushes the current branch to all configured push URLs

.PARAMETER Remote
The name of the remote to synchronize. Defaults to 'origin'.

.PARAMETER Branch
The branch to synchronize. If not specified, uses the current branch.

.PARAMETER WhatIf
Shows what would happen if the cmdlet runs. Because the script supports
ShouldProcess, the built-in `-WhatIf` and `-Confirm` switches are available.

.EXAMPLE
PS> .\scripts\Sync-Remotes.ps1

Synchronizes the current branch with all remotes configured for 'origin'.

.EXAMPLE
PS> .\scripts\Sync-Remotes.ps1 -Branch main -WhatIf

Shows what would happen when synchronizing the 'main' branch without making changes.

.EXAMPLE
PS> .\scripts\Sync-Remotes.ps1 -Remote origin -Branch rename/kalm

Synchronizes the 'rename/kalm' branch with the 'origin' remote.
#>

[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'Medium')]
param(
    [ValidateNotNullOrWhiteSpace()]
    [string]    $Remote = 'origin',

    [ValidateNotNullOrWhiteSpace()]
    [string]    $Branch
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'
$InformationPreference = 'Continue'
$script:SyncRemotesPSCmdlet = $PSCmdlet

$logger = [KalmScriptLogger]::Start('Sync-Remotes', $null)
$initialBranchLabel = if ($Branch) { $Branch } else { '<auto>' }
$logger.LogInfo(("Starting sync for remote '{0}' (branch='{1}', WhatIf={2})" -f $Remote, $initialBranchLabel, $PSBoundParameters.ContainsKey('WhatIf')),'Startup')

# Import the shared Git helpers which include the DryRun singleton API and helpers
Import-Module -Force (Join-Path $PSScriptRoot 'GitSync.psm1')

# If invoked with -WhatIf set the shared dry-run singleton so helper functions can avoid git execution
if ($PSBoundParameters.ContainsKey('WhatIf')) { Set-KalmDryRun $true }

function Get-CurrentBranch {
    $out = Invoke-GitCommand -Arguments @('rev-parse','--abbrev-ref','HEAD') -Description 'Determining current branch' -CaptureOutput
    if (-not $out) { throw 'Failed to determine current branch. Are you in a git repository?' }
    $line = if ($out -is [array]) { $out[0] } else { $out }
    return ([string]$line).Trim()
}

function Test-WorkingTreeClean {
    $out = Invoke-GitCommand -Arguments @('status','--porcelain') -Description 'Checking working tree status' -CaptureOutput -NoThrow
    if ($null -eq $out) { throw 'Failed to check working tree status.' }
    return -not ($out -and ($out -join '').Trim())
}

function Invoke-GitCommand {
    [CmdletBinding(SupportsShouldProcess = $true)]
    param(
        [Parameter(Mandatory)]
        [string[]]
        $Arguments,

        [Parameter()]
        [string]
        $Description,

        [Parameter()]
        [string]
        $Target = 'repository',

        [Parameter()]
        [string]
        $Action
        ,
        [Parameter()]
        [switch]
        $CaptureOutput,
        [Parameter()]
        [switch]
        $NoThrow
    )

    if ($Description) {
        Write-Information $Description
    }

    # Respect global -WhatIf even when ShouldProcess propagation is unreliable
    if ($WhatIfPreference -eq 'Continue') {
        return $null
    }

    $commandLine = "git $($Arguments -join ' ')"
    Write-Verbose "Executing: $commandLine"

    $actionLabel = if ($Action) { $Action } else { $commandLine }
    $targetLabel = if ($Target) { $Target } else { 'repository' }

    if (-not $script:SyncRemotesPSCmdlet.ShouldProcess($targetLabel, $actionLabel)) {
        return $null
    }

    if ($CaptureOutput) {
        $output = & git @Arguments 2>&1
        $exitCode = $LASTEXITCODE
        if ($exitCode -ne 0 -and -not $NoThrow) {
            throw "Command failed with exit code ${exitCode}: $commandLine`nOutput: $($output -join "`n")"
        }
        if ($exitCode -ne 0 -and $NoThrow) { return $null }
        return ,$output
    }
    else {
        & git @Arguments
        $exitCode = $LASTEXITCODE
        if ($exitCode -ne 0) {
            throw "Command failed with exit code ${exitCode}: $commandLine"
        }
        return $null
    }
}

# --- Main script ---

try {
    Write-Information '==> Git Remote Sync Script'
    Write-Information ''

    # Determine branch
    if (-not $Branch) {
        $Branch = Get-CurrentBranch
        Write-Information "Using current branch: $Branch"
        $logger.LogInfo(("Resolved current branch to '{0}'." -f $Branch),'Branch')
    }
    else {
        Write-Information "Using specified branch: $Branch"
        $logger.LogInfo(("Using provided branch '{0}'." -f $Branch),'Branch')
    }

    # Check working tree
    if (-not (Test-WorkingTreeClean)) {
        throw 'Working tree has uncommitted changes. Please commit or stash them first.'
    }

    # Step 1: Fetch from all remotes
    Write-Information ''
    Write-Information 'Step 1: Fetching from all remotes...'
    $logger.LogInfo('Fetching updates from all remotes.','Fetch')
    Invoke-GitCommand -Arguments @('fetch', '--all', '--prune') -Description 'Fetching updates from all remotes...' -Target 'all remotes' -Action 'Fetch updates'

    # Step 2: Check if remote branch exists
    Write-Information ''
    Write-Information 'Step 2: Checking remote tracking branch...'
    $logger.LogDebug(("Validating remote branch '{0}'." -f $remoteBranch),'Fetch')
    $remoteBranch = "$Remote/$Branch"
    git rev-parse --verify "refs/remotes/$remoteBranch" 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Information "Remote branch $remoteBranch exists."

        # Check if we can fast-forward
        $mergeBase = git merge-base HEAD "$remoteBranch" 2>$null
        $headCommit = git rev-parse HEAD 2>$null
        $remoteCommit = git rev-parse "$remoteBranch" 2>$null

        if ($mergeBase -eq $headCommit) {
            Write-Information 'Local branch is behind remote. Fast-forwarding...'
            $logger.LogInfo(("Fast-forwarding local branch from {0}." -f $remoteBranch),'Merge')
            Invoke-GitCommand -Arguments @('merge', '--ff-only', $remoteBranch) -Description "Fast-forwarding to $remoteBranch..." -Target $remoteBranch -Action 'Fast-forward merge'
        }
        elseif ($mergeBase -eq $remoteCommit) {
            Write-Information 'Local branch is ahead of remote. No pull needed.'
            $logger.LogInfo('Local branch ahead of remote; skipping pull.','Merge')
        }
        else {
            Write-Warning 'Local and remote branches have diverged.'
            Write-Information "Attempting to merge $remoteBranch into local branch..."
            $logger.LogWarning(("Detected divergence with remote branch '{0}'. Attempting merge." -f $remoteBranch),'Merge')
            Invoke-GitCommand -Arguments @('merge', $remoteBranch, '--no-edit') -Description "Merging $remoteBranch..." -Target $remoteBranch -Action 'Merge remote changes'
        }
    }
    else {
        Write-Information "Remote branch $remoteBranch does not exist yet. Will create on push."
        $logger.LogWarning(("Remote branch '{0}' missing; will create on push." -f $remoteBranch),'Validation')
    }

    # Step 3: Push to all configured push URLs
    Write-Information ''
    Write-Information 'Step 3: Pushing to all remotes...'
    $logger.LogInfo(("Pushing branch '{0}' to remote '{1}'." -f $Branch, $Remote),'Push')

    $pushUrls = Invoke-GitCommand -Arguments @('remote','get-url','--push','--all',$Remote) -Description 'Listing push URLs' -CaptureOutput -NoThrow
    if (-not $pushUrls -or $pushUrls.Count -eq 0) {
        throw "No push URLs configured for remote '$Remote'."
    }

    Write-Information "Configured push URLs for '$Remote':"
    $pushUrls | ForEach-Object { Write-Information "  - $_" }

    Invoke-GitCommand -Arguments @('push', $Remote, $Branch) -Description "Pushing $Branch to $Remote..." -Target $Remote -Action "Push branch '$Branch'"

    # Summary
    Write-Information ''
    Write-Information '==> Sync completed successfully!'
    Write-Information "Branch '$Branch' is now synchronized across all remotes."
    $logger.LogInfo(("Sync finished for branch '{0}'." -f $Branch),'Summary')
}
catch {
    Write-Error $_
    $logger.LogError(("Sync-Remotes failed: {0}" -f $_.Exception.Message),'Failure')
    exit 1
}
