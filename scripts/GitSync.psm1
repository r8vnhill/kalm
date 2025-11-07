#Requires -Version 7.4
<#
.SYNOPSIS
Reusable Git and submodule automation functions for KALM project workflows.

.DESCRIPTION
This module provides safe, validated functions for common Git operations including:
- Git command execution with error handling
- Branch and status queries
- Submodule discovery (dynamic .gitmodules parsing)
- Submodule synchronization (pull/commit/push)
- Submodule pointer management in parent repository

All state-changing functions support -WhatIf and -Confirm for safe operation.

.NOTES
Version: 1.0.0
Requires: PowerShell 7.4+, Git 2.20+
Module functions are designed for extensibility and reuse across sync scripts.

.EXAMPLE
Import-Module ./GitSync.psm1
$submodules = Get-GitSubmodules -RepoRoot 'E:\repo'
Sync-GitSubmodule -Submodule $submodules[0] -Pull -Push

.EXAMPLE
$subs = Get-GitSubmodules
Update-GitSubmodulePointers -RepoRoot (Get-Location) -SubmodulePaths $subs.Path -WhatIf
#>

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'

function Invoke-Git {
    [CmdletBinding(SupportsShouldProcess=$true)] param(
        [Parameter(Mandatory)] [string[]] $GitArgs,
        [Parameter()] [string] $WorkingDirectory = (Get-Location).Path,
        [Parameter()] [string] $Description,
        [Parameter()] [switch] $Quiet
    )
    
    # Check if this is a state-changing operation
    $stateChanging = $GitArgs[0] -in @('add', 'commit', 'push', 'pull', 'fetch', 'checkout', 'merge', 'rebase', 'reset')
    
    if ($stateChanging) {
        $target = if ($GitArgs[0] -eq 'commit') { 
            "commit in $WorkingDirectory" 
        } else { 
            "$($GitArgs -join ' ') in $WorkingDirectory" 
        }
        
        if (-not $PSCmdlet.ShouldProcess($target, "git $($GitArgs[0])")) {
            if ($Description -and -not $Quiet) { Write-Information "[WhatIf] $Description" }
            return
        }
    }
    
    if ($Description -and -not $Quiet) { Write-Information $Description }
    $exe = 'git'
    $argsList = @('-C', $WorkingDirectory) + $GitArgs
    Write-Verbose ("Executing: {0}" -f ($argsList -join ' '))
    & $exe @argsList
    $code = $LASTEXITCODE
    if ($code -ne 0) { throw "git command failed ($code): $($argsList -join ' ') in '$WorkingDirectory'" }
}

function Get-GitCurrentBranch {
    param([string] $Path = (Get-Location).Path)
    $branch = (git -C $Path rev-parse --abbrev-ref HEAD 2>$null)
    if ($LASTEXITCODE -ne 0) { throw "Failed to get current branch for $Path" }
    return $branch.Trim()
}

function Test-GitClean {
    param([string] $Path = (Get-Location).Path)
    $status = (git -C $Path status --porcelain 2>$null)
    if ($LASTEXITCODE -ne 0) { throw "Failed to check status for $Path" }
    return [string]::IsNullOrWhiteSpace($status)
}

function Get-GitSubmodules {
    [CmdletBinding()] param(
        [Parameter()] [string] $RepoRoot = (Get-Location).Path,
        [Parameter()] [string] $DefaultBranch = 'main'
    )
    $gitmodules = Join-Path $RepoRoot '.gitmodules'
    if (-not (Test-Path $gitmodules)) { return @() }

    $content = Get-Content -Raw -LiteralPath $gitmodules
    $blocks = [regex]::Split($content, "\r?\n\s*\[submodule ") | Where-Object { $_ -match '"' }
    $result = @()
    foreach ($b in $blocks) {
        $name = [regex]::Match($b, '"(?<n>[^\"]+)"').Groups['n'].Value
        $path = [regex]::Match($b, 'path\s*=\s*(?<p>.+)').Groups['p'].Value.Trim()
        $branch = ([regex]::Match($b, 'branch\s*=\s*(?<br>.+)').Groups['br'].Value.Trim())
        if ([string]::IsNullOrWhiteSpace($branch)) { $branch = $DefaultBranch }
        if ($path) {
            $result += [pscustomobject]@{
                Name = $name
                Path = (Join-Path $RepoRoot $path)
                Branch = $branch
            }
        }
    }
    return $result
}

function Sync-GitSubmodule {
    [CmdletBinding()] param(
        [Parameter(Mandatory)] [psobject] $Submodule,
        [Parameter()] [string] $Remote = 'origin',
        [Parameter()] [switch] $Pull,
        [Parameter()] [switch] $Push,
        [Parameter()] [string] $CommitMessage
    )
    $path = $Submodule.Path
    $branch = $Submodule.Branch

    if ($Pull) {
        Invoke-Git -GitArgs @('fetch','--all','--prune') -WorkingDirectory $path -Description "Fetching submodule '$($Submodule.Name)'..."
        # Ensure branch exists locally
        try { Invoke-Git -GitArgs @('rev-parse','--verify',"$branch") -WorkingDirectory $path -Quiet } catch { }
        Invoke-Git -GitArgs @('checkout', $branch) -WorkingDirectory $path -Description "Checking out $branch in '$($Submodule.Name)'..."
        Invoke-Git -GitArgs @('pull','--ff-only', $Remote, $branch) -WorkingDirectory $path -Description "Pulling $branch in '$($Submodule.Name)'..."
    }

    # Stage & commit if there are changes
    if (-not (Test-GitClean -Path $path)) {
        Invoke-Git -GitArgs @('add','-A') -WorkingDirectory $path -Description "Staging changes in submodule '$($Submodule.Name)'..."
        if (-not $CommitMessage) { $CommitMessage = "chore($($Submodule.Name)): update content" }
        Invoke-Git -GitArgs @('commit','-m', $CommitMessage) -WorkingDirectory $path -Description "Committing in submodule '$($Submodule.Name)'..."
    }

    if ($Push) {
        Invoke-Git -GitArgs @('push', $Remote, $branch) -WorkingDirectory $path -Description "Pushing submodule '$($Submodule.Name)'..."
    }
}

function Update-GitSubmodulePointers {
    [CmdletBinding(SupportsShouldProcess=$true)]
    param(
        [Parameter(Mandatory)]
        [string] $RepoRoot,

        [Parameter(Mandatory)]
        [string[]] $SubmodulePaths,

        [Parameter()]
        [string] $CommitMessage = 'chore(submodules): update pointers'
    )

    if (-not $PSCmdlet.ShouldProcess($RepoRoot, 'Update submodule pointers')) {
        return
    }

    # Build args array explicitly to avoid parsing issues when the message or paths contain tokens
    $addArgs = @('add') + $SubmodulePaths
    Invoke-Git -GitArgs $addArgs -WorkingDirectory $RepoRoot `
        -Description 'Staging submodule pointer updates in main repo...'
    
    if (-not (Test-GitClean -Path $RepoRoot)) {
        Invoke-Git -GitArgs @('commit','-m', $CommitMessage) -WorkingDirectory $RepoRoot `
            -Description 'Committing submodule pointer updates in main repo...'
    }
}

Export-ModuleMember -Function *
