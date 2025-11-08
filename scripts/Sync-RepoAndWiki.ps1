#Requires -Version 7.4
<#!
.SYNOPSIS
Synchronize main repository and all submodules (pull/commit/push) in a single workflow.

.DESCRIPTION
Uses shared functions from GitSync.psm1 to minimize duplication. Pulls latest changes
for root and submodules, commits submodule changes if any, updates pointers, and pushes.

.PARAMETER Remote
Git remote name (default: origin)

.PARAMETER SkipPush
If set, performs pull & commit but skips push.

.PARAMETER SkipPull
If set, uses current working tree state without pulling.

.PARAMETER SubmoduleOnly
Operate only on submodules (skip root repo commit/push).

.EXAMPLE
./scripts/Sync-RepoAndWiki.ps1

.EXAMPLE
./scripts/Sync-RepoAndWiki.ps1 -SkipPull -SkipPush
#>
[CmdletBinding(SupportsShouldProcess=$true)]
param(
    [string] $Remote = 'origin',
    [switch] $SkipPush,
    [switch] $SkipPull,
    [switch] $SubmoduleOnly,
    [switch] $IncludeRootChanges,
    [string] $RootCommitMessage = 'chore(repo): sync submodule pointers'
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'
$InformationPreference = 'Continue'

# Import Git helpers first so the dry-run singleton API is available, then set the top-level WhatIf flag
Import-Module -Force (Join-Path $PSScriptRoot 'GitSync.psm1')
if ($PSBoundParameters.ContainsKey('WhatIf')) { Set-KalmDryRun $true }

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$branch = Get-GitCurrentBranch -Path $repoRoot
Write-Information "Main repo branch: $branch"

# Pull main repo if applicable
if (-not $SkipPull -and -not $SubmoduleOnly) {
    if (-not (Test-GitClean -Path $repoRoot)) { throw 'Root working tree dirty. Commit or stash before sync.' }
    Invoke-Git -GitArgs @('fetch','--all','--prune') -WorkingDirectory $repoRoot -Description 'Fetching root remotes...'
    Invoke-Git -GitArgs @('pull','--ff-only',$Remote,$branch) -WorkingDirectory $repoRoot -Description "Fast-forward root repo ($branch)..." -ErrorAction Continue
}

# Enumerate submodules
$subs = Get-GitSubmodules -RepoRoot $repoRoot -DefaultBranch 'main'
if ($subs.Count -eq 0) { Write-Information 'No submodules detected.'; return }

Write-Information "Detected submodules:"; $subs | ForEach-Object { Write-Information " - $($_.Name) [$($_.Branch)]" }

foreach ($s in $subs) {
    if ($PSCmdlet.ShouldProcess($s.Path, "Sync submodule '$($s.Name)'")) {
        Sync-GitSubmodule -Submodule $s -Remote $Remote -Pull:(-not $SkipPull) -Push:(-not $SkipPush) -CommitMessage "chore($($s.Name)): update content"
    }
}

# Update pointers in root repo
if (-not $SubmoduleOnly) {
    # Update tracked submodule pointers in the main repo
    $pointerParams = @{
        RepoRoot = $repoRoot
        SubmodulePaths = $subs.Path
        CommitMessage = $RootCommitMessage
    }
    Update-GitSubmodulePointers @pointerParams

    # Optionally stage & commit any remaining root changes (docs, scripts, config)
    if ($IncludeRootChanges) {
        if ($PSCmdlet.ShouldProcess($repoRoot, 'Stage and commit all root changes')) {
            Invoke-Git -GitArgs @('add','-A') -WorkingDirectory $repoRoot -Description 'Staging all changes in root repository...'

            # If for any reason the generic wrapper didn't stage files (rare), fall back to an explicit add
            if (-not (Test-GitClean -Path $repoRoot)) {
                # Second-chance: run git add using the guarded Invoke-Git to ensure WhatIf is respected
                Write-Verbose 'Fallback: running explicit git add -A to ensure files are staged.'
                Invoke-Git -GitArgs @('add','-A') -WorkingDirectory $repoRoot -Description 'Fallback explicit git add -A' -Quiet
            }

            if (-not (Test-GitClean -Path $repoRoot)) {
                Invoke-Git -GitArgs @('commit','-m', $RootCommitMessage) -WorkingDirectory $repoRoot -Description 'Committing root repository changes...'
            } else {
                Write-Information 'No root changes to commit.'
            }
        }
    }

    if (-not $SkipPush) {
        Invoke-Git -GitArgs @('push',$Remote,$branch) -WorkingDirectory $repoRoot -Description 'Pushing root repo with updated submodule pointers...'
    }
}

Write-Information 'Sync complete.'
