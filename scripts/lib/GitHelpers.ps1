function Get-GitCurrentBranch {
    param([string] $Path = (Get-Location).Path)
    $out = Invoke-Git -GitArgs @('rev-parse','--abbrev-ref','HEAD') -WorkingDirectory $Path -Quiet -CaptureOutput
    if ($null -eq $out) {
        # In dry-run mode avoid calling git; try to read .git/HEAD directly as a non-git fallback
        if ((Get-Command -Name Get-KalmDryRun -ErrorAction SilentlyContinue) -and (Get-KalmDryRun)) {
            try {
                $gitHeadFile = Join-Path -Path $Path -ChildPath '.git'
                if (Test-Path $gitHeadFile -PathType Leaf) {
                    # .git is a file that points to the real gitdir: 'gitdir: <path>'
                    $ref = (Get-Content -Raw -LiteralPath $gitHeadFile).Trim()
                    if ($ref -match 'gitdir:\s*(?<p>.+)') { $gitDir = $matches['p'] } else { $gitDir = $ref }
                    $headFile = Join-Path -Path (Resolve-Path -LiteralPath (Join-Path $Path $gitDir) -ErrorAction SilentlyContinue) -ChildPath 'HEAD'
                }
                else {
                    $headFile = Join-Path -Path $Path -ChildPath '.git/HEAD'
                }
                if (Test-Path $headFile) {
                    $h = (Get-Content -Raw -LiteralPath $headFile).Trim()
                    if ($h -match 'ref: refs/heads/(?<b>.+)') { return $matches['b'] }
                    return $h
                }
            }
            catch { }
        }
        throw "Failed to get current branch for $Path"
    }
    $line = if ($out -is [array]) { $out[0] } else { $out }
    return ([string]$line).Trim()
}

function Test-GitClean {
    param([string] $Path = (Get-Location).Path)
    $out = Invoke-Git -GitArgs @('status','--porcelain') -WorkingDirectory $Path -Quiet -CaptureOutput
    if ($null -eq $out) {
        # No output means git reported a clean working tree; treat as clean so callers proceed safely
        return $true
    }
    # If output is empty or whitespace, it's clean
    return -not ($out -and ($out -join '').Trim())
}

function Show-GitChangesToStage {
    <#
    .SYNOPSIS
    Preview what files would be staged by 'git add -A' without actually staging them.
    
    .DESCRIPTION
    Runs 'git add -A --dry-run' to show which files would be added/updated/removed.
    Useful for verifying changes before committing, especially in -WhatIf mode.
    
    .PARAMETER Path
    Repository working directory path (default: current location).
    
    .EXAMPLE
    Show-GitChangesToStage -Path $repoRoot
    #>
    [CmdletBinding()]
    param([string] $Path = (Get-Location).Path)
    
    $out = Invoke-Git -GitArgs @('add','-A','--dry-run') -WorkingDirectory $Path -CaptureOutput -NoThrow
    if ($null -eq $out) {
        Write-Information "No changes to stage in $Path"
        return
    }
    
    # Ensure $out is always treated as an array
    $outArray = @($out)
    if ($outArray.Count -eq 0 -or -not ($outArray -join '').Trim()) {
        Write-Information "No changes to stage in $Path"
        return
    }
    
    Write-Information "Files that would be staged in ${Path}:"
    $outArray | ForEach-Object { Write-Information "  $_" }
}
