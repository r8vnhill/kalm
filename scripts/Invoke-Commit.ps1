using module ./lib/ScriptLogging.psm1
#Requires -Version 7.4
<#!
.SYNOPSIS
Stage and commit changes with conventional commit message formatting.

.DESCRIPTION
Provides a structured way to commit changes following conventional commits and gitmoji standards.
Supports dry-run preview, automatic staging, and detailed commit message formatting.

.PARAMETER Message
Short commit message (will be prefixed with emoji and scope).

.PARAMETER DetailedDescription
Optional detailed description with bullet points (without emojis).

.PARAMETER Emoji
One or more emoji to prefix the commit message (e.g., '📝', '🔧').

.PARAMETER Scope
The scope of the change (e.g., 'scripts', 'wiki', 'docs').

.PARAMETER Files
Optional array of specific files to stage. If not provided, stages all changes (-A).

.PARAMETER WorkingDirectory
Repository path (default: parent of scripts directory).

.PARAMETER SkipStaging
If set, assumes files are already staged and only performs commit.

.EXAMPLE
.\scripts\Invoke-Commit.ps1 -Emoji '🔧' -Scope 'scripts' -Message 'add git staging preview' -DetailedDescription "- Add Show-GitChangesToStage helper`n- Integrate preview into sync scripts"

.EXAMPLE
.\scripts\Invoke-Commit.ps1 -Emoji '📝','📚' -Scope 'docs' -Message 'update wiki submodule' -WhatIf
#>
[CmdletBinding(SupportsShouldProcess=$true)]
param(
    [Parameter(Mandatory)]
    [string] $Message,

    [Parameter()]
    [string] $DetailedDescription,

    [Parameter(Mandatory)]
    [string[]] $Emoji,

    [Parameter(Mandatory)]
    [string] $Scope,

    [Parameter()]
    [string[]] $Files,

    [Parameter()]
    [string] $WorkingDirectory,

    [Parameter()]
    [switch] $SkipStaging
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'
$InformationPreference = 'Continue'

$logger = [KalmScriptLogger]::Start('Invoke-Commit', $null)
$logger.LogInfo(("Starting commit with scope='{0}', emoji='{1}', skipStaging={2}, files='{3}'" -f $Scope, ($Emoji -join ', '), $SkipStaging.IsPresent, (if ($Files) { $Files -join ', ' } else { '<all>' })),'Startup')

try {
    # Import Git helpers
    Import-Module -Force (Join-Path $PSScriptRoot 'GitSync.psm1')
    if ($PSBoundParameters.ContainsKey('WhatIf')) { Set-KalmDryRun $true }

    if ([string]::IsNullOrWhiteSpace($WorkingDirectory)) {
        $WorkingDirectory = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
    }

    # Build commit message
    $emojiPrefix = $Emoji -join ' '
    $shortMsg = "${emojiPrefix} ${Scope}: ${Message}"

    $fullMessage = $shortMsg
    if (-not [string]::IsNullOrWhiteSpace($DetailedDescription)) {
        $fullMessage = "${shortMsg}`n`n${DetailedDescription}"
    }

    Write-Information "Commit message to use:"
    Write-Information "  $shortMsg"
    if ($DetailedDescription) {
        Write-Information ""
        Write-Information "Detailed description:"
        $DetailedDescription -split "`n" | ForEach-Object { Write-Information "  $_" }
    }
    Write-Information ""
    $logger.LogDebug("Commit preview ready for message: $shortMsg",'Commit')

    # Stage files if not skipped
    if (-not $SkipStaging) {
        # Preview what would be staged
        Show-GitChangesToStage -Path $WorkingDirectory

        if ($PSCmdlet.ShouldProcess($WorkingDirectory, 'Stage changes')) {
            if ($Files -and $Files.Count -gt 0) {
                $logger.LogInfo(("Staging specific files: {0}" -f ($Files -join ', ')),'Staging')
                $addArgs = @('add') + $Files
                Invoke-Git -GitArgs $addArgs -WorkingDirectory $WorkingDirectory -Description 'Staging specified files...'
            } else {
                $logger.LogInfo('Staging all repository changes.','Staging')
                Invoke-Git -GitArgs @('add', '-A') -WorkingDirectory $WorkingDirectory -Description 'Staging all changes...'
            }
        }
    }
    else {
        $logger.LogInfo('SkipStaging flag set; assuming user staged files manually.','Staging')
    }

    # Check if there are staged changes
    if ($PSCmdlet.ShouldProcess($WorkingDirectory, 'Commit staged changes')) {
        $statusOut = Invoke-Git -GitArgs @('diff', '--cached', '--quiet') -WorkingDirectory $WorkingDirectory -NoThrow -ReturnExitCode -Quiet

        if ($statusOut -eq 0) {
            Write-Information "No staged changes to commit."
            $logger.LogWarning('Commit aborted because no staged changes were detected.','Validation')
            return
        }

        Invoke-Git -GitArgs @('commit', '-m', $fullMessage) -WorkingDirectory $WorkingDirectory -Description 'Committing changes...'

        Write-Information ""
        Write-Information "Commit successful!"
        $logger.LogInfo('Commit completed successfully.','Summary')
    }
}
catch {
    $logger.LogError(("Invoke-Commit failed: {0}" -f $_.Exception.Message),'Failure')
    throw
}
