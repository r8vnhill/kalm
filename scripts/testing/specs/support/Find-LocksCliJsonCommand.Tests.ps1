#Requires -Version 7.4
Set-StrictMode -Version 3.0

Describe 'Find-LocksCliJsonCommand' {
    BeforeAll {
        $script:findJsonObjectLine = Join-Path $PSScriptRoot '..' '..' '..' 'lib' 'Find-JsonObjectLine.ps1'
        $findLocksJson = Join-Path $PSScriptRoot '..' '..' '..' 'lib' 'Find-LocksCliJsonCommand.ps1'
        Test-Path -LiteralPath $script:findJsonObjectLine | Should -BeTrue
        Test-Path -LiteralPath $findLocksJson | Should -BeTrue
        . $script:findJsonObjectLine
        . $findLocksJson
    }

    It 'extracts command from JSON payload in noisy output' {
        $lines = @(
            'gradle noise'
            '{"exitCode":0,"command":"./gradlew preflight --write-locks --no-parallel","message":null}'
        )
        Find-LocksCliJsonCommand -Lines $lines | Should -Be './gradlew preflight --write-locks --no-parallel'
    }

    It 'throws when JSON payload is missing' {
        { Find-LocksCliJsonCommand -Lines @('noise') } | Should -Throw -ExpectedMessage '*did not emit a JSON payload*'
    }

    It 'throws when command is missing in JSON payload' {
        { Find-LocksCliJsonCommand -Lines @('{"exitCode":0,"command":null,"message":"x"}') } |
            Should -Throw -ExpectedMessage '*did not include a command*'
    }

    It 'throws when Find-JsonObjectLine is not available in scope' {
        Remove-Item function:Find-JsonObjectLine -ErrorAction SilentlyContinue
        try {
            { Find-LocksCliJsonCommand -Lines @('{"command":"git diff"}') } |
                Should -Throw -ExpectedMessage '*requires Find-JsonObjectLine*'
        }
        finally {
            . $script:findJsonObjectLine
        }
    }

    It 'throws when payload is not a JSON object' {
        function Find-JsonObjectLine {
            param([string[]] $Lines)
            'true'
        }
        try {
            { Find-LocksCliJsonCommand -Lines @('noise') } |
                Should -Throw -ExpectedMessage '*payload must be an object*'
        }
        finally {
            Remove-Item function:Find-JsonObjectLine -ErrorAction SilentlyContinue
            . $script:findJsonObjectLine
        }
    }
}
