function Get-GitSubmodules {
    [CmdletBinding()] param(
        [Parameter()] [string] $RepoRoot = (Get-Location).Path,
        [Parameter()] [string] $DefaultBranch = 'main'
    )
    $gitmodules = Join-Path $RepoRoot '.gitmodules'
    if (-not (Test-Path $gitmodules)) { return @() }

    $content = Get-Content -Raw -LiteralPath $gitmodules
    $blocks = [regex]::Split($content, '\r?\n\s*\[submodule ') | Where-Object { $_ -match '"' }
    $result = @()
    foreach ($b in $blocks) {
        $name = [regex]::Match($b, '"(?<n>[^\"]+)"').Groups['n'].Value
        $path = [regex]::Match($b, 'path\s*=\s*(?<p>.+)').Groups['p'].Value.Trim()
        $branch = ([regex]::Match($b, 'branch\s*=\s*(?<br>.+)').Groups['br'].Value.Trim())
        if ([string]::IsNullOrWhiteSpace($branch)) { $branch = $DefaultBranch }
        if ($path) {
            $result += [pscustomobject]@{
                Name   = $name
                Path   = (Join-Path $RepoRoot $path)
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
        [Parameter()] [string] $CommitMessage,
        [Parameter()] [ValidateSet('ff-only','merge','rebase')] [string] $PullStrategy = 'ff-only'
    )
    $path = $Submodule.Path
    $branch = $Submodule.Branch

    if ($Pull) {
        Invoke-Git -GitArgs @('fetch', '--all', '--prune') -WorkingDirectory $path -Description "Fetching submodule '$($Submodule.Name)'..."
        # Ensure branch exists locally
        try { Invoke-Git -GitArgs @('rev-parse', '--verify', "$branch") -WorkingDirectory $path -Quiet } catch { }
        Invoke-Git -GitArgs @('checkout', $branch) -WorkingDirectory $path -Description "Checking out $branch in '$($Submodule.Name)'..."

        # First try a fast-forward pull; if it fails and a fallback strategy is provided, apply it
        $ffCode = Invoke-Git -GitArgs @('pull', '--ff-only', $Remote, $branch) -WorkingDirectory $path -Description "Pulling $branch in '$($Submodule.Name)' (ff-only)..." -NoThrow -ReturnExitCode
        if ($ffCode -ne 0) {
            switch ($PullStrategy) {
                'merge' {
                    Invoke-Git -GitArgs @('pull', '--no-rebase', '--no-edit', $Remote, $branch) -WorkingDirectory $path -Description "Fast-forward failed; merging remote $branch into local (auto-resolve divergence)..."
                }
                'rebase' {
                    Invoke-Git -GitArgs @('pull', '--rebase', $Remote, $branch) -WorkingDirectory $path -Description "Fast-forward failed; rebasing local $branch onto remote..."
                }
                default {
                    throw "Fast-forward pull failed and PullStrategy is 'ff-only'. Re-run with -PullStrategy merge or rebase to resolve divergence."
                }
            }
        }
    }

    # Stage & commit if there are changes
    if (-not (Test-GitClean -Path $path)) {
        Show-GitChangesToStage -Path $path
        Invoke-Git -GitArgs @('add', '-A') -WorkingDirectory $path -Description "Staging changes in submodule '$($Submodule.Name)'..."
        if ([string]::IsNullOrWhiteSpace($CommitMessage)) {
            throw "Submodule '$($Submodule.Name)' has changes to commit. Please provide a meaningful commit message via -CommitMessage."
        }
        Invoke-Git -GitArgs @('commit', '-m', $CommitMessage) -WorkingDirectory $path -Description "Committing in submodule '$($Submodule.Name)'..."
    }

    if ($Push) {
        Invoke-Git -GitArgs @('push', $Remote, $branch) -WorkingDirectory $path -Description "Pushing submodule '$($Submodule.Name)'..."
    }
}

function Update-GitSubmodulePointers {
    [CmdletBinding(SupportsShouldProcess = $true)]
    param(
        [Parameter(Mandatory)]
        [string] $RepoRoot,

        [Parameter(Mandatory)]
        [string[]] $SubmodulePaths,

        [Parameter()]
        [string] $CommitMessage
    )

    if (-not $PSCmdlet.ShouldProcess($RepoRoot, 'Update submodule pointers')) {
        return
    }

    # Build args array explicitly to avoid parsing issues when the message or paths contain tokens
    $addArgs = @('add') + $SubmodulePaths
    
    # Preview what would be staged
    Write-Information "Submodule pointers to stage:"
    $SubmodulePaths | ForEach-Object { Write-Information "  $_" }
    
    Invoke-Git -GitArgs $addArgs -WorkingDirectory $RepoRoot `
        -Description 'Staging submodule pointer updates in main repo...'

    # Determine if any of the specified submodule paths have staged pointer changes
    $relPaths = @()
    foreach ($p in $SubmodulePaths) {
        try {
            $full = (Resolve-Path -LiteralPath $p).Path
        }
        catch { $full = $p }
        if ($full.StartsWith($RepoRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
            $rel = $full.Substring($RepoRoot.Length).TrimStart([char[]]('\', '/'))
        }
        else {
            $rel = $p
        }
        $relPaths += $rel
    }

    # Use Invoke-Git to respect -WhatIf and capture exit code without throwing
    $gitArgs = @('diff', '--cached', '--quiet', '--') + $relPaths
    $code = Invoke-Git -GitArgs $gitArgs -WorkingDirectory $RepoRoot -Quiet -NoThrow -ReturnExitCode
    $hasStagedPointerChanges = ($code -ne 0)

    if ($hasStagedPointerChanges) {
        if ([string]::IsNullOrWhiteSpace($CommitMessage)) {
            throw 'Submodule pointers changed. Please provide a meaningful commit message via -CommitMessage.'
        }
        Invoke-Git -GitArgs @('commit', '-m', $CommitMessage) -WorkingDirectory $RepoRoot `
            -Description 'Committing submodule pointer updates in main repo...'
    }
}
