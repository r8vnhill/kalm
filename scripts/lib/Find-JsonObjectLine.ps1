function Find-JsonObjectLine {
    <#
    .SYNOPSIS
        Finds the last line that is a valid JSON object in a mixed output stream.

    .PARAMETER Lines
        Output lines to inspect.

    .OUTPUTS
        System.String. The matching JSON object line, or $null if none was found.
    #>
    [OutputType([string])]
    param(
        [Parameter(Mandatory)]
        [AllowEmptyString()]
        [string[]] $Lines
    )

    for ($i = $Lines.Count - 1; $i -ge 0; $i--) {
        $line = ($Lines[$i] ?? '').Trim()
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        try {
            $parsed = $line | ConvertFrom-Json -ErrorAction Stop
            if ($parsed -is [PSCustomObject]) {
                return $line
            }
        }
        catch {
            continue
        }
    }

    return $null
}
