#Requires -Version 7.4
Set-StrictMode -Version 3

<#
.SYNOPSIS
    Finds the last line that is a valid JSON object in a mixed output stream.

.DESCRIPTION
    Scans lines from bottom to top and returns the last line that deserializes to a JSON object.

    ## Contract:

    - JSON object payloads (`{...}`) are accepted.
    - JSON arrays (`[...]`) are ignored.
    - JSON primitives (`"x"`, `1`, `true`, `false`, `null`) are ignored.
    - The JSON payload must be contained on a single line.
    - Leading and trailing whitespace around a candidate line is ignored.

.PARAMETER Lines
    Output lines to inspect. Empty lines are allowed.

.OUTPUTS
    [System.String]
    The matching JSON object line, or $null if none was found.
#>
function Find-JsonObjectLine {
    [CmdletBinding()]
    [OutputType([string])]
    param(
        [Parameter(Mandatory)]
        [AllowEmptyCollection()]
        [AllowEmptyString()]
        [string[]] $Lines
    )

    for ($i = $Lines.Count - 1; $i -ge 0; $i--) {
        $line = ($Lines[$i] ?? '').Trim()
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        try {
            $parsed = ConvertFrom-Json -InputObject $line -AsHashtable -ErrorAction Stop
            if ($parsed -is [hashtable]) {
                return $line
            }
        }
        catch {
            continue
        }
    }

    return $null
}
