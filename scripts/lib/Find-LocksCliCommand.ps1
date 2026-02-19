function Find-LocksCliCommand {
    <#
    .SYNOPSIS
        Extracts and validates an executable command emitted by the locks CLI output.

    .DESCRIPTION
        The locks CLI is expected to emit a command line in stdout. This helper:
        - ignores null/blank lines
        - selects the last non-empty line
        - validates it against an allowlist prefix

    .PARAMETER Lines
        Output lines produced by the CLI process.

    .OUTPUTS
        System.String. The validated command line.
    #>
    [CmdletBinding()]
    [OutputType([string])]
    param(
        [Parameter(Mandatory)]
        [AllowEmptyString()]
        [string[]] $Lines
    )

    $command = $Lines |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
        Select-Object -Last 1

    if ([string]::IsNullOrWhiteSpace($command)) {
        throw "Locks CLI did not return a command."
    }

    $trimmed = $command.Trim()
    if ($trimmed -notmatch '^(?:\./gradlew(?:\.bat)?\b|gradlew(?:\.bat)?\b|git\b)') {
        throw "Unexpected command emitted by locks CLI: '$trimmed'"
    }

    $trimmed
}

