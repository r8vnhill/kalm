#Requires -Version 7.4
using module ..\lib\ScriptLogging.psm1

<#
.SYNOPSIS
Wiki-focused sync: operate only on the wiki submodule.
#>

param(
    [string] $Remote = 'origin',
    [switch] $UpdatePointer,
    [switch] $SkipPull,
    [switch] $SkipPush,
    [string] $RootCommitMessage,
    [string] $WikiCommitMessage,
    [ValidateSet('ff-only','merge','rebase')] [string] $PullStrategy = 'ff-only'
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'

$logger = [KalmScriptLogger]::Start('Sync-WikiOnly', $null)
$logger.LogInfo('Starting wiki-only synchronization.','Startup')

Import-Module -Force (Join-Path $PSScriptRoot 'GitSync.psm1')
if ($PSBoundParameters.ContainsKey('WhatIf')) { Set-KalmDryRun $true }

# Implementation note: the script locates the wiki submodule, performs a pull/commit/push
# inside that submodule, and optionally updates the pointer in the root repository.

try {
    # Detect wiki submodule path and operate inside it.
    $repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
    $subs = Get-GitSubmodules -RepoRoot $repoRoot -DefaultBranch 'main'
    $wiki = $subs | Where-Object { $_.Name -match 'wiki' }
    if (-not $wiki) { throw 'Wiki submodule not found.' }

    $logger.LogInfo(("Operating on wiki submodule at {0}" -f $wiki.Path),'Wiki')
    if (-not $SkipPull) { Sync-GitSubmodule -Submodule $wiki -Remote $Remote -Pull $true -Push $false -CommitMessage $WikiCommitMessage -PullStrategy $PullStrategy }
    if ($PSBoundParameters.ContainsKey('WikiCommitMessage') -and -not $SkipPush) { Sync-GitSubmodule -Submodule $wiki -Remote $Remote -Pull $false -Push $true -CommitMessage $WikiCommitMessage }

    if ($UpdatePointer) {
        Update-GitSubmodulePointers -RepoRoot $repoRoot -SubmodulePaths @($wiki.Path) -CommitMessage $RootCommitMessage
    }

    Write-Information 'Wiki sync complete.'
    $logger.LogInfo('Wiki sync complete.','Summary')
}
catch {
    $logger.LogError(("Sync-WikiOnly failed: {0}" -f $_.Exception.Message),'Failure')
    throw
}
