<#
Pester tests for the ScriptLogging module (scripts/lib/ScriptLogging.psm1)

Purpose
-------
These unit tests exercise the public behavior of the `KalmScriptLogger` helper used by the
repository's PowerShell automation helpers. They are small, fast, and designed to run in CI or
locally via the repo test helper.

What this file covers
- Initialization: logger names are sanitized and required directories/files are created
- Logging: log entries include run identifier, severity and optional category
- Rotation: file rotation/archiving when the file exceeds MaxFileSizeBytes
- Helper: `LogIfConfigured` no-ops safely when no logger is set and delegates to the active
    logger when one is present

Testing guidance & conventions
-----------------------------
- Tests run in an isolated temporary location provided by Pester (`$TestDrive`) and create unique
    subdirectories using GUIDs to avoid collisions and keep the user's environment untouched.
- Rotation thresholds are intentionally tiny (for example, 48–64 bytes) so the tests can trigger
    rotation deterministically without writing large files.
- Tests must not rely on or mutate global state. The suite uses `[KalmScriptLogger]::Reset()` in
    `BeforeEach` to ensure a clean logger state between examples.

API usage notes for contributors
-------------------------------
- Do not assign module script-scope fields directly (for example, avoid assigning
    `$script:KalmLoggerDefaultDirectory`).
    Instead, use the exposed Set/Get helpers provided by the module when tests or helpers need to
    override defaults:

    ```powershell
    [KalmScriptLogger]::SetDefaultDirectory('C:\temp\kalm-logs')
    $d = [KalmScriptLogger]::GetDefaultDirectory()

    [KalmScriptLogger]::SetSyncRoot([object]::new())
    $root = [KalmScriptLogger]::GetSyncRoot()

    # Replace or read the active logger
    [KalmScriptLogger]::SetCurrent($logger)
    $current = [KalmScriptLogger]::GetCurrent()
    ```

    The module also exposes a small Start/Initialize convenience API for creating a logger for
    tests:
    ```powershell
    $logger = [KalmScriptLogger]::Start('test-name', $directory)
    ```

How to run
----------
- Preferred (repo helper):
    ```powershell
    .\scripts\Invoke-PesterWithConfig.ps1
    ```

- Direct (Pester):
    ```powershell
    Invoke-Pester -Script .\scripts\tests\ScriptLogging.Tests.ps1 -OutputFormat NUnitXml -OutputFile build/test-results/pester/test-results.xml
    ```
#>

using module '..\helpers\aggregate\PesterHelpers.psm1'
using module '..\..\..\scripts\lib\ScriptLogging.psm1'

Describe 'KalmScriptLogger' {
    # One-time setup for the test suite: locate and import the module under test.
    BeforeAll {
        # Resolve the repository root relative to this test file and ensure the module exists
        # before importing; fail the suite early if it's missing.
        $repoRoot = Get-KalmRepoRoot -StartPath $PSScriptRoot
        $modulePath = Join-Path $repoRoot 'scripts' 'lib' 'ScriptLogging.psm1'
        if (-not (Test-Path -LiteralPath $modulePath)) {
            throw "Missing module: $modulePath"
        }
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

    Context 'custom sinks' {
        It 'invokes registered sinks with structured log entries' {
            $logDir = Join-Path -Path $TestDrive -ChildPath ([guid]::NewGuid().ToString())
            $logger = [KalmScriptLogger]::Start('sink-test', $logDir)

            $captured = [System.Collections.Generic.List[psobject]]::new()
            $logger.AddSink({
                    param($record)
                    $captured.Add($record) | Out-Null
                })

            $logger.LogInfo('hello world', 'SinkCategory')

            $captured.Count | Should -Be 1
            $captured[0].Level | Should -Be ([KalmLogLevel]::Info)
            $captured[0].Message | Should -Be 'hello world'
            $captured[0].Category | Should -Be 'SinkCategory'
            $captured[0].Logger | Should -Be 'sink-test'
        }

        It 'console sink uses the appropriate Write-* command' {
            $logDir = Join-Path -Path $TestDrive -ChildPath ([guid]::NewGuid().ToString())
            $logger = [KalmScriptLogger]::Start('console-test', $logDir)

            $moduleName = 'ScriptLogging'
            Mock Write-Information {} -ModuleName $moduleName
            Mock Write-Warning {} -ModuleName $moduleName
            Mock Write-Verbose {} -ModuleName $moduleName
            Mock Write-Error {} -ModuleName $moduleName

            $logger.AddConsoleSink()

            $logger.LogInfo('info message', 'Console')
            $logger.LogWarning('warn message', 'Console')

            Assert-MockCalled Write-Information -ModuleName $moduleName -Times 1 -Exactly -ParameterFilter { $MessageData -like '*info message*' }
            Assert-MockCalled Write-Warning -ModuleName $moduleName -Times 1 -Exactly -ParameterFilter { $Message -like '*warn message*' }
            Assert-MockCalled Write-Verbose -ModuleName $moduleName -Times 0
            Assert-MockCalled Write-Error -ModuleName $moduleName -Times 0
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
