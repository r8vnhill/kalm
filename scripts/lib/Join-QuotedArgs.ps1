function Join-QuotedArgs {
    <#
    .SYNOPSIS
        Converts an argument list into a single string suitable for Gradle's `--args=` forwarding.

    .DESCRIPTION
        Gradle expects a single string for `--args=`. This helper quotes each argument and escapes
        backslashes and double quotes so argument boundaries remain stable.

    .PARAMETER Arguments
        Argument tokens to quote and join.

    .OUTPUTS
        System.String. A single command-line string.
    #>
    [OutputType([string])]
    param(
        [Parameter(Mandatory)]
        [string[]] $Arguments
    )

    $escaped = foreach ($argument in $Arguments) {
        '"' + ($argument -replace '\\', '\\\\' -replace '"', '\"') + '"'
    }

    return ($escaped -join ' ')
}
