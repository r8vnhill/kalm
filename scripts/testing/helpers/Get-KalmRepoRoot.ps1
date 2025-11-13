#Requires -Version 7.4

<#
.SYNOPSIS
    Locate the repository root by walking parent directories.

.DESCRIPTION
    Traverses ancestor directories from a given start path until a marker (default: .git) is found.
    Returns either a string path or a [System.IO.DirectoryInfo] when -AsObject is used.

.PARAMETER StartPath
    Path to begin searching (default: current directory).

.PARAMETER Marker
    Directory or file name identifying the project root (default: .git).

.PARAMETER AsObject
    Return [DirectoryInfo] instead of a string path.

.OUTPUTS
    [string] or [System.IO.DirectoryInfo]
#>
function Get-KalmRepoRoot {
    [CmdletBinding(DefaultParameterSetName = 'Default')]
    # Indicate the returned type depending on the selected parameter set.
    [OutputType([string], ParameterSetName = 'Default')]
    [OutputType([System.IO.DirectoryInfo], ParameterSetName = 'AsObject')]
    param(
        [ValidateNotNullOrWhiteSpace()]
        [string] $StartPath = (Get-Location).ProviderPath,

        [ValidateNotNullOrWhiteSpace()]
        [string] $Marker = '.git',

        [Parameter(ParameterSetName = 'AsObject')]
        [switch] $AsObject
    )

    # Normalize and resolve the start path to a provider path early
    try {
        $resolved = Resolve-Path -LiteralPath $StartPath -ErrorAction Stop | Select-Object -First 1
        $current = $resolved.ProviderPath
    }
    catch {
        throw [System.IO.DirectoryNotFoundException]::new("StartPath not found: $StartPath")
    }

    while (-not (Test-Path -LiteralPath (Join-Path $current $Marker))) {
        Write-Verbose "Checking parent: $current"
        $parent = Split-Path -Parent $current
        if ($parent -eq $current) {
            throw [System.IO.DirectoryNotFoundException]::new(
                "Could not find '$Marker' above '$StartPath'."
            )
        }
        $current = $parent
    }

    if ($AsObject) { Get-Item -LiteralPath $current }
    else { $current }
}
