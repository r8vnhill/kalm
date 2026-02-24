#Requires -Version 7.4
<#
.SYNOPSIS
    Module entrypoint for the Locks CLI PowerShell integration.

.DESCRIPTION
    This module wires together the public Invoke-LocksCli command and its supporting implementation by:

    1) Defining the expected file layout for required helper scripts and submodules.
    2) Validating that all required files exist (fail fast with actionable errors).
    3) Dot-sourcing helper scripts and importing helper modules so functions and types are available.
    4) Verifying that key public and internal commands are present in the session.
    5) Exporting Invoke-LocksCli as the only public module member.

    The goal is to keep Invoke-LocksCli.psm1 minimal and declarative: it acts as a module "composition root", while the
    real implementation lives in:
    - locks-cli/Types.psm1
    - locks-cli/Helpers.ps1
    - locks-cli/Public.Invoke-LocksCli.ps1
    plus shared repository scripts under ../lib.

    This structure improves modularity, testability, and maintainability:
    - Each file has a single responsibility (types, helpers, or public API).
    - The entrypoint validates dependencies early (useful in CI).
    - Consumers import a single module and get a single exported command.
#>

Set-StrictMode -Version 3.0

$repoRootScript = Join-Path $PSScriptRoot '..' 'lib' 'Get-KalmRepoRoot.ps1'
$quoteArgsScript = Join-Path $PSScriptRoot '..' 'lib' 'Join-QuotedArgs.ps1'
$findJsonObjectLineScript = Join-Path $PSScriptRoot '..' 'lib' 'Find-JsonObjectLine.ps1'
$findLocksJsonCommandScript = Join-Path $PSScriptRoot '..' 'lib' 'Find-LocksCliJsonCommand.ps1'
$resolveExecutionCommandModule = Join-Path $PSScriptRoot '..' 'lib' 'Resolve-LocksCliExecutionCommand.psm1'

$locksCliTypesModule = Join-Path $PSScriptRoot 'locks-cli' 'Types.psm1'
$locksCliHelpersScript = Join-Path $PSScriptRoot 'locks-cli' 'Helpers.ps1'
$locksCliPublicScript = Join-Path $PSScriptRoot 'locks-cli' 'Public.Invoke-LocksCli.ps1'

<## Validates that all required module components exist before loading. ##>
foreach ($scriptPath in @(
        $repoRootScript,
        $quoteArgsScript,
        $findJsonObjectLineScript,
        $findLocksJsonCommandScript,
        $resolveExecutionCommandModule,
        $locksCliTypesModule,
        $locksCliHelpersScript,
        $locksCliPublicScript
    )) {
    if (-not (Test-Path -LiteralPath $scriptPath)) {
        throw "Required helper script not found: '$scriptPath'"
    }
}

<## Loads repository-wide helper scripts. ##>
. $repoRootScript
. $quoteArgsScript
. $findJsonObjectLineScript
. $findLocksJsonCommandScript

<## Imports helper modules required by the Locks CLI integration. ##>
Import-Module $resolveExecutionCommandModule -Force
Import-Module $locksCliTypesModule -Force

<## Loads module-local helper and public API scripts. ##>
. $locksCliHelpersScript
. $locksCliPublicScript

<## Verifies that required commands are available after module composition. ##>
foreach ($requiredFunction in @(
        'Get-KalmRepoRoot',
        'Join-QuotedArgs',
        'Find-JsonObjectLine',
        'Find-LocksCliJsonCommand',
        'ConvertTo-LocksCliExecutionSpec',
        'Invoke-LocksCli'
    )) {
    Get-Command -Name $requiredFunction -ErrorAction Stop | Out-Null
}

<## Public API Export ##>
Export-ModuleMember -Function Invoke-LocksCli
