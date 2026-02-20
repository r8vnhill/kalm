#Requires -Version 7.4
Set-StrictMode -Version 3.0

<#
.SYNOPSIS
    Extracts the `command` field from a locks CLI JSON payload in mixed output.

.DESCRIPTION
    Requires `Find-JsonObjectLine` to be available in scope.
    Expects a JSON object payload containing a non-empty `command` field.

.PARAMETER Lines
    Output lines to inspect.

.OUTPUTS
    System.String. The emitted command.
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

    if (-not (Get-Command Find-JsonObjectLine -ErrorAction SilentlyContinue)) {
        throw "Find-LocksCliJsonCommand requires Find-JsonObjectLine to be available in scope."
    }

    $jsonLine = Find-JsonObjectLine -Lines $Lines
    if ([string]::IsNullOrWhiteSpace($jsonLine)) {
        throw "Locks CLI did not emit a JSON payload."
    }

    try {
        $payload = ConvertFrom-Json -InputObject $jsonLine -AsHashtable -ErrorAction Stop
    }
    catch {
        $snippet = if ($jsonLine.Length -gt 120) { $jsonLine.Substring(0, 120) + "..." } else { $jsonLine }
        throw "Failed to parse locks CLI JSON payload. Candidate line: $snippet"
    }

    if ($payload -isnot [hashtable]) {
        throw "Locks CLI JSON payload must be an object."
    }

    $command = $payload['command']
    if ([string]::IsNullOrWhiteSpace($command)) {
        throw "Locks CLI JSON payload did not include a command."
    }

    $command.Trim()
}
