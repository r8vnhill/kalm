function Get-CurrentBranchName {
    [CmdletBinding()]
    [OutputType([string])]
    param (
        [System.Management.Automation.CommandInfo] $GitCommand,

        [ValidateNotNullOrEmpty()]
        [string] $RepositoryPath = (Get-Location).Path,

        [ValidateSet('Error', 'Name', 'Sha', 'Null')]
        [string] $DetachedBehavior = 'Error',

        [switch] $EnsureGitRepository
    )

    . (Join-Path $PSScriptRoot 'Convert-RepositoryPath.ps1')

    if (-not $GitCommand) {
        $GitCommand = Get-Command git -ErrorAction SilentlyContinue
        if (-not $GitCommand) { throw '❌ Git is required to determine the current branch.' }
    }

    $RepositoryPath = Convert-RepositoryPath `
        -GitCommand $GitCommand `
        -RepositoryPath $RepositoryPath `
        -EnsureDirectory `
        -EnsureGitRepository:$EnsureGitRepository

    Write-Verbose "🔎 Determining current branch in '$RepositoryPath'..."

    Script:Get-CurrentBranchNameInternal `
        -GitCommand $GitCommand `
        -RepositoryPath $RepositoryPath `
        -DetachedBehavior $DetachedBehavior
}

function Script:Get-CurrentBranchNameInternal {
    [CmdletBinding()]
    [OutputType([string])]
    param (
        [Parameter(Mandatory)]
        [System.Management.Automation.CommandInfo] $GitCommand,
        [Parameter(Mandatory)]
        [ValidateNotNullOrEmpty()]
        [string] $RepositoryPath,
        [ValidateSet('Error', 'Name', 'Sha', 'Null')]
        [string] $DetachedBehavior = 'Error'
    )

    Write-Verbose "🔎 Reading branch (symbolic-ref) in '$RepositoryPath'..."
    $branch = & $GitCommand -C $RepositoryPath symbolic-ref --short -q HEAD 2>$null
    if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($branch)) {
        return $branch.Trim()
    }

    switch ($DetachedBehavior) {
        'Error' {
            throw '❌ Detached HEAD: no branch is currently checked out.'
        }
        'Name' {
            return 'HEAD'
        }
        'Sha' {
            $sha = & $GitCommand -C $RepositoryPath rev-parse --short HEAD 2>$null
            if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($sha)) {
                return $sha.Trim()
            }
            throw '❌ Detached HEAD and failed to resolve current commit SHA.'
        }
        'Null' {
            return $null
        }
    }
}
