<#
Pester tests for Import-PesterModule (scripts/testing/helpers/PesterHelpers.psm1)

These tests exercise importing by literal module path and by module name, and verify that success
and failure paths are logged via the repository logger.

Conventions:
- Tests use $TestDrive for temporary filesystem isolation.
- The ScriptLogging module is used to create a [KalmScriptLogger] instance for structured logging
    and verification.
#>

using module '..\..\..\scripts\lib\ScriptLogging.psm1'

Describe 'Import-PesterModule' {
    <#
    Notes on test structure and patterns used in this file:

    - Module vs implementation helpers:
        The project exposes the public helper API via `PesterHelpers.psm1`. That module
        intentionally exports only the public surface (e.g. `Import-PesterModule`,
        `Get-KalmRepoRoot`). For finer-grained, unit-like tests we dot-source the
        implementation script (`Import-PesterModule.ps1`) in `BeforeAll` below. This
        keeps the public module API unchanged while allowing tests to call internal
        helpers directly.

    - Discovery vs execution time in Pester:
        Top-level code in the spec runs during test *discovery*; code inside `BeforeAll`,
        `It`, `BeforeEach`, etc. runs at *execution* time. When generating per-case tests
        in a loop, be careful: capturing loop variables (closures) can cause tests to use
        the final loop value at execution time. To avoid that, we generate literal `It`
        blocks at discovery time using `[ScriptBlock]::Create()` so each test contains
        its own embedded, immutable values.

    - Escaping variables in generated literal blocks:
        Inside the literal string we escape `$` with a backtick so the created scriptblock
        evaluates the variables at execution time rather than during the string interpolation
        at discovery time. At the same time, we interpolate loop-specific values (like
        the case name) into the literal at discovery time so they're baked into the test.
    #>
    BeforeAll {
        # Determine helper module path relative to this spec and import it first
        $helperPath = Join-Path $PSScriptRoot '..' 'helpers' 'aggregate' 'PesterHelpers.psm1'
        try {
            $helperPath = Resolve-Path -Path $helperPath -ErrorAction Stop |
                Select-Object -ExpandProperty Path
        }
        catch {
            throw "Missing helper module (could not resolve path): $helperPath"
        }
        if (-not (Test-Path -LiteralPath $helperPath)) {
            throw "Missing helper module: $helperPath"
        }

        # Import the helper module so Get-KalmRepoRoot / Import-PesterModule are available in tests
        Import-Module $helperPath -Force -ErrorAction Stop

        # Dot-source the specific helper script so internal helper functions
        # (Import-PesterModuleByPath, Import-PesterModuleByName) are available to tests for direct
        # unit-like testing. The module intentionally exports only the public surface; dot-sourcing
        # here gives us access to the implementation helpers for finer-grained tests.
        $helperScript = Join-Path $PSScriptRoot '..' 'helpers' 'module' 'Import-PesterModule.ps1'
        $helperScript = Resolve-Path -Path $helperScript -ErrorAction Stop |
            Select-Object -ExpandProperty Path
        . $helperScript
    }

    Context 'helpers (direct)' {
        It 'Import-PesterModuleByPath imports module file and logs start' {
            $tmpModule = Join-Path $TestDrive ([guid]::NewGuid().ToString() + '.psm1')
            $moduleSource = @'
function Test-Export {
    'ok'
}
Export-ModuleMember -Function Test-Export
'@
            Set-Content -LiteralPath $tmpModule -Value $moduleSource

            $logDir = Join-Path $TestDrive ([guid]::NewGuid().ToString())
            $logger = [KalmScriptLogger]::Start('pesterhelper-path', $logDir)

            $moduleInfo = Import-PesterModuleByPath -ModulePath $tmpModule -Logger $logger

            $moduleInfo | Should -Not -BeNullOrEmpty
            $content = Get-Content -LiteralPath $logger.FilePath -Raw
            $content | Should -Match 'Importing module from path'
        }

        It 'Import-PesterModuleByPath throws for missing path and logs error' {
            $missing = Join-Path $TestDrive 'no-such-module-helpers.psm1'
            $logDir = Join-Path $TestDrive ([guid]::NewGuid().ToString())
            $logger = [KalmScriptLogger]::Start('pesterhelper-path-fail', $logDir)

            { Import-PesterModuleByPath -ModulePath $missing -Logger $logger } | Should -Throw -ExceptionType ([System.IO.FileNotFoundException])

            $content = Get-Content -LiteralPath $logger.FilePath -Raw
            $content | Should -Match 'ModulePath not found'
        }

        It 'Import-PesterModuleByName imports by name and logs start' {
            $logDir = Join-Path $TestDrive ([guid]::NewGuid().ToString())
            $logger = [KalmScriptLogger]::Start('pesterhelper-name', $logDir)
            $minVer = [Version]'5.0.0'

            { Import-PesterModuleByName -ModuleName 'Pester' -MinimumVersion $minVer -Logger $logger } | Should -Not -Throw
            $content = Get-Content -LiteralPath $logger.FilePath -Raw
            $content | Should -Match 'Importing module by name'
        }

        It 'Import-PesterModuleByName throws when minimum version unmet' {
            $logDir = Join-Path $TestDrive ([guid]::NewGuid().ToString())
            $logger = [KalmScriptLogger]::Start('pesterhelper-name-fail', $logDir)
            $minVer = [Version]'99.0.0'

            { Import-PesterModuleByName -ModuleName 'Pester' -MinimumVersion $minVer -Logger $logger } | Should -Throw -ExceptionType ([System.InvalidOperationException])
        }
    }

    BeforeEach {
        # Ensure a clean logger state between tests
        [KalmScriptLogger]::Reset()
    }

    Context 'import by ModulePath' {
        $pathCases = @(
            @{ Name = 'module-success'; ShouldThrow = $false }
            @{ Name = 'module-missing'; ShouldThrow = $true }
        )

        foreach ($case in $pathCases) {
            # Create literal test blocks at discovery time so each It gets its own captured values.
            $caseName = $case.Name
            $shouldThrowToken = if ($case.ShouldThrow) { '$true' } else { '$false' }

            $literal = @"
It '$caseName handles ModulePath imports' {
    `$tmpModule = Join-Path `$TestDrive ([guid]::NewGuid().ToString() + '.psm1')
    if (-not $($shouldThrowToken)) {
        `$moduleSource = @'
function Test-Export {
    'ok'
}
Export-ModuleMember -Function Test-Export
'@
        Set-Content -LiteralPath `$tmpModule -Value `$moduleSource
    }

    `$logDir = Join-Path `$TestDrive ([guid]::NewGuid().ToString())
    `$logger = [KalmScriptLogger]::Start('pesterhelper-path-$caseName', `$logDir)

    `$block = { Import-PesterModule -ModulePath `$tmpModule -Logger `$logger }
    if ($($shouldThrowToken)) {
        `$block | Should -Throw
        (Get-Content -LiteralPath `$logger.FilePath -Raw) | Should -Match 'ModulePath not found'
    }
    else {
        `$block | Should -Not -Throw
        `$content = Get-Content -LiteralPath `$logger.FilePath -Raw
        `$content | Should -Match 'Importing module from path'
    }
}
"@

            & ([ScriptBlock]::Create($literal))
        }
    }

    Context 'import by ModuleName' {
        It 'imports Pester by name when available' {
            $logDir = Join-Path $TestDrive ([guid]::NewGuid().ToString())
            $logger = [KalmScriptLogger]::Start('pester-name', $logDir)

            { Import-PesterModule -ModuleName 'Pester' -Logger $logger } | Should -Not -Throw
            $content = Get-Content -LiteralPath $logger.FilePath -Raw
            $content | Should -Match 'Imported module'
        }

        It 'throws and logs when minimum version requirement is unmet' {
            $logDir = Join-Path $TestDrive ([guid]::NewGuid().ToString())
            $logger = [KalmScriptLogger]::Start('pester-minver', $logDir)

            {
                $params = @{
                    ModuleName     = 'Pester'
                    MinimumVersion = [Version]'99.0.0'
                    Logger         = $logger
                }
                Import-PesterModule @params
            } | Should -Throw
            $content = Get-Content -LiteralPath $logger.FilePath -Raw
            $content | Should -Match 'Failed to import'
        }

        It 'logs when the requested module name is unavailable' {
            $logDir = Join-Path $TestDrive ([guid]::NewGuid().ToString())
            $logger = [KalmScriptLogger]::Start('pester-missing', $logDir)

            { Import-PesterModule -ModuleName 'DefinitelyNotAModule' -Logger $logger } | Should -Throw
            (Get-Content -LiteralPath $logger.FilePath -Raw) | Should -Match 'Failed to import module'
        }
    }
}
