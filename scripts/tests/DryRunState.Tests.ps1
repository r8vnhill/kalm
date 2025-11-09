<#
Pester tests for DryRunState.ps1

Validates the Set-KalmDryRun / Get-KalmDryRun singleton behavior.
#>

Describe 'DryRunState singleton' {
    BeforeAll {
        # Compute repository root from the test script location and dot-source the
        # DryRunState implementation under scripts/lib. This keeps the tests
        # resilient whether run from CI or locally.
        $repoRoot = Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')
        $libPath = Join-Path $repoRoot 'scripts\lib'
        . (Join-Path $libPath 'DryRunState.ps1')
    }

    BeforeEach {
        # Ensure a clean baseline before each test
        Set-KalmDryRun $false
    }

    It 'defaults to false when not set' {
        (Get-KalmDryRun) | Should -BeFalse
    }

    It 'returns true after setting true' {
        Set-KalmDryRun $true
        (Get-KalmDryRun) | Should -BeTrue
    }

    It 'returns false after setting false' {
        Set-KalmDryRun $true
        Set-KalmDryRun $false
        (Get-KalmDryRun) | Should -BeFalse
    }

    It 'persists the last set value across calls' {
        Set-KalmDryRun $true
        Set-KalmDryRun $true
        (Get-KalmDryRun) | Should -BeTrue
        Set-KalmDryRun $false
        (Get-KalmDryRun) | Should -BeFalse
    }

}
