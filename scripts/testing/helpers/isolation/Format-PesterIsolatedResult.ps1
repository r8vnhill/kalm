#Requires -Version 7.4
<#
.SYNOPSIS
Prints only the captured Pester output for each test file.

.DESCRIPTION
Consumes the PSCustomObject emitted by the isolated Pester harness, or
objects that expose Output/File properties, and writes the original Pester
log lines to the console without re-printing the metadata block.

.EXAMPLE
Invoke-Pester -Configuration (New-PesterConfiguration -Hashtable (Import-PowerShellDataFile './scripts/testing/pester.config.psd1')) | ./scripts/testing/helpers/Format-PesterIsolatedResult.ps1
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
        Write-Information ("==> {0}" -f $label) -InformationAction Continue
    }

    foreach ($line in $lines) {
        $plain = $line -replace '\x1B\[[0-9;]*m',''
        if ($plain -match '^\s*Containers\s*:') { break }
        Write-Information $line -InformationAction Continue
    }
}

