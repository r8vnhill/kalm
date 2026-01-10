#Requires -Version 7.4
using module ..\lib\ScriptLogging.psm1
<#!
.SYNOPSIS
Synchronize main repository and all submodules (pull/commit/push) in a single workflow.

.DESCRIPTION
Uses shared functions from GitSync.psm1 to minimize duplication. Pulls latest changes
for root and submodules, commits submodule changes if any, updates pointers, and pushes.
#>
[CmdletBinding(SupportsShouldProcess=$true)]
param(
    [string] $Remote = 'origin',
    [switch] $SkipPush,
    [switch] $SkipPull,
    [switch] $SubmoduleOnly,
    [switch] $IncludeRootChanges,
    [string] $RootCommitMessage = 'chore(repo): sync submodule pointers',
    [ValidateSet('ff-only','merge','rebase')] [string] $PullStrategy = 'ff-only'
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'
$InformationPreference = 'Continue'

# Import Git helpers first so the dry-run singleton API is available, then set the top-level WhatIf flag
Import-Module -Force (Join-Path $PSScriptRoot 'GitSync.psm1')
if ($PSBoundParameters.ContainsKey('WhatIf')) { Set-KalmDryRun $true }

$logger = [KalmScriptLogger]::Start('Sync-RepoAndWiki', $null)
$logger.LogInfo(("Starting repo + submodule sync (Remote='{0}', SkipPull={1}, SkipPush={2}, SubmoduleOnly={3}, IncludeRootChanges={4})" -f $Remote, $SkipPull.IsPresent, $SkipPush.IsPresent, $SubmoduleOnly.IsPresent, $IncludeRootChanges.IsPresent),'Startup')

try {
    $repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
    $branch = Get-GitCurrentBranch -Path $repoRoot
    Write-Information "Main repo branch: $branch"
    $logger.LogInfo(("Working on repository '{0}' (branch '{1}')." -f $repoRoot, $branch),'Root')

    # Pull main repo if applicable
    if (-not $SkipPull -and -not $SubmoduleOnly) {
        if (-not (Test-GitClean -Path $repoRoot)) { throw 'Root working tree dirty. Commit or stash before sync.' }
        Invoke-Git -GitArgs @('fetch','--all','--prune') -WorkingDirectory $repoRoot -Description 'Fetching root remotes...'
        $logger.LogInfo('Fetched latest refs for root repository.','Root')

        # Try fast-forward first; fall back to configured strategy when ff fails
        $ffCode = Invoke-Git -GitArgs @('pull','--ff-only',$Remote,$branch) -WorkingDirectory $repoRoot -Description "Pulling root repo ($branch) (ff-only)..." -NoThrow -ReturnExitCode
        if ($ffCode -ne 0) {
            $logger.LogWarning(("Fast-forward failed for '{0}'. Applying strategy '{1}'." -f $branch, $PullStrategy),'Root')
            switch ($PullStrategy) {
                'merge' {
                    Invoke-Git -GitArgs @('pull','--no-rebase','--no-edit',$Remote,$branch) -WorkingDirectory $repoRoot -Description "Fast-forward failed; merging remote $branch into local (auto-resolve divergence)..."
                }
                'rebase' {
                    Invoke-Git -GitArgs @('pull','--rebase',$Remote,$branch) -WorkingDirectory $repoRoot -Description "Fast-forward failed; rebasing local $branch onto remote..."
                }
                default {
                    throw "Fast-forward pull for root repo failed and PullStrategy is 'ff-only'. Re-run with -PullStrategy merge or rebase to resolve divergence."
                }
            }
        }
    }
    elseif ($SkipPull) {
        $logger.LogInfo('SkipPull requested; using current working tree state.','Root')
    }

    # Enumerate submodules
    $subs = Get-GitSubmodules -RepoRoot $repoRoot -DefaultBranch 'main'
    if ($subs.Count -eq 0) {
        Write-Information 'No submodules detected.'
        $logger.LogWarning('No submodules detected; nothing to sync.','Submodules')
        return
    }

    Write-Information "Detected submodules:"; $subs | ForEach-Object { Write-Information " - $($_.Name) [$($_.Branch)]" }
    $logger.LogInfo(("Detected submodules: {0}" -f (($subs | ForEach-Object Name) -join ', ')),'Submodules')

    foreach ($s in $subs) {
        if ($PSCmdlet.ShouldProcess($s.Path, "Sync submodule '$($s.Name)'") ) {
            $logger.LogInfo(("Syncing submodule '{0}' (branch '{1}')." -f $s.Name, $s.Branch),'Submodules')
            Sync-GitSubmodule -Submodule $s -Remote $Remote -Pull:(-not $SkipPull) -Push:(-not $SkipPush) -CommitMessage "chore($($s.Name)): update content" -PullStrategy $PullStrategy
        }
    }

    # Update pointers in root repo
    if (-not $SubmoduleOnly) {
        $logger.LogInfo('Updating submodule pointers in root repository.','Root')
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
                # Preview what would be staged
                Show-GitChangesToStage -Path $repoRoot

                Invoke-Git -GitArgs @('add','-A') -WorkingDirectory $repoRoot -Description 'Staging all changes in root repository...'

                # If for any reason the generic wrapper didn't stage files (rare), fall back to an explicit add
                if (-not (Test-GitClean -Path $repoRoot)) {
                    # Second-chance: run git add using the guarded Invoke-Git to ensure WhatIf is respected
                    Write-Verbose 'Fallback: running explicit git add -A to ensure files are staged.'
                    Invoke-Git -GitArgs @('add','-A') -WorkingDirectory $repoRoot -Description 'Fallback explicit git add -A' -Quiet
                }

                if (-not (Test-GitClean -Path $repoRoot)) {
                    Invoke-Git -GitArgs @('commit','-m', $RootCommitMessage) -WorkingDirectory $repoRoot -Description 'Committing root repository changes...'
                    $logger.LogInfo('Committed additional root changes.','Root')
                } else {
                    Write-Information 'No root changes to commit.'
                    $logger.LogInfo('No additional root changes to commit.','Root')
                }
            }
        }

        if (-not $SkipPush) {
            Invoke-Git -GitArgs @('push',$Remote,$branch) -WorkingDirectory $repoRoot -Description 'Pushing root repo with updated submodule pointers...'
            $logger.LogInfo(("Pushed root repository to '{0}/{1}'." -f $Remote, $branch),'Root')
        }
        else {
            $logger.LogInfo('SkipPush requested; root push skipped.','Root')
        }
    }

    Write-Information 'Sync complete.'
    $logger.LogInfo('Sync complete.','Summary')
}
catch {
    $logger.LogError(("Sync-RepoAndWiki failed: {0}" -f $_.Exception.Message),'Failure')
    throw
}
