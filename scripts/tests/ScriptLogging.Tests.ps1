<#
Pester tests for the ScriptLogging module (scripts/lib/ScriptLogging.psm1)

This test file validates the public behavior of the `KalmScriptLogger` helper used by the
repository's PowerShell automation helpers.

High-level goals tested here:
- Initialization: logger names are sanitized and files/directories are created
- Logging: entries include run identifier, severity and category
- Rotation: small max-size triggers archive files (fast, deterministic)
- Helper: `LogIfConfigured` safely no-ops when logger is absent and delegates correctly when a
  logger is present

Notes about test design:
- Tests avoid touching user locations by using `$TestDrive` (Pester-provided temporary location)
  and random GUID directories for isolation.
- Rotation thresholds are intentionally tiny (e.g. 64 bytes) so we can force rotation fast without
  writing large files; this is common in unit tests to verify behavior deterministically.
#>

Describe 'KalmScriptLogger' {
    # One-time setup for the test suite: locate and import the module under test.
    BeforeAll {
        # Resolve the repository root relative to this test file and ensure the module exists
        # before importing; fail the suite early if it's missing.
        $repoRoot = Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..' '..')
        $modulePath = Join-Path $repoRoot 'scripts' 'lib' 'ScriptLogging.psm1'
        if (-not (Test-Path -LiteralPath $modulePath)) {
            throw "Missing module: $modulePath"
        }

        # Remove any previously loaded version first, then import fresh
        Get-Module -Name 'ScriptLogging' | Remove-Module -Force -ErrorAction SilentlyContinue
        Import-Module $modulePath -Force
    }

    # Reset the logger state before each example to ensure tests are independent and do not leak a
    # shared logger instance between tests.
    BeforeEach {
        [KalmScriptLogger]::Reset()
    }

    Context 'initialization' {
        $names = @(
            @{ Raw = 'plain'; Expected = 'plain' },
            @{ Raw = 'my*script?'; Expected = 'my-script-' },
            @{ Raw = '  spaced name  '; Expected = 'spaced name' },
            @{ Raw = 'path\component'; Expected = 'path-component' }
        )

        It 'sanitizes "<Raw>" to "<Expected>" and creates directories' -ForEach $names {
            $logDir = Join-Path -Path $TestDrive -ChildPath ([guid]::NewGuid().ToString())
            $logger = [KalmScriptLogger]::Start($Raw, $logDir)

            $logger.Name | Should -Be $Expected
            Test-Path $logDir | Should -BeTrue
            # The logger creates a file at $logger.FilePath, so test that directly
            Test-Path $logger.FilePath | Should -BeTrue
            [KalmScriptLogger]::GetCurrent() | Should -Be $logger
        }

        It 'rejects null or whitespace names' {
            { [KalmScriptLogOptions]::new('') } | Should -Throw
            { [KalmScriptLogOptions]::new('   ') } | Should -Throw
        }
    }

    Context 'logging entries' {
        $levels = @(
            @{ Method = 'LogDebug'; Level = 'DEBUG' },
            @{ Method = 'LogInfo'; Level = 'INFO' },
            @{ Method = 'LogWarning'; Level = 'WARNING' },
            @{ Method = 'LogError'; Level = 'ERROR' },
            @{ Method = 'LogCritical'; Level = 'CRITICAL' }
        )

        It 'writes entries for <Level> with run id and category' -ForEach $levels {
            $logDir = Join-Path -Path $TestDrive -ChildPath ([guid]::NewGuid().ToString())
            $logger = [KalmScriptLogger]::Start('audit', $logDir)

            $logger.$Method('an event occurred', 'Sync')
            $logContent = Get-Content -LiteralPath $logger.FilePath -Raw

            $logContent | Should -Match $logger.RunId
            $logContent | Should -Match $Level
            $logContent | Should -Match 'Sync'
            $logContent | Should -Match 'an event occurred'
        }

        It 'ignores blank messages' {
            $logDir = Join-Path -Path $TestDrive -ChildPath ([guid]::NewGuid().ToString())
            $logger = [KalmScriptLogger]::Start('audit', $logDir)

            $logger.LogInfo('   ', 'SkipMe')
            (Get-Content -LiteralPath $logger.FilePath -ErrorAction SilentlyContinue | Measure-Object -Character).Characters | Should -Be 0
        }
    }

    Context 'rotation' {
        It 'rolls log files when size exceeds configured threshold' {
            # We intentionally use a very small MaxFileSizeBytes to trigger rotation after a few
            # writes. This keeps the unit test fast while still exercising the rotation behavior
            # deterministically.
            $logDir = Join-Path -Path $TestDrive -ChildPath ([guid]::NewGuid().ToString())
            $options = [KalmScriptLogOptions]::new('you-spin-me-right-round')
            $options.Directory = $logDir
            $options.MaxFileSizeBytes = 64      # tiny threshold for unit test
            $options.MaxArchivedFiles = 2       # keep a couple of archives

            $logger = [KalmScriptLogger]::Initialize($options)

            # Write a small number of entries that will exceed the 64-byte threshold and cause at
            # least one archive to be created.
            1..20 | ForEach-Object { $logger.LogInfo("entry $_", 'Rotate') }

            # The active log file and at least one rotated file should exist
            Test-Path (Join-Path $logDir 'you-spin-me-right-round.log') | Should -BeTrue
            Test-Path (Join-Path $logDir 'you-spin-me-right-round.log.1') | Should -BeTrue
        }

        It 'caps archived files based on MaxArchivedFiles' {
            $logDir = Join-Path -Path $TestDrive -ChildPath ([guid]::NewGuid().ToString())
            $options = [KalmScriptLogOptions]::new('cap-test')
            $options.Directory = $logDir
            $options.MaxFileSizeBytes = 48
            $options.MaxArchivedFiles = 1

            $logger = [KalmScriptLogger]::Initialize($options)
            1..40 | ForEach-Object { $logger.LogInfo("entry $_", 'Cap') }

            Test-Path (Join-Path $logDir 'cap-test.log') | Should -BeTrue
            Test-Path (Join-Path $logDir 'cap-test.log.1') | Should -BeTrue
            Test-Path (Join-Path $logDir 'cap-test.log.2') | Should -BeFalse
        }
    }

    Context 'LogIfConfigured helper' {
        It 'no-ops when the logger has not been initialized' {
            # Ensure logger is reset to simulate the 'not-initialized' state
            [KalmScriptLogger]::Reset()

            # The helper should not throw when called without a logger
            { [KalmScriptLogger]::LogIfConfigured(
                    [KalmLogLevel]::Info, 'no logger yet', 'Tests') } | Should -Not -Throw
            [KalmScriptLogger]::GetCurrent() | Should -BeNullOrEmpty
        }

        It 'delegates to the active logger when available' {
            # Start a real logger and then call the helper which should write to the same active
            # log file (delegation behavior).
            $logDir = Join-Path -Path $TestDrive -ChildPath ([guid]::NewGuid().ToString())
            $logger = [KalmScriptLogger]::Start('helper', $logDir)

            [KalmScriptLogger]::LogIfConfigured([KalmLogLevel]::Info, 'delegated entry', 'Helper')
            $content = Get-Content -LiteralPath $logger.FilePath -Raw

            # Verify the delegated message and category are present
            $content | Should -Match 'delegated entry'
            $content | Should -Match 'Helper'
        }
    }
}
