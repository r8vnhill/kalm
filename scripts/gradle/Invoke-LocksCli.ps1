#Requires -Version 7.4
<#
.SYNOPSIS
    Runs the dependency-lock workflow CLI from :tools and executes the emitted command.

.DESCRIPTION
    This script keeps interactive or parameterized lock workflows outside Gradle tasks.
    It invokes `:tools:runLocksCli`, captures the generated shell command, and executes it from repository root.

    Examples:
      ./scripts/gradle/Invoke-LocksCli.ps1 write-all
      ./scripts/gradle/Invoke-LocksCli.ps1 write-module --module :core
      ./scripts/gradle/Invoke-LocksCli.ps1 write-configuration --module :core --configuration testRuntimeClasspath
      ./scripts/gradle/Invoke-LocksCli.ps1 diff

.PARAMETER CliArgs
    Arguments forwarded to the locks CLI.
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory, ValueFromRemainingArguments)]
    [string[]] $CliArgs
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot '..' 'lib' 'Get-KalmRepoRoot.ps1')
. (Join-Path $PSScriptRoot '..' 'lib' 'Join-QuotedArgs.ps1')

$repoRoot = Get-KalmRepoRoot -StartPath $PSScriptRoot
$gradlew = if ($IsWindows) {
    Join-Path $repoRoot 'gradlew.bat'
} else {
    Join-Path $repoRoot 'gradlew'
}

if (-not (Test-Path -LiteralPath $gradlew)) {
    throw "Gradle wrapper not found at '$gradlew'."
}

$argsString = Join-QuotedArgs -Arguments $CliArgs
$gradleArgs = @(':tools:runLocksCli', "--args=$argsString", '--quiet')

Push-Location $repoRoot
try {
    $rawOutput = & $gradlew @gradleArgs
    $gradleExitCode = $LASTEXITCODE
    if ($gradleExitCode -ne 0) {
        throw "Failed to run :tools:runLocksCli (exit code $gradleExitCode)."
    }
    $lastLine = $rawOutput | Select-Object -Last 1
    if ($null -eq $lastLine) {
        throw "Locks CLI did not return any output."
    }
    $command = $lastLine.Trim()
    if ([string]::IsNullOrWhiteSpace($command)) {
        throw "Locks CLI did not return a command."
    }
    Write-Information ("Executing: {0}" -f $command) -InformationAction Continue
    if ($IsWindows) {
        & cmd /c $command
    } else {
        & sh -c $command
    }
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
