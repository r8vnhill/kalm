#Requires -Version 7.4
<#
.SYNOPSIS
    Entrypoint for executing the dependency-lock workflow CLI.

.DESCRIPTION
    Invokes the Gradle task `:tools:runLocksCli`, captures the emitted shell command, and executes
    that command from the repository root.

    This script intentionally keeps interactive and parameterized dependency-lock workflows outside
    Gradle task definitions. Gradle is responsible only for computing the correct command; this
    script is responsible for executing it.

    ## Execution flow:

    1. Resolve repository root.
    2. Resolve the Gradle wrapper (`gradlew` or `gradlew.bat`).
    3. Forward CLI arguments to `:tools:runLocksCli`.
    4. Capture Gradle output.
    5. Extract the emitted command via `Find-LocksCliCommand`.
    6. Execute the extracted command using the appropriate shell.
    7. Terminate with the executed command’s exit code.

    This script behaves as an entrypoint-style executable and intentionally terminates the
    PowerShell process using `exit <code>` to propagate the underlying command’s result to CI
    environments.

.EXAMPLE
    Write all locks:
        ./scripts/gradle/Invoke-LocksCli.ps1 write-all

.EXAMPLE
    Write locks for a module:
        ./scripts/gradle/Invoke-LocksCli.ps1 write-module --module :core

.EXAMPLE
    Write locks for a specific configuration:
        ./scripts/gradle/Invoke-LocksCli.ps1 write-configuration `
            --module :core `
            --configuration testRuntimeClasspath

    Show differences:
        ./scripts/gradle/Invoke-LocksCli.ps1 diff

.PARAMETER CliArgs
    Arguments forwarded to the locks CLI. All remaining arguments are treated as
    CLI input and passed through unchanged.

.OUTPUTS
    None. The script executes an emitted shell command and exits with that
    command’s exit code.

.NOTES
    Requirements:
      - PowerShell 7.4+
      - A valid Gradle wrapper at the repository root
      - Helper scripts in ../lib:
          - Get-KalmRepoRoot.ps1
          - Join-QuotedArgs.ps1
          - Find-LocksCliCommand.ps1

    Design considerations:
      - Uses `Set-StrictMode -Version 3.0` for defensive scripting.
      - Uses `$ErrorActionPreference = 'Stop'` for fail-fast behavior.
      - Uses `$InformationPreference = 'Continue'` to surface executed commands.
      - Uses `Write-Verbose` for trace-level diagnostics.
      - Command extraction is delegated to `Find-LocksCliCommand` to avoid
        brittle "last-line wins" parsing.

    Security note:
      The command executed is produced by the repository’s own Gradle task.
      If the Gradle output is compromised, this script will execute the emitted
      command. Ensure CI and repository integrity controls are in place.

.LINK
    Gradle Dependency Locking:
    https://docs.gradle.org/current/userguide/dependency_locking.html
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory, ValueFromRemainingArguments)]
    [string[]] $CliArgs
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'
$InformationPreference = 'Continue'

$repoRootScript = Join-Path $PSScriptRoot '..' 'lib' 'Get-KalmRepoRoot.ps1'
$quoteArgsScript = Join-Path $PSScriptRoot '..' 'lib' 'Join-QuotedArgs.ps1'
$findLocksCommandScript = Join-Path $PSScriptRoot '..' 'lib' 'Find-LocksCliCommand.ps1'
$resolveExecutionCommandScript = Join-Path $PSScriptRoot '..' 'lib' 'Resolve-LocksCliExecutionCommand.ps1'

foreach ($scriptPath in @($repoRootScript, $quoteArgsScript, $findLocksCommandScript, $resolveExecutionCommandScript)) {
    if (-not (Test-Path -LiteralPath $scriptPath)) {
        throw "Required helper script not found: '$scriptPath'"
    }
}

. $repoRootScript
. $quoteArgsScript
. $findLocksCommandScript
. $resolveExecutionCommandScript

$repoRoot = Get-KalmRepoRoot -StartPath $PSScriptRoot
Write-Verbose ("Resolved repository root: {0}" -f $repoRoot)
$gradlew = if ($IsWindows) {
    Join-Path $repoRoot 'gradlew.bat'
} else {
    Join-Path $repoRoot 'gradlew'
}
Write-Verbose ("Resolved Gradle wrapper: {0}" -f $gradlew)

if (-not (Test-Path -LiteralPath $gradlew)) {
    throw "Gradle wrapper not found at '$gradlew'."
}

$argsString = Join-QuotedArgs -Arguments $CliArgs
$gradleArgs = @(':tools:runLocksCli', "--args=$argsString", '--quiet')
Write-Verbose ("Gradle invocation args: {0}" -f ($gradleArgs -join ' '))

Push-Location $repoRoot
try {
    $rawOutput = & $gradlew @gradleArgs
    $gradleExitCode = $LASTEXITCODE
    if ($gradleExitCode -ne 0) {
        throw "Failed to run :tools:runLocksCli (exit code $gradleExitCode)."
    }
    $command = Find-LocksCliCommand -Lines $rawOutput
    $executionCommand = Resolve-LocksCliExecutionCommand -Command $command
    Write-Verbose ("Extracted command: {0}" -f $command)
    Write-Verbose ("Execution command: {0}" -f $executionCommand)
    Write-Information ("Executing: {0}" -f $executionCommand)
    if ($IsWindows) {
        & cmd /c $executionCommand
    } else {
        & sh -c $executionCommand
    }
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
