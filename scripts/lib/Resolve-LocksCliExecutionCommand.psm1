#Requires -Version 7.4
Set-StrictMode -Version 3.0

class LocksCliExecutionSpec {
    [string] $Executable
    [string[]] $Arguments

    LocksCliExecutionSpec([string] $executable, [string[]] $arguments) {
        $this.Executable = $executable
        $this.Arguments = $arguments
    }
}

<#
.SYNOPSIS
    Represents the components of a command prefix required for safe rewriting.

.DESCRIPTION
    The parser splits a command into three parts so rewriting logic can operate only on the command body
    while preserving formatting:

    - `{sh} LeadingWhitespace`: indentation or leading spacing before the command.
    - `{sh} QuotePrefix`: a single opening quote (`'` or `"`), if present.
    - `{sh} Body`: the remaining command text after whitespace and the optional opening quote.

    The quote is treated as a prefix only; the module does not attempt to locate or validate a closing quote.
#>
class LocksCliCommandPrefixParts {
    <#
    .SYNOPSIS
        Whitespace prefix found before the command content.
    #>
    [string] $LeadingWhitespace

    <#
    .SYNOPSIS
        A single leading quote prefix (`'` or `"`), if present.
    #>
    [string] $QuotePrefix

    <#
    .SYNOPSIS
        The command body after leading whitespace and the optional leading quote.
    #>
    [string] $Body

    LocksCliCommandPrefixParts([string] $leadingWhitespace, [string] $quotePrefix, [string] $body) {
        $this.LeadingWhitespace = $leadingWhitespace
        $this.QuotePrefix = $quotePrefix
        $this.Body = $body
    }
}

<#
.SYNOPSIS
    Splits a command into leading whitespace, an optional opening quote, and the remaining body.

.DESCRIPTION
    This function provides a small parsing step that enables prefix rewriting without disturbing
    output formatting. It:

    1. Computes the leading whitespace by comparing the original command with its left-trimmed form.
    2. Detects a single leading quote (`'` or `"`), treating it as a prefix.
    3. Returns the remainder as the command body to be inspected and potentially rewritten.

.PARAMETER Command
    The raw command string to parse.

.OUTPUTS
    LocksCliCommandPrefixParts. A record containing the parsed parts.

.EXAMPLE
    Split-LocksCliCommandPrefix -Command '  "./gradlew test"'
    # LeadingWhitespace = '  '
    # QuotePrefix       = '"'
    # Body              = './gradlew test"'
#>
function Split-LocksCliCommandPrefix {
    [CmdletBinding()]
    [OutputType([LocksCliCommandPrefixParts])]
    param(
        [Parameter(Mandatory)]
        [string] $Command
    )

    $trimmedCommand = $Command.TrimStart()
    $leadingWhitespaceLength = $Command.Length - $trimmedCommand.Length
    $leadingWhitespace = $Command.Substring(0, $leadingWhitespaceLength)
    $quotePrefix = ''
    $commandBody = $trimmedCommand

    if ($commandBody.StartsWith('"') -or $commandBody.StartsWith("'")) {
        $quotePrefix = $commandBody.Substring(0, 1)
        $commandBody = $commandBody.Substring(1)
    }

    return [LocksCliCommandPrefixParts]::new($leadingWhitespace, $quotePrefix, $commandBody)
}

<#
.SYNOPSIS
    Checks whether a matched prefix ends at a safe boundary.

.DESCRIPTION
    When rewriting a command prefix (for example, `{sh} ./gradlew`), it is important to avoid
    rewriting longer tokens that merely start with the same characters (for example,
    `{sh} ./gradlewX`). This function treats a prefix as safely matched when:

    - The value ends exactly at the prefix length, or
    - The next character is whitespace, or
    - The next character is a quote (`'` or `"`).

.PARAMETER Value
    The string being tested.

.PARAMETER PrefixLength
    The length of the prefix that was matched at the start of the string.

.OUTPUTS
    System.Boolean. `$true` when the prefix ends at a boundary, otherwise `$false`.

.EXAMPLE
    Test-LocksCliPrefixBoundary -Value './gradlew test' -PrefixLength 8
    # => $true

.EXAMPLE
    Test-LocksCliPrefixBoundary -Value './gradlewX test' -PrefixLength 8
    # => $false
