#Requires -Version 7.4
Set-StrictMode -Version 3.0

# Coverage for path normalization helper: ensures normalization, pipeline handling, and property
# binding stay consistent and easy to reason about.
Describe 'ConvertTo-NormalizedPath' {
    BeforeAll {
        $pathNormalizationScript = Join-Path -Path $PSScriptRoot -ChildPath 'PathNormalization.ps1'
        . $pathNormalizationScript
    }

    It 'normalizes slash direction and collapses duplicate separators' {
        $paths = @('foo\\bar', '/foo////bar//baz')
        $result = ConvertTo-NormalizedPath $paths
        $result | Should -Be @('foo/bar', '/foo/bar/baz')
    }

    It 'accepts pipeline input and skips blank entries' {
        $paths = @('foo\\bar', '', $null, '/foo//bar')
        # Pipeline should honor ordering while dropping empty/null entries
        $result = $paths | ConvertTo-NormalizedPath
        $result | Should -Be @('foo/bar', '/foo/bar')
    }

    It 'binds by property name when objects expose Path' {
        $paths = @(
            [PSCustomObject]@{ Path = 'c:\\temp\\foo' }
            [PSCustomObject]@{ Path = '/tmp//bar' }
        )
        # ValueFromPipelineByPropertyName should pick up 'Path' properties without extra work
        $paths | ConvertTo-NormalizedPath | Should -Be @('c:/temp/foo', '/tmp/bar')
    }
}
