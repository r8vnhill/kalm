<#
.SYNOPSIS
    Gets the upstream tracking branch for the current Git repository.

.DESCRIPTION
    Uses `git rev-parse` to determine the symbolic full name of the upstream branch.
    Throws an error if detection fails.

.PARAMETER RepositoryPath
    The path to the Git repository.

.PARAMETER Git
    A CommandInfo reference to the git executable (defaults to the first result of
    Get-Command git).

.OUTPUTS
    [string] The name of the upstream ref (e.g., origin/main).

.EXAMPLE
    Get-UpstreamRef -RepositoryPath "C:\MyRepo"
#>
function Get-UpstreamRef {
    [CmdletBinding()]
    param (
        [Parameter(Mandatory)]
        [string]
        $RepositoryPath,
        [System.Management.Automation.CommandInfo]
        $Git = (Get-Command git -ErrorAction Stop)
    )

    $detected = & $Git `
        -C $RepositoryPath `
        rev-parse --abbrev-ref --symbolic-full-name '@{u}' 2>$null

    if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($detected)) {
        return $detected.Trim()
    }

    throw [System.InvalidOperationException] '❌ Failed to detect upstream reference.'
}
