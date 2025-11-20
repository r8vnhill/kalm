#Requires -Version 7.4
Set-StrictMode -Version 3.0

<#
.SYNOPSIS
    Normalizes path separators for consistent comparisons.

.DESCRIPTION
    Converts incoming paths to use forward slashes and compresses duplicate separators. Accepts
    pipeline input so callers can stream paths directly.

.PARAMETER Path
    One or more paths to normalize; accepts pipeline input.

.OUTPUTS
    System.String

.EXAMPLE
    'C:\foo\bar', '/tmp//foo' | ConvertTo-NormalizedPath
#>
function ConvertTo-NormalizedPath {
    [CmdletBinding()]
    [OutputType([string])]
    param(
        [Parameter(ValueFromPipeline, ValueFromPipelineByPropertyName)]
        [string[]] $Path
    )

    process {
        foreach ($item in $Path) {
            if ([string]::IsNullOrWhiteSpace($item)) { continue }
            $normalized = ($item -replace '\\', '/') -replace '/+', '/'
            Write-Output $normalized
        }
    }
}
