#Requires -Version 7.4

using module ..\..\lib\ScriptLogging.psm1

<#
.SYNOPSIS
    Helpers to normalize patterns, resolve test files (with ** support), and apply excludes.

.DESCRIPTION
    Provides reusable discovery utilities for the Pester harness.
    - Resolve-PesterTestFiles expands include patterns to absolute file paths (ProviderPath)
    - Filter-PesterTestFiles removes matches using wildcard-based excludes

    Notes:
    - Resolve-Path does not understand '**'; when '**' is present, this module enumerates from
      the longest literal prefix directory and matches using -like against the full absolute path.
    - Deterministic ordering is enforced via Sort-Object -Unique.

.OUTPUTS
    [string[]]
#>

function Get-PesterTestFiles {
    [CmdletBinding()]
    [OutputType([string[]])]
    param(
        # TODO: Parameter validation
        [Parameter(Mandatory, Position = 0)]
        [string[]] $IncludePatterns,

        [string] $BaseDirectory,

        [Parameter(Mandatory)]
        [KalmScriptLogger] $Logger
    )

    if (-not $BaseDirectory) { $BaseDirectory = (Get-Location).ProviderPath }

    $all = [System.Collections.Generic.List[string]]::new()

    foreach ($pattern in $IncludePatterns) {
        if (-not $pattern) { continue }

        $isRooted = [System.IO.Path]::IsPathRooted($pattern)
        $absPattern = if ($isRooted) {
            $pattern
        }
        else {
            Join-Path -Path $BaseDirectory -ChildPath $pattern
        }

        # Normalize separators to platform style for matching
        $absPatternNorm = $absPattern -replace '/', '\\'

        $patternMatches = @()
        if ($absPatternNorm -like '*`**`*') {
            # TODO: Extract this to a function
            # Fallback discovery for patterns with '**'
            # Find first wildcard index to compute an enumeration root
            $wildIdx = $absPatternNorm.IndexOf('*')
            if ($wildIdx -lt 0) { $wildIdx = $absPatternNorm.Length }
            $rootCandidate = $absPatternNorm.Substring(0, $wildIdx)
            $rootDir = if ([System.IO.Directory]::Exists($rootCandidate)) {
                $rootCandidate
            }
            else {
                [System.IO.Path]::GetDirectoryName($rootCandidate)
            }
            if (
                -not [string]::IsNullOrWhiteSpace($rootDir) -and (Test-Path -LiteralPath $rootDir)
            ) {
                $gciSplat = @{
                    LiteralPath = $rootDir
                    Recurse     = $true
                    File        = $true
                    ErrorAction = 'SilentlyContinue'
                }
                # Convert pattern: treat '**' as wildcard crossing directories.
                # -like handles '\' in the candidate.
                # '**' -> '*'
                $likePattern = $absPatternNorm -replace '\\\\', '\\' -replace '\*\*', '*'
                # TODO: Maybe this could be improved? Is ForEach-Object the best option here?
                $patternMatches = Get-ChildItem @gciSplat |
                    ForEach-Object { $_.FullName } |
                    Where-Object {
                        ($_ -replace '/', '\\') -like $likePattern
                    }
            }
        }
        else {
            $patternMatches = try {
                Resolve-Path -Path $absPatternNorm -ErrorAction Stop |
                    ForEach-Object { $_.ProviderPath }
            }
            catch {
                @()
            }
        }

        $Logger.LogDebug("Pattern '$pattern' → ${@($patternMatches).Count} file(s)", 'Discovery')
        $all.AddRange([string[]]$patternMatches)
    }

    $result = $all | Where-Object { $_ } | Sort-Object -Unique
    return , $result
}

function Select-PesterTestFiles {
    [CmdletBinding()]
    [OutputType([string[]])]
    param(
        # TODO: Parameter validation
        [Parameter(Mandatory, Position = 0)]
        [string[]] $Files,

        [string[]] $ExcludePatterns = @(),

        [Parameter(Mandatory)]
        [KalmScriptLogger] $Logger
    )

    # TODO: This function could be easily broken into smaller pieces for modularity/testing.
    if (-not $ExcludePatterns -or @($ExcludePatterns).Count -eq 0) {
        return , ($Files | Sort-Object -Unique)
    }

    # Normalize exclude patterns: if no wildcard provided, wrap with *pattern*
    $normalizedEx = foreach ($p in $ExcludePatterns) {
        if (-not $p) { continue }
        if ($p -like '*[*?]*') { $p } else { "*${p}*" }
    }

    $kept = foreach ($f in $Files) {
        $exclude = $false
        foreach ($p in $normalizedEx) {
            if ($f -like $p) { $exclude = $true; break }
        }
        if ($exclude) {
            if ($Logger) { $Logger.LogDebug(("Excluded '{0}' by '{1}'" -f $f, ($p)), 'Discovery') }
            continue
        }
        $f
    }

    return , ($kept | Sort-Object -Unique)
}

Export-ModuleMember -Function Get-PesterTestFiles, Select-PesterTestFiles
