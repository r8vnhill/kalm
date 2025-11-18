<#
Pester tests for the discovery helper Get-PesterTestFiles.

These specs cover:
- literal include patterns resolved relative to an explicit or implicit base directory
- duplicate/ordering normalization via data-driven cases
- recursive discovery for patterns containing '**'
- error handling when BaseDirectory validation fails
#>

using module '..\..\lib\ScriptLogging.psm1'
using module '..\helpers\Discover-PesterTestFiles.psm1'

function Normalize-Paths {
    param([string[]] $Paths)
    if (-not $Paths) { return @() }
    $Paths |
        Where-Object { $_ } |
        ForEach-Object {
            ($_ -replace '\\', '/') -replace '/+', '/'
        }
}

Describe 'Get-PesterTestFiles' {
    $sortAndDedupCases = @(
        @{
            Name          = 'dedup duplicates and sort relative patterns'
            IncludePatterns = @('Foo.Tests.ps1', 'Foo.Tests.ps1', 'sub/Bar.Tests.ps1')
            ExpectedNames = @('Foo.Tests.ps1', 'sub/Bar.Tests.ps1')
        },
        @{
            Name          = 'sorts lexicographically regardless of input order'
            IncludePatterns = @('sub/deep/Deep.Tests.ps1', 'sub/Bar.Tests.ps1')
            ExpectedNames = @('sub/Bar.Tests.ps1', 'sub/deep/Deep.Tests.ps1')
        }
    )

    BeforeAll {
        $script:TempRoot = Join-Path -Path $TestDrive -ChildPath ([guid]::NewGuid().ToString())
        New-Item -ItemType Directory -Path $script:TempRoot -Force | Out-Null

        $createTestFile = {
            param($relativePath)
            $fullPath = Join-Path -Path $script:TempRoot -ChildPath $relativePath
            $dir = Split-Path -Path $fullPath -Parent
            if (-not (Test-Path -LiteralPath $dir)) {
                New-Item -ItemType Directory -Path $dir -Force | Out-Null
            }
            New-Item -ItemType File -Path $fullPath -Force | Out-Null
            return $fullPath
        }

        $script:FooPath = & $createTestFile 'Foo.Tests.ps1'
        $script:BarPath = & $createTestFile 'sub/Bar.Tests.ps1'
        $script:DeepPath = & $createTestFile 'sub/deep/Deep.Tests.ps1'

        $logDir = Join-Path -Path $script:TempRoot -ChildPath 'logs'
        $script:Logger = [KalmScriptLogger]::Start('discover-pester-testfiles', $logDir)
    }

    AfterAll {
        [KalmScriptLogger]::Reset()
        if (Test-Path -LiteralPath $script:TempRoot) {
            Remove-Item -LiteralPath $script:TempRoot -Recurse -Force
        }
    }

    It 'resolves literal patterns relative to the provided base directory' {
        $files = Get-PesterTestFiles -IncludePatterns 'Foo.Tests.ps1' -BaseDirectory $script:TempRoot -Logger $script:Logger
        $normalizedResult = Normalize-Paths $files
        $normalizedExpected = Normalize-Paths @($script:FooPath)
        $normalizedResult | Should -Be $normalizedExpected
    }

    It 'falls back to the current location when BaseDirectory is omitted' {
        Push-Location $script:TempRoot
        try {
            $files = Get-PesterTestFiles -IncludePatterns 'Foo.Tests.ps1' -Logger $script:Logger
            $normalizedResult = Normalize-Paths $files
            $normalizedExpected = Normalize-Paths @($script:FooPath)
            $normalizedResult | Should -Be $normalizedExpected
        }
        finally {
            Pop-Location
        }
    }

    # Data-driven coverage ensures duplicates are removed and ordering stays deterministic.
    It -TestCases $sortAndDedupCases 'dedups and sorts literal include patterns' {
        param($IncludePatterns, $ExpectedNames)
        $result = Get-PesterTestFiles -IncludePatterns $IncludePatterns -BaseDirectory $script:TempRoot -Logger $script:Logger
        $expected = $ExpectedNames |
            ForEach-Object { Join-Path -Path $script:TempRoot -ChildPath $_ } |
            Sort-Object -Unique
        $normalizedResult = Normalize-Paths $result
        $normalizedExpected = Normalize-Paths $expected
        $normalizedResult | Should -Be $normalizedExpected
    }

    It 'supports recursive "**" include patterns' {
        $files = Get-PesterTestFiles -IncludePatterns '**/*.Tests.ps1' -BaseDirectory $script:TempRoot -Logger $script:Logger
        $expected = @($script:BarPath, $script:DeepPath, $script:FooPath) | Sort-Object
        $normalizedResult = Normalize-Paths $files
        $normalizedExpected = Normalize-Paths $expected
        $normalizedResult | Should -Be $normalizedExpected
    }

    It 'ignores include patterns with no matches' {
        $files = Get-PesterTestFiles -IncludePatterns @('Foo.Tests.ps1', 'Missing.Tests.ps1') -BaseDirectory $script:TempRoot -Logger $script:Logger
        $normalizedResult = Normalize-Paths $files
        $normalizedExpected = Normalize-Paths @($script:FooPath)
        $normalizedResult | Should -Be $normalizedExpected
    }

    It 'validates that BaseDirectory exists' {
        $missing = Join-Path -Path $script:TempRoot -ChildPath 'does-not-exist'
        { Get-PesterTestFiles -IncludePatterns 'Foo.Tests.ps1' -BaseDirectory $missing -Logger $script:Logger } |
            Should -Throw
    }
}
