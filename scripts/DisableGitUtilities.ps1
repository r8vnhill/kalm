[CmdletBinding()]
param ()

# Define the name of the module to remove from the session
$moduleName = 'Keen.Git'

# Check whether the module is currently loaded in the session
if (-not (Get-Module -Name $moduleName)) {
    # If the module is not loaded, log a verbose message and exit
    Write-Verbose "ℹ️ Module '$moduleName' is not currently loaded."
    return
}

try {
    # Attempt to remove the module from the current session
    Remove-Module -Name $moduleName -Force -ErrorAction Stop
    # If successful, provide a verbose confirmation
    Write-Verbose "✅ Module '$moduleName' removed from session."
} catch {
    # If an error occurs during removal, report it as an error
    Write-Error "❌ Failed to remove module '$moduleName': $_"
}
