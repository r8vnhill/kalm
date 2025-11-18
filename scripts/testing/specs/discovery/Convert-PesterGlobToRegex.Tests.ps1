<#
Pester coverage for Convert-PesterGlobToRegex helper.
#>

using module '..\..\helpers\discovery\Discover-PesterTestFiles.psm1'

Describe 'Convert-PesterGlobToRegex' {
    It 'converts a simple wildcard pattern' {
        Convert-PesterGlobToRegex -Pattern '*.ps1' | Should -Be '^[^/]*\.ps1$'
    }

    It 'converts ** to match multiple directories' {
        # '**/' should become '(?:(?:[^/]+/)*)' to match zero or more complete path segments
        Convert-PesterGlobToRegex -Pattern '**/test.ps1' | Should -Be '^(?:(?:[^/]+/)*)test\.ps1$'
    }

    It 'converts ** in the middle of a pattern' {
        Convert-PesterGlobToRegex -Pattern 'src/**/tests/*.ps1' |
            Should -Be '^src/(?:(?:[^/]+/)*)tests/[^/]*\.ps1$'
    }

    It 'converts ? to match a single character' {
        Convert-PesterGlobToRegex -Pattern 'test?.ps1' | Should -Be '^test[^/]\.ps1$'
    }

    It 'escapes special regex characters' {
        Convert-PesterGlobToRegex -Pattern 'test[1].ps1' | Should -Be '^test\[1]\.ps1$'
    }

    It 'normalizes backslashes to forward slashes' {
        Convert-PesterGlobToRegex -Pattern 'src\tests\*.ps1' | Should -Be '^src/tests/[^/]*\.ps1$'
    }

    It 'handles standalone ** without trailing slash' {
        Convert-PesterGlobToRegex -Pattern 'src/**' | Should -Match '\.\*'
    }
}
