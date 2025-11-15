<#
.SYNOPSIS
    Resolve the repository Pester configuration file path and validate it exists.

.DESCRIPTION
    Builds the expected path to `pester.config.psd1` under the testing folder and
    verifies the file exists. On failure the function writes a helpful error and
    throws an exception to allow callers to fail-fast.

.PARAMETER ScriptRoot
    The script root (typically `$PSScriptRoot`) of the caller used to compute the
    expected settings file path.

.OUTPUTS
    [string] Absolute path to the resolved settings file.
#>
function Resolve-PesterSettingsPath {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string] $ScriptRoot
    )

    $settingsPath = Join-Path $ScriptRoot 'pester.config.psd1'
    if (-not (Test-Path -LiteralPath $settingsPath)) {
        Write-Error @(
            "Pester settings file not found: $settingsPath",
            "Create a 'pester.config.psd1' under 'scripts/testing' or run Invoke-Pester manually."
        ) -join [Environment]::NewLine
        throw 'Missing Pester runsettings file'
    }

    # Return a fully resolved path
    (Resolve-Path -LiteralPath $settingsPath -ErrorAction Stop).ProviderPath
}

Export-ModuleMember -Function Resolve-PesterSettingsPath
