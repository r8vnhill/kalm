#Requires -Version 7.0
<#
.SYNOPSIS
    Runs the Hadolint CLI via Gradle and returns a JSON payload on stdout.

.DESCRIPTION
    This script is a thin PowerShell wrapper around the Gradle task `:tools:runHadolintCli`.

    ## Key behaviors:

    - Forwards CLI flags to the Kotlin-based Hadolint CLI.
    - Performs optional *strict* pre-validation of Dockerfile paths in PowerShell for faster feedback.
    - Uses Gradle’s `--args=` forwarding mechanism, which requires careful quoting.
    - Preserves a clean stdout stream from the Kotlin CLI (JSON), while Gradle and other diagnostics may appear depending on configuration.

    The Kotlin CLI is designed to emit machine-readable JSON, which makes this wrapper useful in automation:

        $result = ./Invoke-HadolintCli.ps1 | ConvertFrom-Json
        if ($result.exitCode -ne 0) { exit $result.exitCode }

.PARAMETER FailureThreshold
    Hadolint failure threshold used to determine when the linter should exit non-zero.

    Valid values: error, warning, info, style, ignore.

.PARAMETER Dockerfiles
    One or more Dockerfile paths to lint.

    Defaults to `Dockerfile`. Paths are forwarded as provided; resolution to absolute paths happens in the Kotlin CLI.
    When -StrictFiles is enabled, this script validates file existence before invoking Gradle.

.PARAMETER StrictFiles
    If set, missing Dockerfiles cause the script to fail immediately.

    This mirrors the Kotlin CLI’s `--strict-files`, but performs a pre-check in PowerShell so failures are:
    - Faster (no Gradle startup),
    - More actionable (shows the resolved candidate path).

.PARAMETER PassthroughArgs
    Additional arguments forwarded verbatim to the Kotlin CLI.

    You may optionally include `--` as a separator to improve UX:

        ./Invoke-HadolintCli.ps1 -- --some-extra-flag

.NOTES
- Requires PowerShell 7+.
- Exits with the underlying Gradle process exit code ($LASTEXITCODE).
#>

[CmdletBinding()]
param(
    [ValidateSet('error', 'warning', 'info', 'style', 'ignore')]
    [string] $FailureThreshold = 'warning',

    [Alias('Dockerfile')]
    [string[]] $Dockerfiles = @('Dockerfile'),

    [switch] $StrictFiles,

    [Parameter(ValueFromRemainingArguments)]
    [string[]] $PassthroughArgs
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'

# Import helper that discovers the repository root from the current script directory. Keeping this
# as a separate function avoids duplicating "repo root detection" logic across scripts.
. (Join-Path $PSScriptRoot '..' 'lib' 'Get-KalmRepoRoot.ps1')

function Join-QuotedArgs {
    <#
    .SYNOPSIS
        Converts an argument list into a single string suitable for Gradle's `--args=` forwarding.

    .DESCRIPTION
        Gradle expects a single string for `--args=`. That means we must reconstruct the CLI arguments as a single value, including quoting.

        ## Strategy:

        - Quote *every* argument to ensure stable parsing regardless of spaces or special characters.
        - Escape backslashes and quotes to prevent accidental unquoting or truncation.

        This reduces cross-platform variance (Windows/Linux shells) and avoids subtle parsing bugs.

    .PARAMETER Arguments
        Argument tokens to quote and join.

    .OUTPUTS
        System.String. A single command-line string.
    #>
    [OutputType([string])]
    param(
        [Parameter(Mandatory)]
        [string[]] $Arguments
    )

    $escaped = foreach ($argument in $Arguments) {
        # Quote all args for stable parsing by Gradle's --args forwarding.
        # - Escape backslashes first so they are not consumed during later parsing.
        # - Escape double quotes so the argument stays intact.
        '"' + ($argument -replace '\\', '\\\\' -replace '"', '\"') + '"'
    }

    return ($escaped -join ' ')
}

# Resolve the repository root so the wrapper can be executed from any working directory.
$repoRoot = Get-KalmRepoRoot -StartPath $PSScriptRoot

# Select the correct Gradle wrapper depending on platform.
# - Windows uses gradlew.bat
# - Unix-like environments use gradlew
$gradlew = if ($IsWindows) {
    Join-Path $repoRoot 'gradlew.bat'
}
else {
    Join-Path $repoRoot 'gradlew'
}

# Fail fast with a clear message if the wrapper does not exist.
if (-not (Test-Path -LiteralPath $gradlew)) {
    throw "Gradle wrapper not found at '$gradlew'. Run this script from the repository checkout."
}

# Construct the Kotlin CLI args. We build this as a list of tokens, then serialize it into a single
# string for --args=.
$cliArgs = @('--failure-threshold', $FailureThreshold)

foreach ($path in $Dockerfiles) {
    $cliArgs += @('--dockerfile', $path)
}

if ($StrictFiles) {
    # Pre-validate Dockerfile existence to avoid paying Gradle startup cost when files are missing.
    # We resolve relative paths against the repository root because we will execute Gradle from
    # $repoRoot.
    foreach ($path in $Dockerfiles) {
        $candidate = if ([System.IO.Path]::IsPathRooted($path)) {
            $path
        }
        else {
            Join-Path $repoRoot $path
        }

        if (-not (Test-Path -LiteralPath $candidate)) {
            throw "Dockerfile not found with -StrictFiles: '$path' (resolved to '$candidate')."
        }
    }

    # Forward strict behavior to the Kotlin CLI as well (keeps behaviors consistent).
    $cliArgs += '--strict-files'
}

if ($PassthroughArgs) {
    # Support an optional `--` separator (common in CLI tooling) to improve UX.
    # Example:
    #   ./Invoke-HadolintCli.ps1 -- --some-flag
    $extra = $PassthroughArgs
    if ($extra[0] -eq '--') {
        $extra = $extra | Select-Object -Skip 1
    }

    $cliArgs += $extra
}

# Gradle forwards args as a single string value, so join and quote the tokens.
$argsString = Join-QuotedArgs -Arguments $cliArgs

# Invoke the dedicated Gradle task that runs the Kotlin Hadolint CLI. The Kotlin CLI is expected to
# print JSON to stdout for downstream processing.
$gradleArgs = @(':tools:runHadolintCli', "--args=$argsString")

Write-Host ("Running: ./gradlew {0}" -f ($gradleArgs -join ' '))

# Ensure Gradle runs from the repository root for consistent resolution of relative paths.
Push-Location $repoRoot
try {
    # Capture stdout so we can parse the Kotlin CLI JSON payload into native PowerShell objects.
    $rawOutput = & $gradlew @gradleArgs
    $exitCode = $LASTEXITCODE

    if (-not $rawOutput) {
        throw "Hadolint CLI did not produce JSON output."
    }

    $jsonText = $rawOutput -join [System.Environment]::NewLine

    try {
        $result = $jsonText | ConvertFrom-Json -ErrorAction Stop
    }
    catch {
        throw "Failed to parse Hadolint JSON output. Raw output:`n$jsonText"
    }

    # Emit parsed JSON result for callers.
    $result

    # For external processes, PowerShell stores the exit code in $LASTEXITCODE. Forward it as this 
    # script's process exit code for CI and automation.
    exit $exitCode
}
finally {
    Pop-Location
}
