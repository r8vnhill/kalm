#Requires -Version 7.4
Set-StrictMode -Version 3.0

<#
.SYNOPSIS
    Pester specification for `Find-LocksCliJsonCommand`.

.DESCRIPTION
    Verifies the behavior of `Find-LocksCliJsonCommand`, a helper that extracts the `command` field
    from a JSON payload emitted by the dependency-lock CLI.

    The function under test operates on mixed output streams (e.g., Gradle logs interleaved with
    structured JSON). It relies on `Find-JsonObjectLine` to locate the last valid JSON object line
    and then parses that object to extract the `command` field.

    ## This specification validates:

    - Correct extraction of the `command` field.
    - Preference for the last JSON object when multiple are present.
    - Trimming of surrounding whitespace in the extracted command.
    - Proper failure when:
        - no JSON payload is present,
        - the JSON cannot be parsed,
        - the payload is not a JSON object,
        - the `command` field is missing or empty.
    - Proper handling of the dependency on `Find-JsonObjectLine`.

    The tests use strict mode to surface unintended global state or uninitialized variables and
    carefully restore any overridden functions to avoid cross-test contamination.
#>

Describe 'Find-LocksCliJsonCommand' {

    # Loads the implementation under test.
    #
    # Resolves and dot-sources `Find-LocksCliJsonCommand.ps1`. The dependency `Find-JsonObjectLine`
    # is expected to be available in scope, or explicitly overridden by individual tests.
    BeforeAll {
        $findLocksJson = Join-Path $PSScriptRoot '..' '..' '..' 'lib' 'Find-LocksCliJsonCommand.ps1'
        Test-Path -LiteralPath $findLocksJson | Should -BeTrue
        . $findLocksJson
    }

    # Captures the original definition of `Find-JsonObjectLine`.
    #
    # Stores any existing `Find-JsonObjectLine` function so tests that override it can safely
    # restore the original implementation after execution.
    BeforeEach {
        $commandLookup = @{
            CommandType = 'Function'
            ErrorAction = 'SilentlyContinue'
        }
        $script:originalFindJsonObjectLine = Get-Command Find-JsonObjectLine @commandLookup
    }

    # Restores `Find-JsonObjectLine` after each test.
    #
    # If the function existed before the test, its original script block is restored. Otherwise,
    # any temporary override is removed to maintain isolation between tests.
    AfterEach {
        if ($null -ne $script:originalFindJsonObjectLine) {
            $setItemArgs = @{
                Path  = 'function:Find-JsonObjectLine'
                Value = $script:originalFindJsonObjectLine.ScriptBlock
            }
            Set-Item @setItemArgs
        }
        else {
            Remove-Item function:Find-JsonObjectLine -ErrorAction SilentlyContinue
        }
    }

    # Validates successful extraction scenarios.
    Context 'happy path' {

        It 'loads Find-JsonObjectLine when the script is dot-sourced' {
            Get-Command Find-JsonObjectLine -ErrorAction SilentlyContinue |
                Should -Not -BeNullOrEmpty

            Find-LocksCliJsonCommand -Lines @('{"command":"git diff"}') |
                Should -Be 'git diff'
        }

        It 'extracts command from JSON payload in noisy output' {
            $lines = @(
                'gradle noise'
                '{"exitCode":0,"command":"./gradlew preflight --write-locks --no-parallel","message":null}'
            )

            Find-LocksCliJsonCommand -Lines $lines |
                Should -Be './gradlew preflight --write-locks --no-parallel'
        }

        It 'uses the last JSON object payload when multiple are present' {
            $lines = @(
                '{"command":"first"}'
                'noise'
                '{"command":"second"}'
            )

            Find-LocksCliJsonCommand -Lines $lines |
                Should -Be 'second'
        }

        It 'trims surrounding whitespace from command' {
            $lines = @('{"command":"  git diff  "}')

            Find-LocksCliJsonCommand -Lines $lines |
                Should -Be 'git diff'
        }
    }

    # Validates failure scenarios and defensive behavior.
    Context 'error handling' {

        It 'throws when JSON payload is missing' {
            { Find-LocksCliJsonCommand -Lines @('noise') } |
                Should -Throw -ExpectedMessage '*did not emit a JSON payload*'
        }

        It 'throws when command is missing in JSON payload' {
            { Find-LocksCliJsonCommand -Lines @('{"exitCode":0,"command":null,"message":"x"}') } |
                Should -Throw -ExpectedMessage '*did not include a command*'
        }

        It 'throws when JSON payload cannot be parsed' {
            Set-Item -Path function:Find-JsonObjectLine -Value {
                param([string[]] $Lines)
                '{bad json'
            }

            { Find-LocksCliJsonCommand -Lines @('noise') } |
                Should -Throw -ExpectedMessage '*Failed to parse*'
        }

        It 'throws when payload is not a JSON object' {
            Set-Item -Path function:Find-JsonObjectLine -Value {
                param([string[]] $Lines)
                'true'
            }

            { Find-LocksCliJsonCommand -Lines @('noise') } |
                Should -Throw -ExpectedMessage '*payload must be an object*'
        }
    }
}
