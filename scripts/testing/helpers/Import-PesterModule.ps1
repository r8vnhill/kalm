#Requires -Version 7.4

<#
.SYNOPSIS
    Import a module (commonly 'Pester') with guarded error handling and structured logging.

.DESCRIPTION
    Import-PesterModule centralizes external module loading for the test harness,
    logging the exact source/version on success and providing a readable failure
    when nothing matches the requested name/version.

.PARAMETER ModuleName
    Name of the module to import in the `ByName` parameter set. Defaults to 'Pester'.

.PARAMETER MinimumVersion
    Minimum semantic version required when importing by name. Defaults to 5.7.1.

.PARAMETER ModulePath
    Literal path to a module file for the `ByPath` parameter set.

.PARAMETER Logger
    `KalmScriptLogger` instance used for structured log events.

.OUTPUTS
    [System.Management.Automation.PSModuleInfo]
#>
function Import-PesterModule {
    [CmdletBinding(DefaultParameterSetName = 'ByName')]
    [OutputType([System.Management.Automation.PSModuleInfo])]
    param(
        [Parameter(ParameterSetName = 'ByName')]
        [ValidateNotNullOrWhiteSpace()]
        [string] $ModuleName = 'Pester',

        [Parameter(ParameterSetName = 'ByName')]
        [Version] $MinimumVersion = [Version]'5.7.1',

        [Parameter(Mandatory, ParameterSetName = 'ByPath')]
        [ValidateNotNullOrWhiteSpace()]
        [string] $ModulePath,

        [Parameter(Mandatory)]
        [KalmScriptLogger] $Logger
    )

    try {
        $moduleInfo = $null

        switch ($PSCmdlet.ParameterSetName) {
            'ByPath' {
                $Logger.LogInfo(("Importing module from path: {0}" -f $ModulePath), 'Startup')

                if (-not (Test-Path -LiteralPath $ModulePath)) {
                    $Logger.LogError(("ModulePath not found: {0}" -f $ModulePath), 'Failure')
                    throw [System.IO.FileNotFoundException]::new("ModulePath not found: $ModulePath")
                }

                if ((Get-Command Import-Module).Parameters.Keys -contains 'LiteralPath') {
                    $moduleInfo = Import-Module -LiteralPath $ModulePath -PassThru -ErrorAction Stop
                }
                else {
                    $moduleInfo = Import-Module $ModulePath -PassThru -ErrorAction Stop
                }
            }

            'ByName' {
                $Logger.LogInfo(("Importing module by name: {0} (>= {1})" -f $ModuleName, $MinimumVersion), 'Startup')

                try {
                    $moduleInfo = Import-Module -Name $ModuleName -MinimumVersion $MinimumVersion -PassThru -ErrorAction Stop
                }
                catch {
                    $available = (Get-Module -ListAvailable -Name $ModuleName |
                                  Sort-Object Version -Descending |
                                  Select-Object -ExpandProperty Version -Unique) -join ', '
                    if ($available) {
                        $msg = "Failed to import module '{0}' with MinimumVersion {1}. Available versions: {2}" -f $ModuleName, $MinimumVersion, $available
                    }
                    else {
                        $msg = "Failed to import module '{0}': no versions found on this system." -f $ModuleName
                    }
                    throw [System.InvalidOperationException]::new($msg, $_.Exception)
                }
            }
        }

        $source = if ($moduleInfo.Path) { $moduleInfo.Path } else { $moduleInfo.Name }
        $Logger.LogInfo(("Imported module: {0} (v{1})" -f $source, $moduleInfo.Version), 'Startup')
        return $moduleInfo
    }
    catch {
        $Logger.LogError(("Module import failed: {0}" -f $_.Exception.Message), 'Failure')
        throw
    }
}
