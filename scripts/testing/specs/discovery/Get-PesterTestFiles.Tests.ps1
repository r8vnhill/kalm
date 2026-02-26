# Pester coverage for Get-PesterTestFiles helper.
# New-PesterTestFile is kept script-scoped for test setup only.
#Requires -Version 7.4
using module '..\..\..\lib\ScriptLogging.psm1'
using module '..\..\helpers\discovery\Discover-PesterTestFiles.psm1'

Set-StrictMode -Version 3.0

<#
.SYNOPSIS
    Creates one or more test files under a base directory and returns absolute paths.

.DESCRIPTION
    Script-scoped helper for these specs; accepts relative paths from the pipeline, ensures parent
    directories exist, and emits normalized absolute file paths.

.PARAMETER BaseDirectory
    Directory where the files are created.

.PARAMETER RelativePath
    Relative file path(s) to create; accepts pipeline input.

.OUTPUTS
    System.String
#>
function Script:New-PesterTestFile {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory, ValueFromPipelineByPropertyName)]
        [ValidateNotNullOrWhiteSpace()]
        [string] $BaseDirectory,

        [Parameter(Mandatory, ValueFromPipeline, ValueFromPipelineByPropertyName)]
        [ValidateNotNullOrWhiteSpace()]
        [string[]] $RelativePath
    )

    process {
        foreach ($path in $RelativePath) {
            $fullPath = Join-Path -Path $BaseDirectory -ChildPath $path -ErrorAction Stop
            $dir = Split-Path -Path $fullPath -Parent
            if (-not (Test-Path -LiteralPath $dir -PathType Container)) {
                # Ensure parent exists
                New-Item -ItemType Directory -Path $dir -Force -ErrorAction Stop | Out-Null
            }
            # Idempotent create/overwrite
            New-Item -ItemType File -Path $fullPath -Force -ErrorAction Stop | Out-Null
            Write-Output ([System.IO.Path]::GetFullPath($fullPath))
        }
    }
}

function Script:Invoke-InLocation {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [ValidateNotNullOrWhiteSpace()]
        [string] $Path,

        [Parameter(Mandatory)]
        [scriptblock] $ScriptBlock
    )

    Push-Location -LiteralPath $Path
    try {
        & $ScriptBlock
    }
    finally {
        Pop-Location
    }
}

Describe 'Get-PesterTestFiles' {

    $sortAndDedupCases = @(
        @{
            Name            = 'dedup duplicates and sort relative patterns'
            IncludePatterns = @('Foo.Tests.ps1', 'Foo.Tests.ps1', 'sub/Bar.Tests.ps1')
            ExpectedNames   = @('Foo.Tests.ps1', 'sub/Bar.Tests.ps1')
        },
        @{
            Name            = 'sorts lexicographically regardless of input order'
            IncludePatterns = @('sub/deep/Deep.Tests.ps1', 'sub/Bar.Tests.ps1')
            ExpectedNames   = @('sub/Bar.Tests.ps1', 'sub/deep/Deep.Tests.ps1')
        }
    )

    BeforeAll {
        $pathNormalizationScript = Join-Path $PSScriptRoot '..'
        $pathNormalizationScript = (
            Join-Path $pathNormalizationScript 'support' 'PathNormalization.ps1')
        . $pathNormalizationScript

        $script:TempRoot = Join-Path $TestDrive ([guid]::NewGuid().ToString())
        New-Item -ItemType Directory -Path $script:TempRoot -Force | Out-Null
        $script:FooPath, $script:BarPath, $script:DeepPath =
        @('Foo.Tests.ps1', 'sub/Bar.Tests.ps1', 'sub/deep/Deep.Tests.ps1') |
            New-PesterTestFile -BaseDirectory $script:TempRoot

        $logDir = Join-Path $script:TempRoot 'logs'
        $script:Logger = [KalmScriptLogger]::Start('discover-pester-testfiles', $logDir)
    }

    AfterAll {
        [KalmScriptLogger]::Reset()
        if (Test-Path -LiteralPath $script:TempRoot) {
            Remove-Item -LiteralPath $script:TempRoot -Recurse -Force
        }
    }

    It 'resolves literal patterns relative to the provided base directory' {
        $params = @{
            IncludePatterns = 'Foo.Tests.ps1'
            BaseDirectory   = $script:TempRoot
            Logger          = $script:Logger
        }
        $files = Get-PesterTestFiles @params
        ConvertTo-NormalizedPath $files | Should -Be (ConvertTo-NormalizedPath @($script:FooPath))
    }

    It 'falls back to the current location when BaseDirectory is omitted' {
        $files = Invoke-InLocation -Path $script:TempRoot -ScriptBlock {
            Get-PesterTestFiles -IncludePatterns 'Foo.Tests.ps1' -Logger $script:Logger
        }
        ConvertTo-NormalizedPath $files | Should -Be (ConvertTo-NormalizedPath @($script:FooPath))
    }

    # Data-driven coverage ensures duplicates are removed and ordering stays deterministic.
    It -TestCases $sortAndDedupCases 'dedups and sorts literal include patterns' {
        param($IncludePatterns, $ExpectedNames)
        $result = Get-PesterTestFiles -IncludePatterns $IncludePatterns -BaseDirectory $script:TempRoot -Logger $script:Logger
        $expected = $ExpectedNames |
            ForEach-Object { Join-Path -Path $script:TempRoot -ChildPath $_ } |
            Sort-Object -Unique
        $normalizedResult = ConvertTo-NormalizedPath $result
        $normalizedExpected = ConvertTo-NormalizedPath $expected
        $normalizedResult | Should -Be $normalizedExpected
    }

    It 'supports recursive "**" include patterns' {
        $files = Get-PesterTestFiles -IncludePatterns '**/*.Tests.ps1' -BaseDirectory $script:TempRoot -Logger $script:Logger
        # Should discover every .Tests.ps1 file regardless of depth
        @($files).Count | Should -Be 3
        $fileNames = $files | ForEach-Object { Split-Path -Leaf $_ }
        $fileNames | Should -Contain 'Foo.Tests.ps1'
        $fileNames | Should -Contain 'Bar.Tests.ps1'
        $fileNames | Should -Contain 'Deep.Tests.ps1'
    }

    It 'ignores include patterns with no matches' {
        $files = Get-PesterTestFiles -IncludePatterns @('Foo.Tests.ps1', 'Missing.Tests.ps1') -BaseDirectory $script:TempRoot -Logger $script:Logger
        $normalizedResult = ConvertTo-NormalizedPath $files
        $normalizedExpected = ConvertTo-NormalizedPath @($script:FooPath)
        $normalizedResult | Should -Be $normalizedExpected
    }

    It 'validates that BaseDirectory exists' {
        $missing = Join-Path -Path $script:TempRoot -ChildPath 'does-not-exist'
        { Get-PesterTestFiles -IncludePatterns 'Foo.Tests.ps1' -BaseDirectory $missing -Logger $script:Logger } |
            Should -Throw
    }

    It 'throws when IncludePatterns is empty array' {
        { Get-PesterTestFiles -IncludePatterns @() -BaseDirectory $script:TempRoot -Logger $script:Logger } |
            Should -Throw
    }

    It 'handles rooted absolute paths' {
        $files = Get-PesterTestFiles -IncludePatterns @($script:FooPath) -BaseDirectory $script:TempRoot -Logger $script:Logger
        $normalizedResult = ConvertTo-NormalizedPath $files
        $normalizedExpected = ConvertTo-NormalizedPath @($script:FooPath)
        $normalizedResult | Should -Be $normalizedExpected
    }
}
