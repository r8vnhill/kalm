#Requires -Version 7.4
Set-StrictMode -Version 3.0

<#
.SYNOPSIS
    Resolve the repository Pester configuration file path and validate it exists.

.DESCRIPTION
    Builds the expected path to `pester.config.psd1` under the testing folder and verifies the file
    exists. On failure the function writes a helpful error and throws an exception to allow callers
    to fail-fast.

.PARAMETER ScriptRoot
    The script root (typically `$PSScriptRoot`) of the caller used to compute the expected settings
    file path.

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
        $message = @(
            "Pester settings file not found: $settingsPath",
            "Create a 'pester.config.psd1' under 'scripts/testing' or run Invoke-Pester manually."
        ) -join [Environment]::NewLine
        Write-Error -Message $message
        throw 'Missing Pester runsettings file'
    }

    # Return a fully resolved path
    (Resolve-Path -LiteralPath $settingsPath -ErrorAction Stop).ProviderPath
}

<#
.SYNOPSIS
    Extract test file patterns from a Pester settings object.

.DESCRIPTION
    Normalizes the `Run.Path` value from the provided settings into an array of patterns. Accepts
    both scalar strings and arrays. Returns an empty array when `Run.Path` is missing.

    This function handles both hashtables (as returned by Import-PowerShellDataFile) and
    PSCustomObjects. It safely navigates nested properties/keys to extract Run.Path values.

.PARAMETER Settings
    A settings object (hashtable from Import-PowerShellDataFile or PSCustomObject) that may
    contain `Run.Path`. Accepts pipeline input by value or by property name.

.OUTPUTS
    [string[]] Array of patterns (possibly empty).

.EXAMPLE
    # Load settings from file and extract patterns
    $settings = Import-PowerShellDataFile -Path 'pester.config.psd1'
    $patterns = Get-PesterPatterns -Settings $settings

.EXAMPLE
    # Pipeline usage with hashtable
    @{ Run = @{ Path = '**/*.Tests.ps1' } } | Get-PesterPatterns
#>
function Get-PesterPatterns {
    [CmdletBinding()]
    [OutputType([string[]])]
    param(
        [Parameter(Mandatory = $false, ValueFromPipeline = $true, ValueFromPipelineByPropertyName = $true)]
        $Settings
    )

    # Handle both hashtable (from Import-PowerShellDataFile) and PSCustomObject inputs.
    # Safely navigate to Run.Path without tripping StrictMode on missing members.
    $paths = $null
    if ($Settings) {
        $runConfig = $null
        if ($Settings -is [System.Collections.IDictionary]) {
            $runConfig = $Settings['Run']
        }
        elseif ($Settings.PSObject -and $Settings.PSObject.Properties.Match('Run').Count -gt 0) {
            $runConfig = $Settings.Run
        }

        if ($runConfig) {
            if ($runConfig -is [System.Collections.IDictionary]) {
                $paths = $runConfig['Path']
            }
            elseif (
                $runConfig.PSObject -and $runConfig.PSObject.Properties.Match('Path').Count -gt 0
            ) {
                $paths = $runConfig.Path
            }
        }
    }

    if (-not $paths) {
        return @()
    }

    # Normalize to [string[]] so callers always get an array, even for a single path
    [string[]]$normalized = $paths
    return $normalized
}

Export-ModuleMember -Function Resolve-PesterSettingsPath, Get-PesterPatterns
