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
      the longest literal prefix directory and matches using custom glob semantics against the
      full absolute path.
    - Deterministic ordering is enforced via Sort-Object -Unique.

.OUTPUTS
    [string[]]
#>

function Convert-PesterGlobToRegex {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string] $Pattern
    )

    $normalized = ($Pattern -replace '\\', '/')
    $builder = [System.Text.StringBuilder]::new()
    $builder.Append('^') | Out-Null

    for ($i = 0; $i -lt $normalized.Length;) {
        $char = $normalized[$i]

        if ($char -eq '*') {
            if (($i + 1) -lt $normalized.Length -and $normalized[$i + 1] -eq '*') {
                # Treat '**' as "zero or more directories". When followed by '/' allow the slash to be optional
                # so '**/Foo.ps1' still matches 'Foo.ps1' at the root.
                $i += 2
                if ($i -lt $normalized.Length -and $normalized[$i] -eq '/') {
                    $builder.Append('(?:.*/)?') | Out-Null
                    $i++
                }
                else {
                    $builder.Append('.*') | Out-Null
                }
                continue
            }

            $builder.Append('[^/]*') | Out-Null
            $i++
            continue
        }
        elseif ($char -eq '?') {
            $builder.Append('[^/]') | Out-Null
            $i++
            continue
        }

        $builder.Append([System.Text.RegularExpressions.Regex]::Escape($char)) | Out-Null
        $i++
    }

    $builder.Append('$') | Out-Null
    return $builder.ToString()
}

function Get-PesterTestFiles {
    [CmdletBinding()]
    [OutputType([string[]])]
    param(
        [ValidateNotNullOrEmpty()]
        [Parameter(Mandatory, Position = 0)]
        [string[]] $IncludePatterns,

        [ValidateScript({
                if (-not $_) { return $true }
                if (Test-Path -LiteralPath $_ -PathType Container) { return $true }
                throw "BaseDirectory '$_' does not exist or is not a directory."
            })]
        [string] $BaseDirectory,

        [ValidateNotNull()]
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
        if ($absPattern -like '*`**`*') {
            # Fallback discovery for patterns with '**'
            $wildIdx = $absPattern.IndexOf('*')
            if ($wildIdx -lt 0) { $wildIdx = $absPattern.Length }
            $literalPrefix = $absPattern.Substring(0, $wildIdx)
            $separatorIndex = [Math]::Max($literalPrefix.LastIndexOf('/'), $literalPrefix.LastIndexOf('\'))

            # TODO: Confirm BaseDirectory fallback behavior for rooted absolute patterns; currently we
            #       prefer the caller-supplied base even when the pattern is already absolute.
            $rootDir = $BaseDirectory
            if ($separatorIndex -gt 0) {
                $candidate = $literalPrefix.Substring(0, $separatorIndex)
                if (-not [string]::IsNullOrWhiteSpace($candidate)) {
                    $rootDir = $candidate
                }
            }

            try {
                $rootDir = (Resolve-Path -Path $rootDir -ErrorAction Stop).ProviderPath
            }
            catch {
                $rootDir = $null
            }

            if ($rootDir) {
                # TODO: Convert-PesterGlobToRegex currently treats '**' by expanding enumeration roots.
                #       Revisit once we have a clearer set of expected Pester glob behaviors.
                $regexPattern = Convert-PesterGlobToRegex -Pattern $absPattern
                $gciSplat = @{
                    LiteralPath = $rootDir
                    Recurse     = $true
                    File        = $true
                    ErrorAction = 'SilentlyContinue'
                }

                $patternMatches = Get-ChildItem @gciSplat |
                    ForEach-Object { $_.FullName } |
                    Where-Object {
                        ($_ -replace '\\', '/') -match $regexPattern
                    }
            }
        }
        else {
            # TODO: Extraction: the literal Resolve-Path branch could reuse a helper shared with the
            #       '**' path to centralize normalization.
            $patternMatches = try {
                Resolve-Path -Path $absPatternNorm -ErrorAction Stop |
                    ForEach-Object { $_.ProviderPath }
            }
            catch {
                @()
            }
        }

        if (-not $patternMatches) {
            $patternMatches = @()
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
