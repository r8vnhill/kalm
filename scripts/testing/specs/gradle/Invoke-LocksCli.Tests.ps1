#Requires -Version 7.4
Set-StrictMode -Version 3.0

Describe 'Invoke-LocksCli module entrypoint' {
    BeforeAll {
        $script:ModulePath = Join-Path $PSScriptRoot '..' '..' '..' 'gradle' 'Invoke-LocksCli.psm1'
    }

    It 'exports Invoke-LocksCli from the gradle module file' {
        Test-Path -LiteralPath $script:ModulePath | Should -BeTrue

        Import-Module $script:ModulePath -Force
        try {
            Get-Command -Name Invoke-LocksCli -Module Invoke-LocksCli -ErrorAction Stop |
                Should -Not -BeNullOrEmpty
        }
        finally {
            Remove-Module Invoke-LocksCli -ErrorAction SilentlyContinue
        }
    }

    It 'supports WhatIf/Confirm semantics for command execution safety' {
        Import-Module $script:ModulePath -Force
        try {
            $cmd = Get-Command -Name Invoke-LocksCli -Module Invoke-LocksCli -ErrorAction Stop
            $cmd.Parameters.ContainsKey('WhatIf') | Should -BeTrue
            $cmd.Parameters.ContainsKey('Confirm') | Should -BeTrue
            $cmd.Parameters.ContainsKey('PassThru') | Should -BeTrue
            $cmd.Parameters.ContainsKey('PerItem') | Should -BeTrue
        }
        finally {
            Remove-Module Invoke-LocksCli -ErrorAction SilentlyContinue
        }
    }

    It 'accumulates pipeline arguments and invokes internal execution once by default' {
        Import-Module $script:ModulePath -Force
        try {
            Mock Get-KalmRepoRoot { 'E:\dev\RESEARCH\kalm' } -ModuleName Invoke-LocksCli
            Mock Resolve-LocksCliGradleWrapperPath { 'E:\dev\RESEARCH\kalm\gradlew.bat' } -ModuleName Invoke-LocksCli
            Mock Test-Path { $true } -ModuleName Invoke-LocksCli
            Mock Invoke-LocksCliInternal { 0 } -ModuleName Invoke-LocksCli

            $result = @('--write-locks', '--no-parallel') | Invoke-LocksCli
            $result.ExitCode | Should -Be 0
            $result.Mode | Should -Be 'Batch'
            $result.Executed | Should -BeTrue

            Assert-MockCalled Invoke-LocksCliInternal -ModuleName Invoke-LocksCli -Times 1 -Exactly -ParameterFilter {
                $CliArgs.Count -eq 2 -and $CliArgs[0] -eq '--write-locks' -and $CliArgs[1] -eq '--no-parallel'
            }
        }
        finally {
            Remove-Module Invoke-LocksCli -ErrorAction SilentlyContinue
        }
    }

    It 'invokes internal execution per pipeline item when PerItem is set' {
        Import-Module $script:ModulePath -Force
        try {
            Mock Get-KalmRepoRoot { 'E:\dev\RESEARCH\kalm' } -ModuleName Invoke-LocksCli
            Mock Resolve-LocksCliGradleWrapperPath { 'E:\dev\RESEARCH\kalm\gradlew.bat' } -ModuleName Invoke-LocksCli
            Mock Test-Path { $true } -ModuleName Invoke-LocksCli
            Mock Invoke-LocksCliInternal { 0 } -ModuleName Invoke-LocksCli

            $result = @(
                [pscustomobject]@{ Args = @('--write-locks') },
                [pscustomobject]@{ Args = @('--verify-locks') }
            ) | Invoke-LocksCli -PerItem

            $result | Should -HaveCount 2
            $result[0].ExitCode | Should -Be 0
            $result[1].ExitCode | Should -Be 0
            $result[0].Mode | Should -Be 'PerItem'
            $result[1].Mode | Should -Be 'PerItem'

            Assert-MockCalled Invoke-LocksCliInternal -ModuleName Invoke-LocksCli -Times 1 -ParameterFilter {
                $CliArgs.Count -eq 1 -and $CliArgs[0] -eq '--write-locks'
            }
            Assert-MockCalled Invoke-LocksCliInternal -ModuleName Invoke-LocksCli -Times 1 -ParameterFilter {
                $CliArgs.Count -eq 1 -and $CliArgs[0] -eq '--verify-locks'
            }
        }
        finally {
            Remove-Module Invoke-LocksCli -ErrorAction SilentlyContinue
        }
    }
}
