#Requires -Version 7.5
using module ./lib/ScriptLogging.psm1
[CmdletBinding()]
param(
    [string] $Settings = "$PSScriptRoot/PSScriptAnalyzerSettings.psd1",
    [string[]] $Paths = @(
        "$PSScriptRoot"
    )
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$logger = [KalmScriptLogger]::Start('Invoke-PSSA', $null)
$logger.LogInfo(("Starting PSScriptAnalyzer with settings '{0}'." -f $Settings),'Startup')

function Install-PSScriptAnalyzerIfMissing {
    # Already available?
    if (Get-Command Invoke-ScriptAnalyzer -ErrorAction SilentlyContinue) {
        return
    }

    Write-Output 'PSScriptAnalyzer not found. Installing...' 
    [KalmScriptLogger]::LogIfConfigured([KalmLogLevel]::Warning, 'PSScriptAnalyzer missing; attempting installation.','Setup')

    # Make sure TLS 1.2 is used for PSGallery over HTTPS
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    }
    catch {
        Write-Warning "Failed to set TLS 1.2: $_"
    }

    # Prefer PSResourceGet if available (PowerShellGet v3)
    if (Get-Command Install-PSResource -ErrorAction SilentlyContinue) {
        # Ensure PSGallery is registered
        if (-not (Get-PSResourceRepository -ErrorAction SilentlyContinue | 
                    Where-Object Name -EQ 'PSGallery')) {
            Register-PSResourceRepository -Name PSGallery `
                -Uri 'https://www.powershellgallery.com/api/v2' -Trusted
        }
        Install-PSResource PSScriptAnalyzer -Scope CurrentUser -TrustRepository -Quiet
    }
    else {
        # Fallback to PowerShellGet v2
        if (-not (Get-Module -ListAvailable PowerShellGet)) {
            Write-Output 'PowerShellGet not found; attempting to install/update...' 
            # Try to get PowerShellGet using the in-box bootstrap
            Install-Module PowerShellGet -Scope CurrentUser -Force -AllowClobber `
                -ErrorAction SilentlyContinue
        }
        if (-not (Get-PSRepository -ErrorAction SilentlyContinue | Where-Object Name -EQ 'PSGallery')) {
            Register-PSRepository -Name PSGallery `
                -SourceLocation 'https://www.powershellgallery.com/api/v2' `
                -InstallationPolicy Trusted
        }
        Install-Module PSScriptAnalyzer -Scope CurrentUser -Force -AllowClobber
    }

    Import-Module PSScriptAnalyzer -Force
    if (-not (Get-Command Invoke-ScriptAnalyzer -ErrorAction SilentlyContinue)) {
        throw 'PSScriptAnalyzer installation/import failed.'
    }
}

# --- main ---
try {
    if (-not (Test-Path -LiteralPath $Settings)) {
        throw "Settings file not found: $Settings"
    }

    Write-Output 'Running PSScriptAnalyzer...'
    Write-Output "Settings: $Settings"
    Write-Output "Paths: $($Paths -join ', ')"
    $logger.LogInfo(("Analyzing paths: {0}" -f ($Paths -join ', ')),'Execution')

    Install-PSScriptAnalyzerIfMissing

$allResults = @()
foreach ($p in $Paths) {
    if (-not (Test-Path -LiteralPath $p)) { continue }
    $rp = (Resolve-Path -LiteralPath $p).Path  # normalize to a single [string]
    Write-Output "Analyzing: $rp"
    $res = Invoke-ScriptAnalyzer -Path $rp -Settings $Settings -Recurse -ErrorAction Stop
    if ($res) { $allResults += $res }
}

# Pretty print + fail on errors
if ($allResults) {
    $allResults |
        Sort-Object Severity, RuleName, ScriptName, Line |
        Format-Table Severity, RuleName, ScriptName, Line, Message -Auto

    if ($allResults.Where({ $_.Severity -eq 'Error' }).Count -gt 0) {
        $logger.LogWarning('PSScriptAnalyzer reported errors.','Summary')
        exit 1
    }
    else { 
        Write-Warning 'PSScriptAnalyzer found warnings/information.' 
        $logger.LogWarning('PSScriptAnalyzer completed with warnings/information.','Summary')
    }
}
else {
    Write-Output 'No issues found. ✅'
    $logger.LogInfo('PSScriptAnalyzer reported no issues.','Summary')
}
}
catch {
    $logger.LogError(("Invoke-PSSA failed: {0}" -f $_.Exception.Message),'Failure')
    throw
}

