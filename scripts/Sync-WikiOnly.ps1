#Requires -Version 7.4
using module ./lib/ScriptLogging.psm1
<#!
.SYNOPSIS
Sync only the wiki submodule: pull, commit changes (if any), push, and update root pointer optionally.

.DESCRIPTION
Loads shared Git helpers from GitSync.psm1. Operates only on the wiki submodule
without touching other parts of the repository unless -UpdatePointer is supplied.

.PARAMETER Remote
Git remote name (default: origin)

.PARAMETER SkipPull
Use current state without pulling new changes.

.PARAMETER SkipPush
Do not push submodule commit or root pointer update.

.PARAMETER UpdatePointer
Stage and commit updated submodule pointer in root repository.

.PARAMETER RootCommitMessage
Custom commit message when updating pointer.

.PARAMETER WikiCommitMessage
Custom commit message to use when committing changes inside the wiki submodule.

.PARAMETER PullStrategy
How to resolve remote divergence when a fast-forward pull is not possible. One of: `ff-only` (default), `merge`, `rebase`.

.EXAMPLE
./scripts/Sync-WikiOnly.ps1 -UpdatePointer

.EXAMPLE
./scripts/Sync-WikiOnly.ps1 -SkipPull -SkipPush
#>
[CmdletBinding(SupportsShouldProcess=$true)]
param(
    [string] $Remote = 'origin',
    [switch] $SkipPull,
    [switch] $SkipPush,
    [switch] $UpdatePointer,
    [string] $RootCommitMessage,
    [string] $WikiCommitMessage,
    [ValidateSet('ff-only','merge','rebase')] [string] $PullStrategy = 'ff-only'
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'
$InformationPreference = 'Continue'

Import-Module -Force (Join-Path $PSScriptRoot 'GitSync.psm1')

# If this script was invoked with -WhatIf, set the shared dry-run singleton for nested helpers
if ($PSBoundParameters.ContainsKey('WhatIf')) { Set-KalmDryRun $true }
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

$logger = [KalmScriptLogger]::Start('Sync-WikiOnly', $null)
$logger.LogInfo(("Starting wiki sync (Remote='{0}', SkipPull={1}, SkipPush={2}, UpdatePointer={3})" -f $Remote, $SkipPull.IsPresent, $SkipPush.IsPresent, $UpdatePointer.IsPresent),'Startup')

# Ensure submodules are initialized before attempting wiki operations
Ensure-GitSubmodulesInitialized -RepoRoot $repoRoot

$subs = Get-GitSubmodules -RepoRoot $repoRoot -DefaultBranch 'main' | Where-Object { $_.Name -match 'wiki' -or $_.Path -match 'wiki' }
if ($subs.Count -eq 0) {
    Write-Warning 'Wiki submodule not found.'
    $logger.LogWarning('Wiki submodule not found; aborting.','Submodules')
    return
}
$wiki = $subs[0]
Write-Information "Wiki path: $($wiki.Path) (branch: $($wiki.Branch))"
$logger.LogInfo(("Operating on wiki path '{0}' (branch '{1}')." -f $wiki.Path, $wiki.Branch),'Submodules')

try {
    if ($PSCmdlet.ShouldProcess($wiki.Path, 'Sync wiki submodule')) {
        Sync-GitSubmodule -Submodule $wiki -Remote $Remote -Pull:(-not $SkipPull) -Push:(-not $SkipPush) -CommitMessage $WikiCommitMessage -PullStrategy $PullStrategy
        $logger.LogInfo('Wiki submodule sync completed.','Submodules')
    }

    if ($UpdatePointer) {
        Write-Information 'Updating wiki submodule pointer in root repo...'
        $logger.LogInfo('Updating wiki pointer in root repository.','Root')
        Update-GitSubmodulePointers -RepoRoot $repoRoot -SubmodulePaths @($wiki.Path) -CommitMessage $RootCommitMessage
        if (-not $SkipPush) {
            $branch = Get-GitCurrentBranch -Path $repoRoot
            Invoke-Git -GitArgs @('push',$Remote,$branch) -WorkingDirectory $repoRoot -Description 'Pushing root repo with updated wiki pointer...'
            $logger.LogInfo(("Pushed root repository with updated wiki pointer to '{0}/{1}'." -f $Remote, $branch),'Root')
        }
        else {
            $logger.LogInfo('SkipPush set; pointer update not pushed.','Root')
        }
    }

    Write-Information 'Wiki sync complete.'
    $logger.LogInfo('Wiki sync complete.','Summary')
}
catch {
    $logger.LogError(("Sync-WikiOnly failed: {0}" -f $_.Exception.Message),'Failure')
    throw
}
