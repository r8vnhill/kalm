#Requires -Version 7.5
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

# Import repo root resolver
. (Join-Path $PSScriptRoot '..' 'lib' 'Get-KalmRepoRoot.ps1')

function Join-QuotedArgs {
    [OutputType([string])]
    param(
        [Parameter(Mandatory)]
        [string[]] $Arguments
    )

    $escaped = foreach ($argument in $Arguments) {
        # Quote all args for stable parsing by Gradle's --args forwarding.
        '"' + ($argument -replace '\\', '\\\\' -replace '"', '\"') + '"'
    }
    return ($escaped -join ' ')
}

$repoRoot = Get-KalmRepoRoot -StartPath $PSScriptRoot
$gradlew = if ($IsWindows) {
    Join-Path $repoRoot 'gradlew.bat'
}
else {
    Join-Path $repoRoot 'gradlew'
}

if (-not (Test-Path -LiteralPath $gradlew)) {
    throw "Gradle wrapper not found at '$gradlew'. Run this script from the repository checkout."
}

$cliArgs = @('--failure-threshold', $FailureThreshold)
foreach ($path in $Dockerfiles) {
    $cliArgs += @('--dockerfile', $path)
}
if ($StrictFiles) {
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
    $cliArgs += '--strict-files'
}
if ($PassthroughArgs) {
    $extra = $PassthroughArgs
    if ($extra[0] -eq '--') {
        $extra = $extra | Select-Object -Skip 1
    }
    $cliArgs += $extra
}

$argsString = Join-QuotedArgs -Arguments $cliArgs
$gradleArgs = @(':tools:runHadolintCli', "--args=$argsString")

Write-Host ("Running: ./gradlew {0}" -f ($gradleArgs -join ' '))

Push-Location $repoRoot
try {
    & $gradlew @gradleArgs
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
