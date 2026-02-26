function Measure-DemoRule {
    [CmdletBinding()]
    param([System.Management.Automation.Language.ScriptBlockAst] $ScriptBlockAst)
    [Microsoft.Windows.PowerShell.ScriptAnalyzer.Generic.DiagnosticRecord]::new('demo', $ScriptBlockAst.Extent, 'DemoRule', 'Warning', $null, $null)
}