#>
function Test-LocksCliPrefixBoundary {
    [CmdletBinding()]
    [OutputType([bool])]
    param(
        [Parameter(Mandatory)]
        [string] $Value,

        [Parameter(Mandatory)]
        [int] $PrefixLength
    )

    if ($Value.Length -eq $PrefixLength) {
        return $true
    }

    $next = $Value[$PrefixLength]
    return [char]::IsWhiteSpace($next) -or $next -eq '"' -or $next -eq "'"
}

<#
.SYNOPSIS
    Rewrites a Gradle wrapper invocation to `{cmd} gradlew.bat` when applicable.

.DESCRIPTION
    Attempts to rewrite the Gradle wrapper prefix in the provided command body. The function checks
    for the more specific `{sh} ./gradlew.bat` first, then falls back to `{sh} ./gradlew`.

    The returned string starts with `{cmd} gradlew.bat` and preserves the remaining suffix verbatim.
    If no supported prefix is matched, the function returns `$null`.

.PARAMETER CommandBody
    The command body to rewrite (typically produced by Split-LocksCliCommandPrefix).

.OUTPUTS
    System.String or `$null`. The rewritten command body, or `$null` if no rewrite applies.

.EXAMPLE
    Convert-LocksCliGradlewPrefix -CommandBody './gradlew test'
    # => 'gradlew.bat test'
#>
function Convert-LocksCliGradlewPrefix {
    [CmdletBinding()]
    [OutputType([string])]
    param(
        [Parameter(Mandatory)]
        [string] $CommandBody
    )

    $batRewrite = Convert-LocksCliPrefixIfMatched -CommandBody $CommandBody -Prefix './gradlew.bat'
    if ($null -ne $batRewrite) {
        return $batRewrite
    }

    return Convert-LocksCliPrefixIfMatched -CommandBody $CommandBody -Prefix './gradlew'
}

<#
.SYNOPSIS
    Rewrites a command body prefix to `{cmd} gradlew.bat` when the prefix matches at a boundary.

.DESCRIPTION
    If `{sh} CommandBody` begins with `{sh} Prefix` (case-insensitive) and the prefix ends at a boundary
    (see Test-LocksCliPrefixBoundary), the function replaces the prefix with `{cmd} gradlew.bat`.

    The suffix following the prefix is preserved as-is.

.PARAMETER CommandBody
    The command body to test and potentially rewrite.

.PARAMETER Prefix
    The prefix that must match at the start of `{sh} CommandBody` for rewriting to occur.

.OUTPUTS
    System.String or `$null`. The rewritten string, or `$null` if the prefix does not match safely.

.EXAMPLE
    Convert-LocksCliPrefixIfMatched -CommandBody './gradlew test' -Prefix './gradlew'
    # => 'gradlew.bat test'

.EXAMPLE
    Convert-LocksCliPrefixIfMatched -CommandBody './gradlewX test' -Prefix './gradlew'
    # => $null
#>
function Convert-LocksCliPrefixIfMatched {
    [CmdletBinding()]
    [OutputType([string])]
    param(
        [Parameter(Mandatory)]
        [string] $CommandBody,

        [Parameter(Mandatory)]
        [string] $Prefix
    )

    $comparison = [System.StringComparison]::OrdinalIgnoreCase
    if (
        -not $CommandBody.StartsWith($Prefix, $comparison) -or
        -not (Test-LocksCliPrefixBoundary -Value $CommandBody -PrefixLength $Prefix.Length)
    ) {
        return $null
    }

    return 'gradlew.bat' + $CommandBody.Substring($Prefix.Length)
}

<#
.SYNOPSIS
    Normalizes a locks CLI command to be executable on the current platform.

.DESCRIPTION
    The locks CLI emits shell-friendly commands such as `./gradlew ...`. On Windows, these commands are commonly
    executed through `{cmd} cmd /c`, which does not support executing files with the `{sh} ./` prefix. This module
    rewrites Gradle wrapper invocations so that an emitted command can be copy-pasted and executed reliably on
    the current platform.

    Specifically, when running in Windows mode, the function replaces a leading `{sh} ./gradlew` (or
    `{sh} ./gradlew.bat`) with `{cmd} gradlew.bat`, while preserving:

    - Any leading whitespace (useful when commands are indented in output),
    - An optional leading quote (`'` or `"`), and
    - The remainder of the command unchanged (arguments, quoting, and spacing).

    **The rewrite is boundary-aware:** the prefix must be followed by end-of-string or a boundary character (whitespace
    or a quote). This prevents accidental matches such as `{sh} ./gradlewX` or `{sh} ./gradlew.batX`.

.PARAMETER Command
    A validated command emitted by the locks CLI. Accepts direct binding, pipeline-by-value, and
    pipeline-by-property-name (`Command`). This function assumes the string is meant to be executed
    as a shell command, not as an already-tokenized argument list.

