function Invoke-GitSafely {
    [CmdletBinding()]
    [OutputType([string])]
    param (
        [Parameter(Mandatory)]
        [ValidateNotNull()]
        [System.Management.Automation.CommandInfo]
        $Git,

        [Parameter(Mandatory)]
        [ValidateNotNull()]
        [string[]]
        $Arguments,
    
        [Parameter(Mandatory)]
        [ValidateNotNullOrEmpty()]
        [string]
        $Context)

    $output = & $Git @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        $formattedMessage = @("❌ git $Context failed (exit $LASTEXITCODE)",
            "→ Command: git $($Arguments -join ' ')",
            "→ Output: $output")
        throw [System.Management.Automation.ErrorRecord]::new(
            [System.Exception]::new($formattedMessage),
            "Git$($Context)Failed",
            [System.Management.Automation.ErrorCategory]::InvalidOperation,
            $Arguments)
    }
    return [GitExecutionResult]@{
        ExitCode = $LASTEXITCODE
        Output   = $output
        Command  = "git $($Arguments -join ' ')"
    }
}
