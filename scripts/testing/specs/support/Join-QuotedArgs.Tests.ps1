#Requires -Version 7.4
Set-StrictMode -Version 3.0

Describe 'Join-QuotedArgs' {
    BeforeAll {
        . (Join-Path $PSScriptRoot '..' '..' '..' 'lib' 'Join-QuotedArgs.ps1')
    }

    It 'joins a single token' {
        Join-QuotedArgs -Arguments @('a') | Should -Be '"a"'
    }

    It 'preserves spaces inside tokens' {
        Join-QuotedArgs -Arguments @('a b') | Should -Be '"a b"'
    }

    It 'escapes embedded double quotes' {
        Join-QuotedArgs -Arguments @('a"b') | Should -Be '"a\"b"'
    }

    It 'escapes backslashes' {
        Join-QuotedArgs -Arguments @('C:\x\y') | Should -Be '"C:\\x\\y"'
    }

    It 'supports empty strings' {
        Join-QuotedArgs -Arguments @('') | Should -Be '""'
    }

    It 'normalizes null tokens to empty strings' {
        Join-QuotedArgs -Arguments @('a', $null) | Should -Be '"a" ""'
    }
}
