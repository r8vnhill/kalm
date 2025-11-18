Set-StrictMode -Version 3.0

function ConvertTo-NormalizedPaths {
    [CmdletBinding()]
    param(
        [string[]] $Paths
    )

    if (-not $Paths) { return @() }

    $Paths |
        Where-Object { $_ } |
        ForEach-Object {
            ($_ -replace '\\', '/') -replace '/+', '/'
        }
}
