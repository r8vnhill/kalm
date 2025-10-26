<#
.SYNOPSIS
Run Gradle wrapper with a specified JDK by setting JAVA_HOME for the invocation.

Usage:
  .\scripts\run-with-jdk.ps1 -JdkPath 'C:\Program Files\Java\jdk-22' -Args 'updateDependencies','--no-daemon'
#>

[CmdletBinding()]
param(
    [string]$JdkPath = $null,
    [string[]]$Args = @('help')
)

if ($JdkPath) {
    if (-not (Test-Path $JdkPath)) {
        Write-Error "JDK path not found: $JdkPath"
        exit 1
    }
    Write-Host "Using JDK: $JdkPath"
    $env:JAVA_HOME = $JdkPath
} else {
    if (-not $env:JAVA_HOME) {
        Write-Host "No -JdkPath provided and JAVA_HOME is not set. Attempting to run Gradle with system Java."
    } else {
        Write-Host "Using existing JAVA_HOME: $env:JAVA_HOME"
    }
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$gradlew = Join-Path $scriptDir '..\gradlew'
$gradlewFull = Resolve-Path $gradlew

Write-Host "Running: $($gradlewFull) $($Args -join ' ')"
& $gradlewFull @Args
exit $LASTEXITCODE
