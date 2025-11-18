#Requires -Version 7.4

using module ..\..\..\lib\ScriptLogging.psm1

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
                # Treat '**' as "zero or more path segments".
                # When followed by '/', emit a pattern that matches zero or more directories
                # so '**/Foo.ps1' matches both 'Foo.ps1' at root and 'sub/Foo.ps1' in subdirs
                $i += 2
                if ($i -lt $normalized.Length -and $normalized[$i] -eq '/') {
                    # Match zero or more complete path segments: (dir/, dir/sub/, or nothing)
                    $builder.Append('(?:(?:[^/]+/)*)') | Out-Null
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

function Get-PesterGlobEnumerationRoot {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string] $Pattern,

        [Parameter(Mandatory)]
        [string] $AbsolutePattern,

        [Parameter(Mandatory)]
        [string] $BaseDirectory,

        [Parameter(Mandatory)]
        [bool] $IsRooted
    )

    $normalizedAbsolute = $AbsolutePattern -replace '\\', '/'
    $normalizedBase = $BaseDirectory -replace '\\', '/'
    $rootDir = if ($IsRooted) {
        [System.IO.Path]::GetPathRoot($AbsolutePattern)
    }
    else {
        $BaseDirectory
    }

    if (-not $rootDir) {
        $rootDir = $BaseDirectory
    }

    $relativePortion = if ($IsRooted) {
        $rootPrefix = ([System.IO.Path]::GetPathRoot($AbsolutePattern) -replace '\\', '/')
        if ($rootPrefix) {
            $normalizedAbsolute.Substring([Math]::Min($normalizedAbsolute.Length, $rootPrefix.Length))
        }
        else {
            $normalizedAbsolute
        }
    }
    else {
        if ($normalizedAbsolute.StartsWith($normalizedBase, [System.StringComparison]::OrdinalIgnoreCase)) {
            $normalizedAbsolute.Substring($normalizedBase.Length)
        }
        else {
            $Pattern -replace '\\', '/'
        }
    }

    if ($relativePortion) {
        $relativePortion = $relativePortion.TrimStart('/')
    }

    if (-not $relativePortion) {
        return $rootDir
    }

    $segments = $relativePortion -split '/'
    foreach ($segment in $segments) {
        if (-not $segment) { continue }
        if ([System.Management.Automation.WildcardPattern]::ContainsWildcardCharacters($segment)) {
            break
        }
        $rootDir = Join-Path -Path $rootDir -ChildPath $segment
    }

    return $rootDir
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
        $absPatternNorm = if ([System.IO.Path]::DirectorySeparatorChar -eq '\') {
            $absPattern -replace '/', '\'
        }
        else {
            $absPattern -replace '\', '/'
        }

        $patternMatches = @()
        $containsRecursiveWildcard = $pattern -match '\*\*'
        if ($containsRecursiveWildcard) {
            # Fallback discovery for patterns with '**'
            $rootDir = Get-PesterGlobEnumerationRoot -Pattern $pattern -AbsolutePattern $absPatternNorm -BaseDirectory $BaseDirectory -IsRooted:$isRooted

            try {
                $rootDir = (Resolve-Path -Path $rootDir -ErrorAction Stop).ProviderPath
            }
            catch {
                $rootDir = $null
            }

            if ($rootDir) {
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
        [ValidateNotNullOrEmpty()]
        [Parameter(Mandatory, Position = 0)]
        [string[]] $Files,

        [string[]] $ExcludePatterns = @(),

        [ValidateNotNull()]
        [Parameter(Mandatory)]
        [KalmScriptLogger] $Logger
    )

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

Export-ModuleMember -Function Get-PesterTestFiles, Select-PesterTestFiles, Convert-PesterGlobToRegex, Get-PesterGlobEnumerationRoot
