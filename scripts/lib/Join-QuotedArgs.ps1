<#
.SYNOPSIS
    Converts an argument list into a single string suitable for Gradle's `--args=` forwarding.

.DESCRIPTION
    Gradle expects a single string for `--args=`. This helper quotes each argument and escapes
    backslashes and double quotes so argument boundaries remain stable. This contract is intended
    for direct Gradle wrapper invocation used in this repository's PowerShell 7+ scripts (not
    `Start-Process -ArgumentList` semantics).

.PARAMETER Arguments
    Argument tokens to quote and join. Null tokens are normalized to empty strings.

.OUTPUTS
    [System.String]
    A single command-line string.
#>
function Join-QuotedArgs {
    [CmdletBinding()]
    [OutputType([string])]
    param(
        [Parameter(Mandatory)]
        [AllowEmptyString()]
        [string[]] $Arguments
    )

    $Arguments |
        ForEach-Object {
            $token = if ($null -eq $_) { '' } else { [string]$_ }
            $escaped = $token.Replace('\', '\\').Replace('"', '\"')
            '"' + $escaped + '"'
        } |
        Join-String -Separator ' '
}
