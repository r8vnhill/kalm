<#
Pester coverage for the Resolve-PesterSettings helper routines.

Scenarios exercised:
- Resolve-PesterSettingsPath locates the anchor settings file via absolute or relative script roots and fails fast when the file is missing.
- Get-PesterPatterns normalizes scalar and collection `Run.Path` values and safely handles absent, null, or empty inputs.
#>

using module '..\helpers\PesterHelpers.psm1'

Describe 'Resolve-PesterSettingsPath' {
    BeforeAll {
        $script:SpecRoot = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $PSCommandPath }
        $script:TestingScriptRoot = (Resolve-Path -LiteralPath (Join-Path -Path $script:SpecRoot -ChildPath '..')).ProviderPath
        $script:ExpectedSettingsFullPath = (Resolve-Path -LiteralPath (Join-Path -Path $script:TestingScriptRoot -ChildPath 'pester.config.psd1')).ProviderPath
        $script:RepoRoot = Get-KalmRepoRoot -StartPath $script:SpecRoot
    }

    It 'returns the canonical settings file when it exists under the caller root' {
        $resolved = Resolve-PesterSettingsPath -ScriptRoot $script:TestingScriptRoot
        $resolved | Should -Be $script:ExpectedSettingsFullPath
    }

    It 'resolves the settings file when a relative ScriptRoot path is supplied' {
        Push-Location $script:RepoRoot
        try {
            $resolved = Resolve-PesterSettingsPath -ScriptRoot 'scripts/testing'
            $resolved | Should -Be $script:ExpectedSettingsFullPath
        }
        finally {
            Pop-Location
        }
    }

    It 'throws when the runtime root lacks a settings file' {
        $tempRoot = Join-Path -Path $TestDrive -ChildPath ([guid]::NewGuid().ToString())
        New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null

        try {
            $err = { Resolve-PesterSettingsPath -ScriptRoot $tempRoot 2>$null } |
                Should -Throw -PassThru
            $err.Exception.Message | Should -Match 'Missing Pester runsettings file|Pester settings file not found'
        }
        finally {
            Remove-Item -LiteralPath $tempRoot -Recurse -Force
        }
    }
}

Describe 'Get-PesterPatterns' {
    It 'wraps a scalar Run.Path value inside an array' {
        $settings = [PSCustomObject]@{
            Run = @{
                Path = 'scripts/testing/specs/*.Tests.ps1'
            }
        }

        $patterns = Get-PesterPatterns -Settings $settings
        $patterns | Should -Be @('scripts/testing/specs/*.Tests.ps1')
    }

    It 'returns every pattern when Run.Path is already an array' {
        $expectedPatterns = @('scripts/**/*.Tests.ps1', 'core/**/*.Tests.ps1')
        $settings = [PSCustomObject]@{
            Run = @{
                Path = $expectedPatterns
            }
        }

        $patterns = Get-PesterPatterns -Settings $settings
        $patterns | Should -Be $expectedPatterns
    }

    It 'produces an empty array when Run.Path is missing' {
        $settings = [PSCustomObject]@{}
        $patterns = Get-PesterPatterns -Settings $settings
        $patterns | Should -BeNullOrEmpty
    }

    It 'produces an empty array when Run.Path is null' {
        $settings = [PSCustomObject]@{
            Run = @{
                Path = $null
            }
        }

        $patterns = Get-PesterPatterns -Settings $settings
        $patterns | Should -BeNullOrEmpty
    }

    It 'produces an empty array when Run.Path is an empty collection' {
        $settings = [PSCustomObject]@{
            Run = @{
                Path = @()
            }
        }

        $patterns = Get-PesterPatterns -Settings $settings
        $patterns | Should -BeNullOrEmpty
    }

    It 'produces an empty array when no settings object is supplied' {
        $patterns = Get-PesterPatterns -Settings $null
        $patterns | Should -BeNullOrEmpty
    }
}
