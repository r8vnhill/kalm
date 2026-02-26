<#
Pester coverage for Get-PesterGlobEnumerationRoot helper.
#>

using module '..\..\helpers\discovery\Discover-PesterTestFiles.psm1'

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
