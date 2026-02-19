#Requires -Version 7.4
Set-StrictMode -Version 3.0

Describe 'Find-LocksCliCommand' {
    BeforeAll {
        $scriptPath = Join-Path $PSScriptRoot '..' '..' '..' 'lib' 'Find-LocksCliCommand.ps1'
        Test-Path -LiteralPath $scriptPath | Should -BeTrue
        . $scriptPath
    }

    It 'returns last non-empty command line' {
        $lines = @(
            'gradle noise'
            ''
            './gradlew preflight --write-locks --no-parallel'
            '   '
        )

        Find-LocksCliCommand -Lines $lines | Should -Be './gradlew preflight --write-locks --no-parallel'
    }

    It 'accepts git command lines' {
        Find-LocksCliCommand -Lines @('git diff -- **/gradle.lockfile settings-gradle.lockfile') |
            Should -Be 'git diff -- **/gradle.lockfile settings-gradle.lockfile'
    }

    It 'throws when no non-empty command is present' {
        { Find-LocksCliCommand -Lines @('', '   ') } | Should -Throw -ExpectedMessage '*did not return a command*'
    }

    It 'throws when emitted command does not match allowlist prefixes' {
        { Find-LocksCliCommand -Lines @('echo pwned') } | Should -Throw -ExpectedMessage '*Unexpected command emitted*'
    }
}

