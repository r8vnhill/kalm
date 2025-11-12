#Requires -Version 7.4
<#
.SYNOPSIS
Prints only the captured Pester output for each test file.

.DESCRIPTION
Consumes the PSCustomObject emitted by Invoke-PesterWithConfig.ps1, or
objects that expose Output/File properties, and writes the original Pester
log lines to the console without re-printing the metadata block.

.EXAMPLE
./scripts/testing/Invoke-PesterWithConfig.ps1 | ./scripts/testing/helpers/Format-PesterIsolatedResult.ps1
#>
[CmdletBinding()]
param(
    [Parameter(ValueFromPipeline, ValueFromPipelineByPropertyName)]
    [psobject]
    $InputObject,

    [Parameter(ValueFromPipelineByPropertyName)]
    [string[]]
    $Output,

    [Parameter(ValueFromPipelineByPropertyName)]
    [string]
    $File
)

process {
    $lines = $null
    $label = $null

    if ($PSBoundParameters.ContainsKey('Output')) {
        $lines = $Output
    }
    elseif ($InputObject -and $InputObject.PSObject.Properties['Output']) {
        $lines = $InputObject.Output
    }

    if (-not $lines) { return }

    if ($PSBoundParameters.ContainsKey('File')) {
        $label = $File
    }
    elseif ($InputObject -and $InputObject.PSObject.Properties['File']) {
        $label = $InputObject.File
    }

    if ($label) {
        Write-Host ""
        Write-Host ("==> {0}" -f $label) -ForegroundColor Cyan
    }

    foreach ($line in $lines) {
        $plain = $line -replace '\x1B\[[0-9;]*m',''
        if ($plain -match '^\s*Containers\s*:') { break }
        Write-Host $line
    }
}
