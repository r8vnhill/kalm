<#
Pester tests covering Get-KalmRepoRoot from scripts/testing/helpers.

This spec verifies the behaviors introduced by the helper:
- it locates the repository root by walking parent directories
- it accepts different StartPath forms (absolute, relative, provider path)
- it supports a custom marker name (e.g. files like MARKER.txt)
- it optionally returns a DirectoryInfo object when -AsObject is used

## Notes on Pester and discovery:
Pester evaluates top-level code at discovery time. `BeforeAll` runs at execution time. Any values
computed in `BeforeAll` (for example, the repo root) will not be available during discovery. To
avoid discovery-time errors we construct runtime-only data (the test cases) inside `BeforeAll` and
store them in script scope for use by the `It` blocks.

---

This file generates one `It` block per test case at discovery time so each case is reported
independently by Pester (one pass/fail line per case). To do this safely we compute the `$RepoRoot`
at discovery (the `using module` above ensures `Get-KalmRepoRoot` is available during parse time)
and then create a literal scriptblock for each case embedding the StartPath and expected root.

## Why this approach?
- Per-case `It` blocks give clearer test reporting.
- Embedding literal [ScriptBlock]s avoids closure/scope capture issues that are common when
    generating tests in loops at discovery time.
- We still set `$script:RepoRoot` in `BeforeAll` so tests that execute in isolated scopes can
    access the expected root value at runtime.
#>

using module '..\helpers\PesterHelpers.psm1'

# Compute RepoRoot and per-case table at discovery time. Because we use `using module` above the
# helper is available at parse/discovery time and calling `Get-KalmRepoRoot` here is safe; this
# allows us to generate a separate `It` block for each case so Pester will report them individually.
$RepoRoot = Get-KalmRepoRoot -StartPath $PSScriptRoot
# Expose a script-scoped alias for tests that run in different scopes/contexts
$script:RepoRoot = $RepoRoot
$Cases = @(
    @{
        Name      = 'test-root'
        StartPath = $RepoRoot
    },
    @{
        Name      = 'test-script'
        StartPath = $PSScriptRoot
    },
    @{
        Name      = 'test-nested'
        StartPath = Join-Path -Path $PSScriptRoot -ChildPath '..\..\testing\specs'
    },
    @{
        Name      = 'test-cwd'
        StartPath = (Get-Location).ProviderPath
    },
    @{
        Name      = 'test-temp-child'
        StartPath = Join-Path -Path $RepoRoot -ChildPath 'scripts/testing/specs'
    }
)

Describe 'Get-KalmRepoRoot' {
    # Ensure runtime visibility of the repo root for test execution: some Pester execution contexts
    # run tests in isolated scopes, so setting the value in BeforeAll guarantees availability when
    # the It blocks run.
    BeforeAll {
        $script:RepoRoot = Get-KalmRepoRoot -StartPath $PSScriptRoot
    }

    # Create one `It` block per case at discovery time while avoiding closure issues by passing
    # case values as parameters to an inline scriptblock.
    foreach ($case in $Cases) {
        $name = $case.Name -replace "'", "''"
        $startPath = $case.StartPath -replace "'", "''"
        $repoRootLiteral = $RepoRoot -replace "'", "''"

        $code = @"
(Get-KalmRepoRoot -StartPath '$startPath') | Should -Be '$repoRootLiteral'
Test-Path (Join-Path (Get-KalmRepoRoot -StartPath '$startPath') '.git') | Should -BeTrue
"@

        $sb = [ScriptBlock]::Create($code)
        It "normalizes start path: $name" $sb
    }

    It 'uses the current path when no StartPath is supplied' {
        $nested = Join-Path $PSScriptRoot .. ..
        Push-Location $nested
        try {
            $resolved = Get-KalmRepoRoot
            $resolved | Should -Be $script:RepoRoot
        }
        finally {
            Pop-Location
        }
    }

    It 'throws when no .git ancestor is found' {
        $tempDir = Join-Path $TestDrive ([guid]::NewGuid().ToString())
        New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
        try {
            { Get-KalmRepoRoot -StartPath $tempDir } |
                Should -Throw -ExpectedMessage 'Cannot bind argument to parameter*'
        }
        finally {
            Remove-Item -LiteralPath $tempDir -Recurse -Force
        }
    }

    It 'throws when StartPath does not exist' {
        $nonExistent = Join-Path $TestDrive ([guid]::NewGuid().ToString())
        { Get-KalmRepoRoot -StartPath $nonExistent } |
            Should -Throw -ExceptionType ([System.IO.DirectoryNotFoundException])
    }

    It 'honors a custom marker name' {
        $tempDir = Join-Path -Path $TestDrive -ChildPath ([guid]::NewGuid().ToString())
        $child = Join-Path -Path $tempDir -ChildPath 'level1\level2'
        New-Item -ItemType Directory -Path $child -Force | Out-Null
        $markerFile = Join-Path -Path $tempDir -ChildPath 'MARKER.txt'
        New-Item -ItemType File -Path $markerFile -Force | Out-Null

        try {
            $root = Get-KalmRepoRoot -StartPath $child -Marker 'MARKER.txt'
            $root | Should -Be (Get-Item -LiteralPath $tempDir).FullName
        }
        finally {
            Remove-Item -LiteralPath $tempDir -Recurse -Force
        }
    }

    It 'returns a DirectoryInfo when requested via AsObject' {
        $root = Get-KalmRepoRoot -StartPath $PSScriptRoot -Marker '.git' -AsObject
        $root | Should -BeOfType [System.IO.DirectoryInfo]
        $root.FullName | Should -Be (Get-KalmRepoRoot -StartPath $PSScriptRoot)
    }

    It 'fails fast for whitespace StartPath inputs' {
        { Get-KalmRepoRoot -StartPath '   ' } | Should -Throw -ExpectedMessage 'Cannot validate argument on parameter*white-space*'
    }
}
