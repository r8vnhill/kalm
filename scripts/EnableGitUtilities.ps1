<#
.SYNOPSIS
    Loads the Keen.Git module from the local git/ps1 path.

.DESCRIPTION
    This script locates and attempts to import the `Keen.Git.psd1` module from a relative
    path within the script's root directory.
    If the module does not exist or fails to import, it reports an error.
    If successful, it imports the module into the current session.

.NOTES
    The module is expected to be located at:
    <script root>/git/ps1/Keen.Git.psd1

.EXAMPLE
    .\Initialize-GitModule.ps1
    Imports the Keen.Git module from the relative path.
#>

[CmdletBinding()]
param ()

$modulePath = Join-Path -Path $PSScriptRoot -ChildPath 'git\ps1\Keen.Git.psd1'

if (-not (Test-Path $modulePath)) {
    Write-Error "❌ Module not found at path: $modulePath"
    return
}

try {
    Import-Module -Name $modulePath -Force -ErrorAction Stop
    Write-Verbose "✅ Module imported successfully from: $modulePath"
} catch {
    Write-Error "❌ Failed to import module: $_"
}
