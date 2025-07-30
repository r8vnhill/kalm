<#
.SYNOPSIS
Checks whether a command exists, and provides a fallback if it does not.

.DESCRIPTION
This utility function attempts to locate a command by name using `Get-Command`.
If the command exists, it returns the command information.
If the command is not found, it executes the fallback script block provided via the `ElseScriptBlock` parameter.

Useful for writing robust scripts that can degrade gracefully or provide clear guidance when dependencies are missing.

.PARAMETER Command
The name of the command to test.
Must not be null or empty.

.PARAMETER ElseScriptBlock
A script block that will be invoked only if the command is not found.

.EXAMPLE
$git = Get-CommandOrElse 'git' {
    throw '❌ git is required to continue.'
}

Stores the `git` command object in a variable, or throws an error if `git` is missing.
#>
function Get-CommandOrElse {
    [CmdletBinding()]
    [OutputType([System.Management.Automation.CommandInfo])]
    param (
        # The name of the command to check
        [Parameter(Mandatory, Position = 0)]
        [ValidateNotNullOrEmpty()]
        [string] $Command,

        # The script block to execute if the command does not exist
        [Parameter(Mandatory, Position = 1)]
        [Alias("Else")]
        [scriptblock] $ElseScriptBlock
    )

    # Check if the command exists
    $cmd = Get-Command -Name $Command -ErrorAction SilentlyContinue

    if ($null -ne $cmd) {
        # Return the command object if it exists
        Write-Verbose "✅ Found command: $Command"
        return $cmd
    }

    # If not found, execute the fallback script block
    Write-Verbose "⚠️ Command '$Command' not found. Executing fallback."
    & $ElseScriptBlock
}
