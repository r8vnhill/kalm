#Requires -Version 7.0

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

.PARAMETER DryRun
When specified, shows what would be done without making any changes.

.EXAMPLE
PS> .\scripts\Sync-Remotes.ps1

Synchronizes the current branch with all remotes configured for 'origin'.

.EXAMPLE
PS> .\scripts\Sync-Remotes.ps1 -Branch main -DryRun

Shows what would happen when synchronizing the 'main' branch without making changes.

.EXAMPLE
PS> .\scripts\Sync-Remotes.ps1 -Remote origin -Branch rename/kalm

Synchronizes the 'rename/kalm' branch with the 'origin' remote.
#>

[CmdletBinding()]
param(
    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]
    $Remote = 'origin',

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]
    $Branch,

    [Parameter()]
    [switch]
    $DryRun
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'

function Get-CurrentBranch {
    $branch = git rev-parse --abbrev-ref HEAD 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to determine current branch. Are you in a git repository?"
    }
    return $branch.Trim()
}

function Test-WorkingTreeClean {
    $status = git status --porcelain 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to check working tree status."
    }
    return [string]::IsNullOrWhiteSpace($status)
}

function Invoke-GitCommand {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]
        $Command,

        [Parameter()]
        [string]
        $Description
    )

    if ($Description) {
        Write-Host $Description -ForegroundColor Cyan
    }

    Write-Verbose "Executing: $Command"

    if ($DryRun) {
        Write-Host "[DRY RUN] Would execute: $Command" -ForegroundColor Yellow
        return $true
    }

    Invoke-Expression $Command
    $exitCode = $LASTEXITCODE

    if ($exitCode -ne 0) {
        Write-Error "Command failed with exit code ${exitCode}: $Command"
        return $false
    }

    return $true
}

# --- Main script ---

Write-Host "==> Git Remote Sync Script" -ForegroundColor Green
Write-Host ""

# Determine branch
if (-not $Branch) {
    $Branch = Get-CurrentBranch
    Write-Host "Using current branch: $Branch" -ForegroundColor Cyan
}
else {
    Write-Host "Using specified branch: $Branch" -ForegroundColor Cyan
}

# Check working tree
if (-not (Test-WorkingTreeClean)) {
    Write-Warning "Working tree has uncommitted changes. Please commit or stash them first."
    exit 1
}

# Step 1: Fetch from all remotes
Write-Host ""
Write-Host "Step 1: Fetching from all remotes..." -ForegroundColor Green
if (-not (Invoke-GitCommand -Command "git fetch --all --prune" -Description "Fetching updates from all remotes...")) {
    exit 1
}

# Step 2: Check if remote branch exists
Write-Host ""
Write-Host "Step 2: Checking remote tracking branch..." -ForegroundColor Green
$remoteBranch = "$Remote/$Branch"
$remoteBranchExists = git rev-parse --verify "refs/remotes/$remoteBranch" 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Host "Remote branch $remoteBranch exists." -ForegroundColor Cyan

    # Check if we can fast-forward
    $mergeBase = git merge-base HEAD "$remoteBranch" 2>$null
    $headCommit = git rev-parse HEAD 2>$null
    $remoteCommit = git rev-parse "$remoteBranch" 2>$null

    if ($mergeBase -eq $headCommit) {
        Write-Host "Local branch is behind remote. Fast-forwarding..." -ForegroundColor Yellow
        if (-not (Invoke-GitCommand -Command "git merge --ff-only $remoteBranch" -Description "Fast-forwarding to $remoteBranch...")) {
            Write-Error "Fast-forward merge failed. Manual intervention required."
            exit 1
        }
    }
    elseif ($mergeBase -eq $remoteCommit) {
        Write-Host "Local branch is ahead of remote. No pull needed." -ForegroundColor Cyan
    }
    else {
        Write-Warning "Local and remote branches have diverged."
        Write-Host "Attempting to merge $remoteBranch into local branch..." -ForegroundColor Yellow
        if (-not (Invoke-GitCommand -Command "git merge $remoteBranch --no-edit" -Description "Merging $remoteBranch...")) {
            Write-Error "Merge failed. Please resolve conflicts manually."
            exit 1
        }
    }
}
else {
    Write-Host "Remote branch $remoteBranch does not exist yet. Will create on push." -ForegroundColor Yellow
}

# Step 3: Push to all configured push URLs
Write-Host ""
Write-Host "Step 3: Pushing to all remotes..." -ForegroundColor Green

$pushUrls = @(git remote get-url --push --all $Remote 2>$null)
if ($pushUrls.Count -eq 0) {
    Write-Warning "No push URLs configured for remote '$Remote'."
    exit 1
}

Write-Host "Configured push URLs for '$Remote':" -ForegroundColor Cyan
$pushUrls | ForEach-Object { Write-Host "  - $_" -ForegroundColor Gray }

if (-not (Invoke-GitCommand -Command "git push $Remote ${Branch}" -Description "Pushing $Branch to $Remote...")) {
    Write-Error "Push failed."
    exit 1
}

# Summary
Write-Host ""
Write-Host "==> Sync completed successfully!" -ForegroundColor Green
Write-Host "Branch '$Branch' is now synchronized across all remotes." -ForegroundColor Cyan

if ($DryRun) {
    Write-Host ""
    Write-Host "[DRY RUN] No changes were made. Remove -DryRun to execute." -ForegroundColor Yellow
}
