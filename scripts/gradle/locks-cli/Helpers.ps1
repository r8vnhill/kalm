#Requires -Version 7.4
using module .\Types.psm1
Set-StrictMode -Version 3.0

<#
.SYNOPSIS
    Resolves and validates context for locks invocation.
.OUTPUTS
    Hashtable with RepoRoot and GradlewPath.
#>
function New-LocksCliInvocationContext {
    [CmdletBinding()]
    [OutputType([hashtable])]
    param(
        [string] $RepoRoot,
        [string] $GradlewPath
    )

    $repoRootResolved = if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
        Get-KalmRepoRoot -StartPath $PSScriptRoot
    }
    else {
        $RepoRoot
    }
    Write-Verbose "Resolved repository root: $repoRootResolved"
    $gradlewResolved = Resolve-LocksCliGradleWrapperPath -RepoRoot $repoRootResolved -GradlewPath $GradlewPath
    Write-Verbose "Resolved Gradle wrapper: $gradlewResolved"
    if (-not (Test-Path -LiteralPath $gradlewResolved)) {
        throw "Gradle wrapper not found at '$gradlewResolved'."
    }

    @{
        RepoRoot    = $repoRootResolved
        GradlewPath = $gradlewResolved
    }
}

<#
.SYNOPSIS
    Appends incoming CLI args to the mutable batch buffer.
#>
function Add-LocksCliBufferedArgs {
    [CmdletBinding()]
    [OutputType([void])]
    param(
        [System.Collections.Generic.List[string]] $Buffer,
        [Parameter(Mandatory)]
        [string[]] $CliArgs
    )

    if ($null -eq $Buffer) {
        throw 'Buffer is required.'
    }

    foreach ($arg in $CliArgs) {
        $null = $Buffer.Add($arg)
    }
}

<#
.SYNOPSIS
    Maps internal execution output to LocksCliInvocationResult.
#>
function ConvertTo-LocksCliInvocationResult {
    [CmdletBinding()]
    [OutputType([LocksCliInvocationResult])]
    param(
        [Parameter(Mandatory)]
        [object] $InternalResult,
        [Parameter(Mandatory)]
        [string] $Mode,
        [Parameter(Mandatory)]
        [bool] $Executed
    )

    $exitCode = $null
    $executionSpec = $null
    if ($InternalResult -is [int]) {
        $exitCode = [int] $InternalResult
    }
    else {
        $executionSpec = $InternalResult
    }

    [LocksCliInvocationResult]::new($exitCode, $executionSpec, $Mode, $Executed)
}

<#
.SYNOPSIS
    Executes one per-item invocation and returns structured result.
#>
function Invoke-LocksCliPerItem {
    [CmdletBinding()]
    [OutputType([LocksCliInvocationResult])]
    param(
        [Parameter(Mandatory)]
        [hashtable] $Context,
        [Parameter(Mandatory)]
        [string[]] $CliArgs,
        [switch] $PassThru,
        [Parameter(Mandatory)]
        [scriptblock] $CommandRunner
    )

    $commandLookup = @{
        RepoRoot      = $Context.RepoRoot
        GradlewPath   = $Context.GradlewPath
        CliArgs       = @($CliArgs)
        PassThru      = $PassThru
        CommandRunner = $CommandRunner
    }
    $internalResult = Invoke-LocksCliInternal @commandLookup
    ConvertTo-LocksCliInvocationResult -InternalResult $internalResult -Mode 'PerItem' -Executed:(-not $PassThru)
}

<#
.SYNOPSIS
    Executes one batch invocation and returns structured result.
#>
function Invoke-LocksCliBatch {
    [CmdletBinding()]
    [OutputType([LocksCliInvocationResult])]
    param(
        [Parameter(Mandatory)]
        [hashtable] $Context,
        [Parameter(Mandatory)]
        [string[]] $CliArgs,
        [switch] $PassThru,
        [Parameter(Mandatory)]
        [scriptblock] $CommandRunner
    )

    $commandLookup = @{
        RepoRoot      = $Context.RepoRoot
        GradlewPath   = $Context.GradlewPath
        CliArgs       = $CliArgs
        PassThru      = $PassThru
        CommandRunner = $CommandRunner
    }
    $internalResult = Invoke-LocksCliInternal @commandLookup
    ConvertTo-LocksCliInvocationResult -InternalResult $internalResult -Mode 'Batch' -Executed:(-not $PassThru)
}

