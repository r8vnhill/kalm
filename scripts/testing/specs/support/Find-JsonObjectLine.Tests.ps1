#Requires -Version 7.4
Set-StrictMode -Version 3.0

Describe 'Find-JsonObjectLine' {
    BeforeAll {
        . (Join-Path $PSScriptRoot '..' '..' '..' 'lib' 'Find-JsonObjectLine.ps1')
    }

    It 'returns the last valid JSON object line when logs are present' {
        $lines = @(
            'Gradle message'
            '{"exitCode":0,"status":"ok"}'
            'Another log'
            '{"exitCode":1,"status":"fail"}'
        )

        (Find-JsonObjectLine -Lines $lines) | Should -Be '{"exitCode":1,"status":"fail"}'
    }

    It 'returns null when there are no JSON object lines' {
        $lines = @('line 1', 'line 2')
        (Find-JsonObjectLine -Lines $lines) | Should -BeNullOrEmpty
    }

    It 'ignores valid non-object JSON payloads' {
        $lines = @('"string"', '[1,2,3]', '{"ok":true}')
        (Find-JsonObjectLine -Lines $lines) | Should -Be '{"ok":true}'
    }
}

