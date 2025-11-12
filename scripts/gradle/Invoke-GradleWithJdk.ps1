#Requires -Version 7.4
using module ..\lib\ScriptLogging.psm1

<#
.SYNOPSIS
Runs the Gradle wrapper with an optional JAVA_HOME override.
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateNotNullOrWhiteSpace()]
    [ValidateScript({
            if (-not (Test-Path -Path $_ -PathType Container)) {
                throw [System.IO.DirectoryNotFoundException]::new("JDK path '$_' was not found.")
            }
            $true
        })]
    [string] $JdkPath,

    [Parameter(ValueFromRemainingArguments)]
    [ValidateNotNull()]
    [string[]] $GradleArgument = @('help'),

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [ValidateScript({
            if (-not (Test-Path -Path $_ -PathType Container)) {
                throw [System.IO.DirectoryNotFoundException]::new("Working directory '$_' was not found.")
            }
            $true
        })]
    [string] $WorkingDirectory = (Join-Path -Path (Split-Path -Parent $PSCommandPath) -ChildPath '..')
)
Set-StrictMode -Version 3.0

$logger = [KalmScriptLogger]::Start('Invoke-GradleWithJdk', $null)
$logger.LogInfo(("Script started with WorkingDirectory='{0}', GradleArgument='{1}', JdkPath='{2}'" -f $WorkingDirectory, ($GradleArgument -join ' '), ($JdkPath -or '<builtin>')),'Startup')
function Invoke-GradleWithJdk {
    [CmdletBinding()]
    param(
        [Parameter()]
        [string]
        $JdkPath,

        [Parameter(Mandatory = $true)]
        [string[]]
        $GradleArgument,

        [Parameter(Mandatory = $true)]
        [string]
        $WorkingDirectory
    )

    $resolvedWorkingDirectory = (Resolve-Path -Path $WorkingDirectory -ErrorAction Stop).ProviderPath
    $gradleExecutable = Join-Path -Path $resolvedWorkingDirectory -ChildPath 'gradlew'
    if (-not (Test-Path -Path $gradleExecutable -PathType Leaf)) {
        throw [System.IO.FileNotFoundException]::new("Gradle wrapper not found at '$gradleExecutable'.")
    }
    $gradleExecutable = (Resolve-Path -Path $gradleExecutable -ErrorAction Stop).ProviderPath

    $originalJavaHome = $env:JAVA_HOME
    $resolvedJdk = $null

    if ($PSBoundParameters.ContainsKey('JdkPath')) {
        $resolvedJdk = (Resolve-Path -Path $JdkPath -ErrorAction Stop).ProviderPath
        if ($env:JAVA_HOME -and ($env:JAVA_HOME -ne $resolvedJdk)) {
            Write-Verbose ("Overriding JAVA_HOME '{0}' with '{1}'." -f $env:JAVA_HOME, $resolvedJdk)
        }
        else {
            Write-Verbose ("Setting JAVA_HOME to '{0}'." -f $resolvedJdk)
        }
        $env:JAVA_HOME = $resolvedJdk
    }
    elseif (-not $env:JAVA_HOME) {
        Write-Warning 'JAVA_HOME is not set; Gradle will use the configured toolchain resolver.'
    }
    else {
        Write-Verbose ("Using existing JAVA_HOME '{0}'." -f $env:JAVA_HOME)
    }

    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $startedAt = Get-Date
    $exitCode = 0
    $errorRecord = $null

    try {
        Write-Verbose ('Invoking {0} {1}' -f $gradleExecutable, ($GradleArgument -join ' '))
        $logger.LogInfo(("Invoking Gradle: {0} {1}" -f $gradleExecutable, ($GradleArgument -join ' ')),'Execution')
        Push-Location -Path $resolvedWorkingDirectory
        try {
            & $gradleExecutable @GradleArgument
            $lastExitCodeVar = Get-Variable -Name LASTEXITCODE -ErrorAction SilentlyContinue
            if ($null -ne $lastExitCodeVar) {
                $exitCode = [int]$lastExitCodeVar.Value
            }
            else {
                $exitCode = 0
            }
        }
        finally {
            Pop-Location
        }
    }
    catch {
        $errorRecord = $_
        $lastExitCodeVar = Get-Variable -Name LASTEXITCODE -ErrorAction SilentlyContinue
        if ($null -ne $lastExitCodeVar) {
            $exitCode = [int]$lastExitCodeVar.Value
        }
        else {
            $exitCode = -1
        }
        $PSCmdlet.WriteError($errorRecord)
        $logger.LogError(("Gradle failed with exit code {0}: {1}" -f $exitCode, $_.Exception.Message),'Execution')
    }
    finally {
        if ($PSBoundParameters.ContainsKey('JdkPath')) {
            if ($null -ne $originalJavaHome) {
                $env:JAVA_HOME = $originalJavaHome
            }
            else {
                Remove-Item -Path Env:JAVA_HOME -ErrorAction SilentlyContinue
            }
        }
        $stopwatch.Stop()
    }

    [System.Environment]::ExitCode = $exitCode

    if ($exitCode -ne 0 -and -not $errorRecord) {
        $failure = New-Object System.Management.Automation.ErrorRecord (
            [System.Exception]::new("Gradle exited with code $exitCode."),
            'GradleFailed',
            [System.Management.Automation.ErrorCategory]::InvalidResult,
            $gradleExecutable
        )
        $PSCmdlet.WriteError($failure)
        $errorRecord = $failure
    }

    $logger.LogInfo(("Gradle finished with exit code {0} (success: {1})" -f $exitCode, ($exitCode -eq 0)),'Summary')

    [pscustomobject]@{
        Command          = $gradleExecutable
        Arguments        = $GradleArgument
        WorkingDirectory = $resolvedWorkingDirectory
        JavaHomeBefore   = $originalJavaHome
        JavaHomeApplied  = if ($resolvedJdk) { $resolvedJdk } else { $env:JAVA_HOME }
        ExitCode         = $exitCode
        Success          = ($exitCode -eq 0)
        StartedAt        = $startedAt
        Duration         = $stopwatch.Elapsed
        ErrorRecord      = $errorRecord
    }
}

$invokeArgs = @{
    GradleArgument   = $GradleArgument
    WorkingDirectory = $WorkingDirectory
}

if ($PSBoundParameters.ContainsKey('JdkPath')) {
    $invokeArgs['JdkPath'] = $JdkPath
}

Invoke-GradleWithJdk @invokeArgs
