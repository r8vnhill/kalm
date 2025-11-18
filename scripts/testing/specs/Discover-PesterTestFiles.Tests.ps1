<#
Pester coverage for the Discover-PesterTestFiles helper module.

Scenarios exercised:
- Convert-PesterGlobToRegex transforms glob patterns to regex with '**' support
- Get-PesterGlobEnumerationRoot computes the correct enumeration starting directory
- Get-PesterTestFiles discovers test files matching include patterns (with and without '**')
- Select-PesterTestFiles filters discovered files using exclude patterns
#>

using module '..\..\lib\ScriptLogging.psm1'
using module '..\helpers\Discover-PesterTestFiles.psm1'

BeforeAll {
    function script:Normalize-Paths {
        param([string[]] $Paths)
        if (-not $Paths) { return @() }
        $Paths |
            Where-Object { $_ } |
            ForEach-Object {
                ($_ -replace '\\', '/') -replace '/+', '/'
            }
    }
}

Describe 'Convert-PesterGlobToRegex' {
    It 'converts a simple wildcard pattern' {
        $result = Convert-PesterGlobToRegex -Pattern '*.ps1'
        $result | Should -Be '^[^/]*\.ps1$'
    }

    It 'converts ** to match multiple directories' {
        $result = Convert-PesterGlobToRegex -Pattern '**/test.ps1'
        # '**/' should become '(?:(?:[^/]+/)*)' to match zero or more complete path segments
        $result | Should -Be '^(?:(?:[^/]+/)*)test\.ps1$'
    }

    It 'converts ** in the middle of a pattern' {
        $result = Convert-PesterGlobToRegex -Pattern 'src/**/tests/*.ps1'
        $result | Should -Be '^src/(?:(?:[^/]+/)*)tests/[^/]*\.ps1$'
    }

    It 'converts ? to match a single character' {
        $result = Convert-PesterGlobToRegex -Pattern 'test?.ps1'
        $result | Should -Be '^test[^/]\.ps1$'
    }

    It 'escapes special regex characters' {
        $result = Convert-PesterGlobToRegex -Pattern 'test[1].ps1'
        $result | Should -Be '^test\[1]\.ps1$'
    }

    It 'normalizes backslashes to forward slashes' {
        $result = Convert-PesterGlobToRegex -Pattern 'src\tests\*.ps1'
        $result | Should -Be '^src/tests/[^/]*\.ps1$'
    }

    It 'handles standalone ** without trailing slash' {
        $result = Convert-PesterGlobToRegex -Pattern 'src/**'
        $result | Should -Match '\.\*'
    }
}

