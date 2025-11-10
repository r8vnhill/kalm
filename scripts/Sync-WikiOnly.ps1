#Requires -Version 7.4
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

# Ensure submodules are initialized before attempting wiki operations
Ensure-GitSubmodulesInitialized -RepoRoot $repoRoot

$subs = Get-GitSubmodules -RepoRoot $repoRoot -DefaultBranch 'main' | Where-Object { $_.Name -match 'wiki' -or $_.Path -match 'wiki' }
if ($subs.Count -eq 0) { Write-Warning 'Wiki submodule not found.'; return }
$wiki = $subs[0]
Write-Information "Wiki path: $($wiki.Path) (branch: $($wiki.Branch))"

if ($PSCmdlet.ShouldProcess($wiki.Path, 'Sync wiki submodule')) {
    Sync-GitSubmodule -Submodule $wiki -Remote $Remote -Pull:(-not $SkipPull) -Push:(-not $SkipPush) -CommitMessage $WikiCommitMessage -PullStrategy $PullStrategy
}

if ($UpdatePointer) {
    Write-Information 'Updating wiki submodule pointer in root repo...'
    Update-GitSubmodulePointers -RepoRoot $repoRoot -SubmodulePaths @($wiki.Path) -CommitMessage $RootCommitMessage
    if (-not $SkipPush) {
        $branch = Get-GitCurrentBranch -Path $repoRoot
        Invoke-Git -GitArgs @('push',$Remote,$branch) -WorkingDirectory $repoRoot -Description 'Pushing root repo with updated wiki pointer...'
    }
}

Write-Information 'Wiki sync complete.'
