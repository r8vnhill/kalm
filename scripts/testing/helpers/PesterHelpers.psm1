using module ..\..\..\lib\ScriptLogging.psm1

function Ensure-PesterModule {
    [CmdletBinding()]
    param(
        [string] $ModuleName = 'Pester',
        [KalmScriptLogger] $Logger = $null
    )

    try {
        Import-Module $ModuleName -ErrorAction Stop
        if ($Logger -ne $null) {
            $Logger.LogInfo(('Imported module: {0}' -f $ModuleName), 'Startup')
        }
        else {
            Write-Verbose ("Imported module: $ModuleName")
        }
    }
    catch {
        if ($Logger -ne $null) {
            $Logger.LogError(('Failed to import {0} module: {1}' -f $ModuleName, $_.Exception.Message), 'Failure')
        }
        else {
            Write-Error ("Failed to import ${ModuleName}: $($_.Exception.Message)")
        }
        throw
    }
}

Export-ModuleMember -Function Ensure-PesterModule
