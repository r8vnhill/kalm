#Requires -Version 7.4

<#
.SYNOPSIS
Runs the Gradle wrapper with an optional JAVA_HOME override.

.DESCRIPTION
Sets JAVA_HOME to the supplied JDK path (if provided), invokes the Gradle wrapper
from the repository root, and returns a structured object describing the
execution. JAVA_HOME is restored afterwards.

.PARAMETER JdkPath
Optional path to the JDK home directory that should be used for the invocation.
The path must exist and point to a directory.

.PARAMETER GradleArgument
Arguments passed to the Gradle wrapper. Defaults to the "help" task when none
are supplied.

.PARAMETER WorkingDirectory
Directory that contains the Gradle wrapper script. Defaults to the repository
root (one level above this script).

.INPUTS
None

.OUTPUTS
System.Management.Automation.PSCustomObject

.EXAMPLE
PS> .\scripts\Invoke-GradleWithJdk.ps1 -JdkPath 'B:\scoop\apps\temurin22-jdk\current' -GradleArgument 'clean','build','--no-daemon'

Runs the Gradle wrapper using the specified Temurin JDK installation.
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
    $gradleWrapperName = if ($IsWindows) { 'gradlew.bat' } else { 'gradlew' }
    $gradleExecutable = Join-Path -Path $resolvedWorkingDirectory -ChildPath $gradleWrapperName
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
    $process = $null

    try {
        Write-Verbose ('Invoking {0} {1}' -f $gradleExecutable, ($GradleArgument -join ' '))
        Push-Location -Path $resolvedWorkingDirectory
        try {
            $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
            $startInfo.FileName = $gradleExecutable
            $startInfo.WorkingDirectory = $resolvedWorkingDirectory
            $startInfo.UseShellExecute = $false
            $startInfo.CreateNoWindow = $true
            foreach ($arg in $GradleArgument) {
                $startInfo.ArgumentList.Add($arg)
            }

            $process = [System.Diagnostics.Process]::new()
            $process.StartInfo = $startInfo
            if (-not $process.Start()) {
                throw [System.ComponentModel.Win32Exception]::new('Failed to start the Gradle wrapper.')
            }

            $process.WaitForExit()
            $exitCode = $process.ExitCode
        }
        finally {
            Pop-Location
        }
    }
    catch {
        $errorRecord = $_
        if ($null -ne $process -and $process.HasExited) {
            $exitCode = $process.ExitCode
        }
        else {
            $lastExitCodeVar = Get-Variable -Name LASTEXITCODE -ErrorAction SilentlyContinue
            if ($null -ne $lastExitCodeVar) {
                $exitCode = [int]$lastExitCodeVar.Value
            }
            else {
                $exitCode = -1
            }
        }
        $PSCmdlet.WriteError($errorRecord)
    }
    finally {
        if ($null -ne $process) {
            $process.Dispose()
        }

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