.PARAMETER ForceWindows
    Optional override primarily intended for tests. When `$true`, forces Windows rewriting behavior regardless of the
    current platform. Defaults to the current `$IsWindows`.

.OUTPUTS
    System.String. A platform-compatible command string.

.NOTES
    - The function is intentionally conservative: if the command does not start with a Gradle wrapper invocation, it is
      returned unchanged.
    - Only the Gradle wrapper prefix is rewritten. Other commands (for example, `{sh} git diff ...`) are not modified.

.EXAMPLE
    # Windows mode rewrites `{sh} ./gradlew` to `{cmd} gradlew.bat`
    Resolve-LocksCliExecutionCommand -Command './gradlew preflight --write-locks --no-parallel' -ForceWindows:$true
    # => gradlew.bat preflight --write-locks --no-parallel

.EXAMPLE
    # Leading whitespace and an opening quote are preserved
    Resolve-LocksCliExecutionCommand -Command '  "./gradlew test"' -ForceWindows:$true
    # =>   "gradlew.bat test"

.EXAMPLE
    # Non-wrapper commands are returned unchanged
    Resolve-LocksCliExecutionCommand -Command 'git diff -- **/gradle.lockfile settings-gradle.lockfile' -ForceWindows:$true
    # => git diff -- **/gradle.lockfile settings-gradle.lockfile
#>
function Resolve-LocksCliExecutionCommand {
    [CmdletBinding()]
    [OutputType([string])]
    param(
        [Parameter(Mandatory, ValueFromPipeline, ValueFromPipelineByPropertyName)]
        [string] $Command,

        [bool] $ForceWindows = $IsWindows
    )

    process {
        if (-not $ForceWindows) {
            return $Command
        }

        $parsed = Split-LocksCliCommandPrefix -Command $Command
        $comparison = [System.StringComparison]::OrdinalIgnoreCase
        if (-not $parsed.Body.StartsWith('./gradlew', $comparison)) {
            return $Command
        }

        $normalizedBody = Convert-LocksCliGradlewPrefix -CommandBody $parsed.Body
        if ($null -eq $normalizedBody) {
            return $Command
        }

        return "$($parsed.LeadingWhitespace)$($parsed.QuotePrefix)$normalizedBody"
    }
}

function ConvertTo-LocksCliExecutionSpec {
    [CmdletBinding()]
    [OutputType([LocksCliExecutionSpec])]
    param(
        [Parameter(Mandatory, ValueFromPipeline, ValueFromPipelineByPropertyName)]
        [string] $Command,

        [bool] $ForceWindows = $IsWindows
    )

    process {
        $normalized = Resolve-LocksCliExecutionCommand -Command $Command -ForceWindows:$ForceWindows
        $tokens = [System.Collections.Generic.List[string]]::new()
        $current = [System.Text.StringBuilder]::new()
        $inSingle = $false
        $inDouble = $false
        $escaped = $false

        foreach ($ch in $normalized.ToCharArray()) {
            if ($escaped) {
                [void] $current.Append($ch)
                $escaped = $false
                continue
            }

            if (-not $inSingle -and $ch -eq '\') {
                $escaped = $true
                continue
            }

            if (-not $inDouble -and $ch -eq "'") {
                $inSingle = -not $inSingle
                continue
            }

            if (-not $inSingle -and $ch -eq '"') {
                $inDouble = -not $inDouble
                continue
            }

            if (-not $inSingle -and -not $inDouble -and [char]::IsWhiteSpace($ch)) {
                if ($current.Length -gt 0) {
                    $tokens.Add($current.ToString())
                    [void] $current.Clear()
                }
                continue
            }

            [void] $current.Append($ch)
        }

        if ($escaped -or $inSingle -or $inDouble) {
            throw "Unable to parse command for execution due to unmatched escaping or quoting: '$normalized'"
        }
        if ($current.Length -gt 0) {
            $tokens.Add($current.ToString())
        }
        if ($tokens.Count -eq 0) {
            throw 'Unable to parse command for execution: no executable token found.'
        }

        $exe = $tokens[0]
        $args = if ($tokens.Count -gt 1) { $tokens.GetRange(1, $tokens.Count - 1).ToArray() } else { @() }
        return [LocksCliExecutionSpec]::new($exe, $args)
    }
}

Export-ModuleMember -Function Resolve-LocksCliExecutionCommand, ConvertTo-LocksCliExecutionSpec
