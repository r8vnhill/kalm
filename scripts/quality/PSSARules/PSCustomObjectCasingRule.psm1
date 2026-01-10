function Measure-PSUsePSCustomObjectCasing {
    <#
    .SYNOPSIS
        Ensures PSCustomObject type literals use the canonical casing.

    .DESCRIPTION
        Flags uses of [pscustomobject] (lowercase) and recommends using [PSCustomObject] to keep
        type notation consistent. Also covers array notation ([pscustomobject[]]).

    .OUTPUTS
        Microsoft.Windows.PowerShell.ScriptAnalyzer.Generic.DiagnosticRecord
    #>
    [CmdletBinding()]
    [OutputType([Microsoft.Windows.PowerShell.ScriptAnalyzer.Generic.DiagnosticRecord])]
    param(
        [Parameter(Mandatory)]
        [System.Management.Automation.Language.ScriptBlockAst] $ScriptBlockAst
    )

    $diagnostics = New-Object 'System.Collections.Generic.List[Microsoft.Windows.PowerShell.ScriptAnalyzer.Generic.DiagnosticRecord]'

    $typeNodes = $ScriptBlockAst.FindAll(
        { param($node) $node -is [System.Management.Automation.Language.TypeExpressionAst] -or $node -is [System.Management.Automation.Language.TypeConstraintAst] },
        $true
    )

    foreach ($node in $typeNodes) {
        $rawText = $node.Extent.Text
        $trimmed = $rawText.Trim()

        $match = [regex]::Match($trimmed, '^\[(?<name>pscustomobject(\[\])?)\]$', [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
        if (-not $match.Success) { continue }

        $name = $match.Groups['name'].Value
        $isArray = $name.EndsWith('[]', [System.StringComparison]::OrdinalIgnoreCase)
        $expected = if ($isArray) { 'PSCustomObject[]' } else { 'PSCustomObject' }
        if ($name -cne $expected) {
            $message = "Use '[$expected]' instead of '$rawText' to keep PSCustomObject casing consistent."
            $diagnostics.Add(
                [Microsoft.Windows.PowerShell.ScriptAnalyzer.Generic.DiagnosticRecord]::new(
                    $message,
                    $node.Extent,
                    'PSUsePSCustomObjectCasing',
                    'Warning',
                    $null,
                    $null
                )
            )
        }
    }

    return $diagnostics.ToArray()
}

Export-ModuleMember -Function Measure-PSUsePSCustomObjectCasing