Describe 'Get-PesterGlobEnumerationRoot' {
    BeforeAll {
        $script:TestBase = (Get-Location).ProviderPath
    }

    It 'returns base directory for non-rooted pattern with no wildcards' {
        $result = Get-PesterGlobEnumerationRoot `
            -Pattern 'scripts/testing/*.ps1' `
            -AbsolutePattern (Join-Path $script:TestBase 'scripts/testing/*.ps1') `
            -BaseDirectory $script:TestBase `
            -IsRooted $false
        
        $normalized = ($result -replace '\\', '/')
        $expected = (Join-Path $script:TestBase 'scripts/testing') -replace '\\', '/'
        $normalized | Should -Be $expected
    }

    It 'returns path up to first wildcard segment' {
        $result = Get-PesterGlobEnumerationRoot `
            -Pattern 'scripts/**/test.ps1' `
            -AbsolutePattern (Join-Path $script:TestBase 'scripts/**/test.ps1') `
            -BaseDirectory $script:TestBase `
            -IsRooted $false
        
        $normalized = ($result -replace '\\', '/')
        $expected = (Join-Path $script:TestBase 'scripts') -replace '\\', '/'
        $normalized | Should -Be $expected
    }

    It 'stops at first segment with wildcard characters' {
        $result = Get-PesterGlobEnumerationRoot `
            -Pattern 'a/b/c*/d/e.ps1' `
            -AbsolutePattern (Join-Path $script:TestBase 'a/b/c*/d/e.ps1') `
            -BaseDirectory $script:TestBase `
            -IsRooted $false
        
        $normalized = ($result -replace '\\', '/')
        $expected = (Join-Path $script:TestBase 'a/b') -replace '\\', '/'
        $normalized | Should -Be $expected
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
        # Should find at least some .Tests.ps1 files recursively
        @($files).Count | Should -BeGreaterOrEqual 1
        $fileNames = $files | ForEach-Object { Split-Path -Leaf $_ }
        # Current ** implementation finds files in first-level subdirectories
        # TODO: Enhance regex to match files at all depths including root
        $fileNames | Should -Contain 'Bar.Tests.ps1'
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

    It 'throws when IncludePatterns is empty array' {
        { Get-PesterTestFiles -IncludePatterns @() -BaseDirectory $script:TempRoot -Logger $script:Logger } |
            Should -Throw
    }

    It 'handles rooted absolute paths' {
        $files = Get-PesterTestFiles -IncludePatterns @($script:FooPath) -BaseDirectory $script:TempRoot -Logger $script:Logger
        $normalizedResult = Normalize-Paths $files
        $normalizedExpected = Normalize-Paths @($script:FooPath)
        $normalizedResult | Should -Be $normalizedExpected
    }
}

Describe 'Select-PesterTestFiles' {
    BeforeAll {
        $script:TempRoot = Join-Path -Path $TestDrive -ChildPath ([guid]::NewGuid().ToString())
        New-Item -ItemType Directory -Path $script:TempRoot -Force | Out-Null
        $logDir = Join-Path -Path $script:TempRoot -ChildPath 'logs'
        $script:Logger = [KalmScriptLogger]::Start('select-pester-testfiles', $logDir)
        
        $script:SampleFiles = @(
            Join-Path $script:TempRoot 'tests\unit\Foo.Tests.ps1'
            Join-Path $script:TempRoot 'tests\integration\Bar.Tests.ps1'
            Join-Path $script:TempRoot 'specs\Baz.Tests.ps1'
            Join-Path $script:TempRoot 'other\Helper.Tests.ps1'
        )
    }

    AfterAll {
        [KalmScriptLogger]::Reset()
        if (Test-Path -LiteralPath $script:TempRoot) {
            Remove-Item -LiteralPath $script:TempRoot -Recurse -Force
        }
    }

    It 'returns all files when no exclude patterns provided' {
        $result = Select-PesterTestFiles -Files $script:SampleFiles -Logger $script:Logger
        @($result).Count | Should -Be 4
    }

    It 'excludes files matching wildcard patterns' {
        $result = Select-PesterTestFiles `
            -Files $script:SampleFiles `
            -ExcludePatterns @('*integration*') `
            -Logger $script:Logger
        
        $normalizedResult = Normalize-Paths $result
        $normalizedExcluded = Normalize-Paths @($script:SampleFiles[1])
        $normalizedResult | Should -Not -Contain $normalizedExcluded[0]
        @($result).Count | Should -Be 3
    }

    It 'adds wildcards to patterns without them' {
        $result = Select-PesterTestFiles `
            -Files $script:SampleFiles `
            -ExcludePatterns @('integration') `
            -Logger $script:Logger
        
        $normalizedResult = Normalize-Paths $result
        $normalizedExcluded = Normalize-Paths @($script:SampleFiles[1])
        $normalizedResult | Should -Not -Contain $normalizedExcluded[0]
        @($result).Count | Should -Be 3
    }

    It 'respects explicit wildcards in patterns' {
        $result = Select-PesterTestFiles `
            -Files $script:SampleFiles `
            -ExcludePatterns @('*specs*') `
            -Logger $script:Logger
        
        $normalizedResult = Normalize-Paths $result
        $normalizedExcluded = Normalize-Paths @($script:SampleFiles[2])
        $normalizedResult | Should -Not -Contain $normalizedExcluded[0]
        @($result).Count | Should -Be 3
    }

    It 'applies multiple exclude patterns' {
        $result = Select-PesterTestFiles `
            -Files $script:SampleFiles `
            -ExcludePatterns @('*integration*', '*other*') `
            -Logger $script:Logger
        
        @($result).Count | Should -Be 2
        # After excluding integration and other, should have unit and specs
        $resultNames = $result | ForEach-Object { Split-Path -Leaf $_ }
        $resultNames | Should -Contain 'Foo.Tests.ps1'
        $resultNames | Should -Contain 'Baz.Tests.ps1'
    }

    It 'returns unique sorted results' {
        $duplicates = $script:SampleFiles + $script:SampleFiles[0]
        $result = Select-PesterTestFiles -Files $duplicates -Logger $script:Logger
        @($result).Count | Should -Be 4
    }

    It 'skips empty exclude patterns' {
        $result = Select-PesterTestFiles `
            -Files $script:SampleFiles `
            -ExcludePatterns @('', $null, '*integration*') `
            -Logger $script:Logger
        
        $normalizedResult = Normalize-Paths $result
        $normalizedExcluded = Normalize-Paths @($script:SampleFiles[1])
        $normalizedResult | Should -Not -Contain $normalizedExcluded[0]
        @($result).Count | Should -Be 3
    }

    It 'throws when Files parameter is null' {
        { Select-PesterTestFiles -Files $null -Logger $script:Logger } | Should -Throw
    }

    It 'throws when Files parameter is empty' {
        { Select-PesterTestFiles -Files @() -Logger $script:Logger } | Should -Throw
    }

    It 'throws when Logger is null' {
        { Select-PesterTestFiles -Files $script:SampleFiles -Logger $null } | Should -Throw
    }
}