<#
.SYNOPSIS
    Resolves platform-appropriate Gradle wrapper path.
#>
function Resolve-LocksCliGradleWrapperPath {
    [CmdletBinding()]
    [OutputType([string])]
    param(
        [Parameter(Mandatory)]
        [string] $RepoRoot,
        [string] $GradlewPath
    )

    if (-not [string]::IsNullOrWhiteSpace($GradlewPath)) {
        $GradlewPath
    }
    elseif ($IsWindows) {
        Join-Path $RepoRoot 'gradlew.bat'
    }
    else {
        Join-Path $RepoRoot 'gradlew'
    }
}

<#
.SYNOPSIS
    Runs :tools:runLocksCli and captures merged output.
#>
function Invoke-LocksCliGradleCommand {
    [CmdletBinding()]
    [OutputType([string[]])]
    param(
        [Parameter(Mandatory)]
        [string] $GradlewPath,
        [Parameter(Mandatory)]
        [string[]] $CliArgs
    )

    $argsString = Join-QuotedArgs -Arguments (@('--json') + $CliArgs)
    $gradleArgs = @(':tools:runLocksCli', "--args=$argsString", '--quiet')
    Write-Verbose "Gradle invocation args: $($gradleArgs -join ' ')"

    $rawOutput = (& $GradlewPath @gradleArgs 2>&1 | ForEach-Object { "$_" })
    $gradleExitCode = $LASTEXITCODE
    if ($gradleExitCode -ne 0) {
        $nl = [Environment]::NewLine
        $tail = ($rawOutput | Select-Object -Last 20) -join $nl
        throw (
            "Failed to run :tools:runLocksCli (exit code {0}). Command: '{1} {2}'. Output tail:{3}{4}" -f @(
                $gradleExitCode, $GradlewPath, ($gradleArgs -join ' '), $nl, $tail
            )
        )
    }

    $rawOutput
}

<#
.SYNOPSIS
    Performs one end-to-end locks invocation.
.DESCRIPTION
    Runs Gradle discovery, parses command, resolves execution spec, and either executes or returns spec.
#>
function Invoke-LocksCliInternal {
    [CmdletBinding(SupportsShouldProcess)]
    param(
        [Parameter(Mandatory)]
        [string] $RepoRoot,
        [Parameter(Mandatory)]
        [string] $GradlewPath,
        [Parameter(Mandatory)]
        [string[]] $CliArgs,
        [switch] $PassThru,
        [Parameter(Mandatory)]
        [scriptblock] $CommandRunner
    )

    Push-Location $RepoRoot
    try {
        $rawOutput = Invoke-LocksCliGradleCommand -GradlewPath $GradlewPath -CliArgs $CliArgs
        $command = Find-LocksCliJsonCommand -Lines $rawOutput
        $executionSpec = ConvertTo-LocksCliExecutionSpec -Command $command
        $executable = if ($executionSpec.Executable -ieq 'gradlew.bat' -or $executionSpec.Executable -ieq './gradlew') {
            $GradlewPath
        }
        else {
            $executionSpec.Executable
        }

        Write-Verbose ('Extracted command: {0}' -f $command)
        Write-Verbose ('Execution executable: {0}' -f $executable)
        Write-Verbose ('Execution arguments: {0}' -f ($executionSpec.Arguments -join ' '))

        if ($PassThru) {
            $executionSpec
        }
        else {
            Write-Information ('Executing: {0} {1}' -f $executable, ($executionSpec.Arguments -join ' '))
            $target = "$executable $($executionSpec.Arguments -join ' ')".Trim()
            if ($PSCmdlet.ShouldProcess($target, 'Execute locks CLI command')) {
                $resultCode = & $CommandRunner $executable $executionSpec.Arguments
                [int] $resultCode
            }
            else {
                0
            }
        }
    }
    finally {
        Pop-Location
    }
}
