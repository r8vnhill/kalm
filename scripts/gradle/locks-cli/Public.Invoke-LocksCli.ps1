#Requires -Version 7.4
using module .\Types.psm1
Set-StrictMode -Version 3.0

<#
.SYNOPSIS
    Public, pipeline-aware entrypoint for executing the dependency-lock workflow CLI.

.DESCRIPTION
    Resolves invocation context once, then executes in batch mode (default) or per pipeline item (-PerItem).
    Always emits a LocksCliInvocationResult object.

.PARAMETER CliArgs
    CLI arguments passed to the locks workflow.
    Accepts remaining args, pipeline-by-value, and pipeline-by-property-name.

.PARAMETER RepoRoot
    Optional repository root override.

.PARAMETER GradlewPath
    Optional Gradle wrapper override.

.PARAMETER PassThru
    Emits execution spec instead of executing final command.

.PARAMETER PerItem
    Executes once per pipeline record instead of one batch run.

.PARAMETER CommandRunner
    Scriptblock used to execute resolved command.

.OUTPUTS
    LocksCliInvocationResult
#>
function Invoke-LocksCli {
    [Diagnostics.CodeAnalysis.SuppressMessageAttribute(
        'PSShouldProcess',
        '',
        Justification = 'ShouldProcess is evaluated in Invoke-LocksCliInternal to keep discovery behavior unchanged.'
    )]
    [CmdletBinding(SupportsShouldProcess)]
    [OutputType([LocksCliInvocationResult])]
    param(
        [Parameter(
            Mandatory,
            Position = 0,
            ValueFromRemainingArguments,
            ValueFromPipeline,
            ValueFromPipelineByPropertyName
        )]
        [ValidateNotNull()]
        [Alias('Argument', 'Args', 'CliArg')]
        [string[]] $CliArgs,

        [string] $RepoRoot,
        [string] $GradlewPath,
        [switch] $PassThru,
        [switch] $PerItem,

        [scriptblock] $CommandRunner = {
            param([string] $Executable, [string[]] $Arguments)
            & $Executable @Arguments
            $LASTEXITCODE
        }
    )

    begin {
        $previousErrorActionPreference = $ErrorActionPreference
        $previousInformationPreference = $InformationPreference
        $ErrorActionPreference = 'Stop'
        $InformationPreference = 'Continue'
        $bufferedArgs = [System.Collections.Generic.List[string]]::new()
        $context = New-LocksCliInvocationContext -RepoRoot $RepoRoot -GradlewPath $GradlewPath
    }

    process {
        if ($PerItem) {
            $output = Invoke-LocksCliPerItem -Context $context -CliArgs $CliArgs -PassThru:$PassThru -CommandRunner $CommandRunner
            Write-Output $output
        }
        else {
            Add-LocksCliBufferedArgs -Buffer $bufferedArgs -CliArgs $CliArgs
        }
    }

    end {
        if (-not $PerItem) {
            $batchInvocation = @{
                Context       = $context
                CliArgs       = $bufferedArgs.ToArray()
                PassThru      = $PassThru
                CommandRunner = $CommandRunner
            }
            $output = Invoke-LocksCliBatch @batchInvocation
            Write-Output $output
        }
    }

    clean {
        $ErrorActionPreference = $previousErrorActionPreference
        $InformationPreference = $previousInformationPreference
    }
}
