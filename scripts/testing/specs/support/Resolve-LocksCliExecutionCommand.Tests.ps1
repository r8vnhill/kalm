#Requires -Version 7.4
Set-StrictMode -Version 3.0

Describe 'Resolve-LocksCliExecutionCommand' {
    BeforeAll {
        $scriptPath = Join-Path $PSScriptRoot '..' '..' '..' 'lib' 'Resolve-LocksCliExecutionCommand.ps1'
        Test-Path -LiteralPath $scriptPath | Should -BeTrue
        . $scriptPath
    }

    It 'rewrites ./gradlew commands for Windows execution' {
        $input = './gradlew preflight --write-locks --no-parallel'
        Resolve-LocksCliExecutionCommand -Command $input -WindowsMode $true |
            Should -Be 'gradlew.bat preflight --write-locks --no-parallel'
    }

    It 'keeps git commands unchanged on Windows' {
        $input = 'git diff -- **/gradle.lockfile settings-gradle.lockfile'
        Resolve-LocksCliExecutionCommand -Command $input -WindowsMode $true |
            Should -Be $input
    }

    It 'keeps commands unchanged on non-Windows mode' {
        $input = './gradlew preflight --write-locks --no-parallel'
        Resolve-LocksCliExecutionCommand -Command $input -WindowsMode $false |
            Should -Be $input
    }
}

