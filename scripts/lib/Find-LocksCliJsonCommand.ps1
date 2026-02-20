#Requires -Version 7.4
Set-StrictMode -Version 3.0

# Dot-source the required script
$findJsonObjectLineScript = Join-Path $PSScriptRoot 'Find-JsonObjectLine.ps1'
if (-not (Test-Path -LiteralPath $findJsonObjectLineScript)) {
    throw "Required script not found: $findJsonObjectLineScript"
}
. $findJsonObjectLineScript

<#
.SYNOPSIS
    Extracts the `command` field from a locks CLI JSON payload embedded in mixed output.

.DESCRIPTION
    Scans a sequence of output lines (for example, Gradle logs mixed with structured JSON), locates
    the last valid JSON object line using `Find-JsonObjectLine`, parses it, and returns the trimmed
    value of its `command` field.

    This function is intended to be used in automation scripts where a Gradle task emits a
    machine-readable JSON payload at the end of execution. Human-readable log lines may precede or
    follow the payload.

    ## Contract:

    - The payload must be a single-line JSON object.
    - The payload must contain a non-empty `command` field.
    - The `command` field must be a string.
    - The function throws if the payload is missing, malformed, or incomplete.

    This script dot-sources `Find-JsonObjectLine.ps1` from the same directory.

.PARAMETER Lines
    The output lines to inspect. May be empty. Blank lines are ignored during scanning.

.OUTPUTS
    System.String.
    The trimmed shell command extracted from the JSON payload.

.NOTES
    ## Throws

    - If `Find-JsonObjectLine.ps1` cannot be found next to this script.
    - If no JSON payload is found.
    - If the JSON payload cannot be parsed.
    - If the payload is not a JSON object.
    - If the `command` field is missing or empty.

    ## Implementation details:

    - Uses `ConvertFrom-Json -AsHashtable` to avoid PSCustomObject property binding semantics and
      ensure consistent key lookup.
    - Returns a trimmed string to eliminate surrounding whitespace.
    - Includes a truncated snippet of malformed JSON in parse errors to aid debugging without
      flooding logs.

.EXAMPLE
    $lines = @(
        'Gradle output...'
        '{"exitCode":0,"command":"./gradlew preflight --write-locks"}'
    )

    $cmd = Find-LocksCliJsonCommand -Lines $lines
    # $cmd = './gradlew preflight --write-locks'

.LINK
    Find-JsonObjectLine
#>
function Find-LocksCliJsonCommand {
    [CmdletBinding()]
    [OutputType([string])]
    param(
        [Parameter(Mandatory)]
        [AllowEmptyCollection()]
        [AllowEmptyString()]
        [string[]] $Lines
    )

    $jsonLine = Find-JsonObjectLine -Lines $Lines
    if ([string]::IsNullOrWhiteSpace($jsonLine)) {
        throw 'Locks CLI did not emit a JSON payload.'
    }

    try {
        $payload = ConvertFrom-Json -InputObject $jsonLine -AsHashtable -ErrorAction Stop
    }
    catch {
        $snippet = if ($jsonLine.Length -gt 120) {
            $jsonLine.Substring(0, 120) + '...'
        }
        else {
            $jsonLine
        }
        throw "Failed to parse locks CLI JSON payload. Candidate line: $snippet"
    }

    if ($payload -isnot [hashtable]) {
        throw 'Locks CLI JSON payload must be an object.'
    }

    $command = $payload['command']
    if ([string]::IsNullOrWhiteSpace($command)) {
        throw 'Locks CLI JSON payload did not include a command.'
    }

    $command.Trim()
}
