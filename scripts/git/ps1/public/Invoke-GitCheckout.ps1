<#
.SYNOPSIS
    Checks out a specified Git branch, creating a tracking branch if needed.

.DESCRIPTION
    Uses Get-GitCheckoutArgs to determine the correct git arguments based on whether the
    branch exists locally or remotely.

.EXAMPLE
    Invoke-GitCheckout -BranchName "feature/login"

.EXAMPLE
    Git-Checkout "hotfix/urgent" -Verbose
#>
function Invoke-GitCheckout {
    [Alias('Git-Checkout')]
    [CmdletBinding()]
    param (
        # The name of the branch to check out.
        [Parameter(Mandatory, Position = 0)]
        [string] $BranchName,

        # The remote name to track if the branch is not found locally.
        [string] $Remote = 'origin',

        # If specified, will pass the output of the git command to the pipeline.
        [Parameter()]
        [switch]$PassThru,

        # Additional arguments to pass to the git checkout command.
        [Parameter(ValueFromRemainingArguments = $true)]
        [string[]] $ExtraArgs = @()
    )
    
    . $PSScriptRoot/../internal/Get-CommandOrElse.ps1

    $git = Get-CommandOrElse -Command git -Else {
        throw '❌ Git is required to perform checkout operations.'
    }

    $gitArgs = Get-GitCheckoutArgs -BranchName $BranchName -Remote $Remote

    $gitArgs += $ExtraArgs

    Write-Verbose "Running: git $($gitArgs -join ' ')"

    $output = & $git @gitArgs 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "❌ Git checkout failed: $output"
    }

    Write-Verbose ($output -join "`n")
    Write-Host "✅ Successfully checked out to branch '$BranchName'."

    if ($PassThru) {
        return $output
    }
}

<#
.SYNOPSIS
    Returns the appropriate git checkout arguments for a given branch.

.DESCRIPTION
    If the branch exists locally, prepares arguments to check it out.
    Otherwise, fetches all remotes and prepares tracking branch checkout if the branch
    exists remotely.

.EXAMPLE
    Get-GitCheckoutArgs -BranchName "feature/login" -Remote "upstream"
#>
function Get-GitCheckoutArgs {
    param (
        # The name of the branch to check out.
        [Parameter(Mandatory)]
        [ValidateNotNullOrEmpty()]
        [string] $BranchName,

        # The remote name to track if the branch is not found locally.
        [ValidateNotNullOrEmpty()]
        [string] $Remote = 'origin'
    )

    . $PSScriptRoot/../internal/Test-GitBranchExists.ps1

    if (Test-GitBranchExists -BranchName $BranchName) {
        Write-Verbose "Branch '$BranchName' exists locally."
        return @('checkout', $BranchName)
    }

    Write-Verbose "Branch '$BranchName' not found locally. Fetching remotes..."
    git fetch --all | Out-Null

    if (-not (Test-GitRemoteBranchExists -BranchName $BranchName -Remote $Remote)) {
        throw "❌ Branch '$BranchName' not found locally or remotely."
    }

    return @('checkout', '--track', "$Remote/$BranchName")
}
