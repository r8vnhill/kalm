<#
Pester coverage for Select-PesterTestFiles helper.
#>

using module '..\..\..\lib\ScriptLogging.psm1'
using module '..\..\helpers\discovery\Discover-PesterTestFiles.psm1'

Describe 'Select-PesterTestFiles' {
    BeforeAll {
        $pathNormalizationScript = Join-Path -Path $PSScriptRoot -ChildPath '..'
        $pathNormalizationScript = Join-Path -Path $pathNormalizationScript -ChildPath 'support/PathNormalization.ps1'
        . $pathNormalizationScript

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

        $normalizedResult = ConvertTo-NormalizedPath $result
        $normalizedExcluded = ConvertTo-NormalizedPath @($script:SampleFiles[1])
        $normalizedResult | Should -Not -Contain $normalizedExcluded[0]
        @($result).Count | Should -Be 3
    }

    It 'adds wildcards to patterns without them' {
        $result = Select-PesterTestFiles `
            -Files $script:SampleFiles `
            -ExcludePatterns @('integration') `
            -Logger $script:Logger

        $normalizedResult = ConvertTo-NormalizedPath $result
        $normalizedExcluded = ConvertTo-NormalizedPath @($script:SampleFiles[1])
        $normalizedResult | Should -Not -Contain $normalizedExcluded[0]
        @($result).Count | Should -Be 3
    }

    It 'respects explicit wildcards in patterns' {
        $result = Select-PesterTestFiles `
            -Files $script:SampleFiles `
            -ExcludePatterns @('*specs*') `
            -Logger $script:Logger

        $normalizedResult = ConvertTo-NormalizedPath $result
        $normalizedExcluded = ConvertTo-NormalizedPath @($script:SampleFiles[2])
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

        $normalizedResult = ConvertTo-NormalizedPath $result
        $normalizedExcluded = ConvertTo-NormalizedPath @($script:SampleFiles[1])
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
