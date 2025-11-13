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
        [Parameter(ParameterSetName = 'ByName', ValueFromPipelineByPropertyName)]
        [ValidateNotNullOrWhiteSpace()]
        [string] $ModuleName = 'Pester',

        [Parameter(ParameterSetName = 'ByName', ValueFromPipelineByPropertyName)]
        [Version] $MinimumVersion = [Version]'5.7.1',

        [Parameter(Mandatory, ParameterSetName = 'ByPath', ValueFromPipelineByPropertyName)]
        [ValidateNotNullOrWhiteSpace()]
        [string] $ModulePath,

        [Parameter(Mandatory, ValueFromPipelineByPropertyName)]
        [KalmScriptLogger] $Logger
    )

    try {
        $moduleInfo = $null

        switch ($PSCmdlet.ParameterSetName) {
            'ByPath' {
                # Defer to helper which handles path validation and import semantics
                $moduleInfo = Import-PesterModuleByPath -ModulePath $ModulePath -Logger $Logger
            }

            'ByName' {
                # Defer to helper which wraps Import-Module by name and surfaces helpful errors
                $moduleInfo = Import-PesterModuleByName -ModuleName $ModuleName -MinimumVersion $MinimumVersion -Logger $Logger
            }
        }

        $source = if ($moduleInfo.Path) { $moduleInfo.Path } else { $moduleInfo.Name }
        $Logger.LogInfo(('Imported module: {0} (v{1})' -f $source, $moduleInfo.Version), 'Startup')
        return $moduleInfo
    }
    catch {
        $Logger.LogError(('Module import failed: {0}' -f $_.Exception.Message), 'Failure')
        throw
    }
}


function Import-PesterModuleByPath {
    param(
        [ValidateNotNullOrWhiteSpace()]
        [string] $ModulePath,

        [KalmScriptLogger] $Logger
    )

    $Logger.LogInfo(('Importing module from path: {0}' -f $ModulePath), 'Startup')

    if (-not (Test-Path -LiteralPath $ModulePath)) {
        $Logger.LogError(('ModulePath not found: {0}' -f $ModulePath), 'Failure')
        throw [System.IO.FileNotFoundException]::new("ModulePath not found: $ModulePath")
    }

    if ((Get-Command Import-Module).Parameters.Keys -contains 'LiteralPath') {
        return Import-Module -LiteralPath $ModulePath -PassThru -ErrorAction Stop
    }
    else {
        return Import-Module $ModulePath -PassThru -ErrorAction Stop
    }
}


function Import-PesterModuleByName {
    param(
        [ValidateNotNullOrWhiteSpace()]
        [string] $ModuleName,

        [Version] $MinimumVersion,

        [KalmScriptLogger] $Logger
    )

    $Logger.LogInfo(('Importing module by name: {0} (>= {1})' -f $ModuleName, $MinimumVersion), 'Startup')

    try {
        return Import-Module -Name $ModuleName -MinimumVersion $MinimumVersion -PassThru -ErrorAction Stop
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
