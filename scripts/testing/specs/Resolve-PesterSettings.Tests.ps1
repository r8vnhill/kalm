<#
Pester coverage for the Resolve-PesterSettings helper routines.

Scenarios exercised:
- Resolve-PesterSettingsPath locates the anchor settings file via absolute or relative script roots
    and fails fast when the file is missing.
- Get-PesterPatterns normalizes scalar and collection `Run.Path` values and safely handles absent,
    null, or empty inputs.
#>

using module '..\helpers\PesterHelpers.psm1'

Describe 'Resolve-PesterSettingsPath' {
    BeforeAll { # Establish a few useful paths for the specs.
        # The directory containing this spec (fallbacks for different Pester/runner contexts)
        $script:SpecRoot =
        if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $PSCommandPath }
        # The canonical `scripts/testing` directory (one level up from specs)
        $script:TestingScriptRoot = (
            Resolve-Path -LiteralPath (Join-Path $script:SpecRoot '..')).ProviderPath
        # Resolved absolute path to the expected `pester.config.psd1` used by tests
        $script:ExpectedSettingsFullPath = (
            Resolve-Path -LiteralPath (
                Join-Path $script:TestingScriptRoot 'pester.config.psd1')).ProviderPath
        # Helper to find the repository root (used by one of the relative-path scenarios)
        $script:RepoRoot = Get-KalmRepoRoot -StartPath $script:SpecRoot
    }

    It 'returns the canonical settings file when it exists under the caller root' {
        Resolve-PesterSettingsPath -ScriptRoot $script:TestingScriptRoot |
            Should -Be $script:ExpectedSettingsFullPath
    }

    It 'resolves the settings file when a relative ScriptRoot path is supplied' {
        # The helper accepts relative paths when invoked from the repository root.
        # This ensures callers can pass 'scripts/testing' and still get the correct file.
        Push-Location $script:RepoRoot
        try {
            Resolve-PesterSettingsPath -ScriptRoot 'scripts/testing' |
                Should -Be $script:ExpectedSettingsFullPath
        }
        finally {
            Pop-Location
        }
    }

    It 'throws when the runtime root lacks a settings file' {
        # This negative test creates an ephemeral directory that does not contain
        # `pester.config.psd1` and verifies the helper fails fast by throwing.
        # We intentionally redirect stderr for the call so the test output remains clean;
        # `Should -Throw -PassThru` returns the thrown exception object which we can assert against
        # to ensure the message is sensible.
        $tempRoot = Join-Path $TestDrive ([guid]::NewGuid().ToString())
        New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null

        try {
            $err = { Resolve-PesterSettingsPath -ScriptRoot $tempRoot 2>$null } |
                Should -Throw -PassThru
            # Allow either the explicit 'Missing Pester runsettings file' message or the more
            # generic 'Pester settings file not found' to keep the
            # assertion tolerant to minor text changes in helper messages.
            $err.Exception.Message |
                Should -Match 'Missing Pester runsettings file|Pester settings file not found'
        }
        finally {
            Remove-Item -LiteralPath $tempRoot -Recurse -Force
        }
    }
}

Describe 'Get-PesterPatterns' {
    It 'wraps a scalar Run.Path value inside an array' {
        # The settings file may specify a single path string or a collection.
        # The helper must ensure a scalar becomes a single-element array so callers can safely call
        # `.Count` and iterate.
        $settings = [PSCustomObject]@{
            Run = @{
                Path = 'scripts/testing/specs/*.Tests.ps1'
            }
        }

        Get-PesterPatterns -Settings $settings | Should -Be @('scripts/testing/specs/*.Tests.ps1')
    }

    It 'returns every pattern when Run.Path is already an array' {
        # If the Run.Path is already a collection, the helper returns it unchanged.
        $expectedPatterns = @('scripts/**/*.Tests.ps1', 'core/**/*..Tests.ps1')
        $settings = [PSCustomObject]@{
            Run = @{
                Path = $expectedPatterns
            }
        }

        Get-PesterPatterns -Settings $settings | Should -Be $expectedPatterns
    }

    It 'produces an empty array when Run.Path is missing' {
        $settings = [PSCustomObject]@{}
        Get-PesterPatterns -Settings $settings | Should -BeNullOrEmpty
    }

    It 'produces an empty array when Run.Path is null' {
        $settings = [PSCustomObject]@{
            Run = @{
                Path = $null
            }
        }

        Get-PesterPatterns -Settings $settings | Should -BeNullOrEmpty
    }

    It 'produces an empty array when Run.Path is an empty collection' {
        $settings = [PSCustomObject]@{
            Run = @{
                Path = @()
            }
        }

        Get-PesterPatterns -Settings $settings | Should -BeNullOrEmpty
    }

    It 'produces an empty array when no settings object is supplied' {
        Get-PesterPatterns -Settings $null | Should -BeNullOrEmpty
    }
}
