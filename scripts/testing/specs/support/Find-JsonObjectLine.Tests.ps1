#Requires -Version 7.4
Set-StrictMode -Version 3.0

<#
.SYNOPSIS
    Pester specification for `Find-JsonObjectLine`.

.DESCRIPTION
    Defines the expected behavior of `Find-JsonObjectLine`, a helper that scans a mixed output
    stream (for example, Gradle logs plus structured output) and returns the last line that
    represents a valid single-line JSON object.

    The function is intended for scenarios where a tool emits human-readable logs intermixed with
    machine-readable JSON payloads (e.g., a final `{ "exitCode": 0, ... }` summary). The caller can
    then parse the returned line as JSON and use it as the authoritative structured result.

    ## This specification verifies:

    - Selection: the last JSON object in the stream wins, even with interleaved logs.
    - Robustness: trailing whitespace and blank lines are ignored.
    - Filtering: arrays and primitives are not treated as valid results.
    - Failure tolerance: invalid JSON lines are ignored in favor of earlier valid objects.
    - Contract: `{}` is considered a valid JSON object.
    - Randomized checks: the contract holds across random mixtures of noise and injected objects.

.NOTES
    - This test suite requires PowerShell 7.4+.
    - The implementation is dot-sourced from `{ps1} lib/Find-JsonObjectLine.ps1`.
    - The tests use `Set-StrictMode -Version 3.0` to surface uninitialized variables and other
      scripting mistakes early.
#>

Describe 'Find-JsonObjectLine' {

    # Loads the implementation under test.
    # Resolves `{ps1} lib/Find-JsonObjectLine.ps1` relative to the test file location, asserts that
    # the file exists, and dot-sources it so `Find-JsonObjectLine` is available for the tests in
    # this session.
    BeforeAll {
        $scriptPath = Join-Path $PSScriptRoot '..' '..' '..' 'lib' 'Find-JsonObjectLine.ps1'
        Test-Path -LiteralPath $scriptPath | Should -BeTrue
        . $scriptPath
    }

    # Validates the "last object wins" selection rule.
    # When multiple JSON object lines are present, `Find-JsonObjectLine` returns the last one
    # (after trimming), ignoring log lines and other non-object output.
    Context 'selection (last object wins)' {

        It 'returns the last valid JSON object line when logs are present' {
            $lines = @(
                'Gradle message'
                '{"exitCode":0,"status":"ok"}'
                'Another log'
                '{"exitCode":1,"status":"fail"}'
            )

            (Find-JsonObjectLine -Lines $lines) | Should -Be '{"exitCode":1,"status":"fail"}'
        }

        It 'ignores trailing blank lines after a JSON object line' {
            $lines = @(
                'noise'
                '{"exitCode":0}'
                ''
                '   '
            )

            (Find-JsonObjectLine -Lines $lines) | Should -Be '{"exitCode":0}'
        }

        It 'returns an earlier valid object when the last candidate is invalid JSON' {
            $lines = @(
                '{"ok":true}'
                '{bad json'
            )

            (Find-JsonObjectLine -Lines $lines) | Should -Be '{"ok":true}'
        }

        It 'returns the last JSON object when arrays and primitives appear later' {
            $lines = @(
                '{"selected":1}'
                '[1,2,3]'
                '"x"'
                'true'
                'null'
            )

            (Find-JsonObjectLine -Lines $lines) | Should -Be '{"selected":1}'
        }
    }

    # Validates filtering rules for non-object JSON and invalid JSON.
    # Only JSON objects are considered valid results. JSON arrays and primitives must be ignored.
    # If no JSON object line is present, the function returns `$null`.
    Context 'filtering (arrays/primitives/invalid)' {

        It 'returns null when there are no JSON object lines' {
            $lines = @('line 1', 'line 2')
            (Find-JsonObjectLine -Lines $lines) | Should -BeNullOrEmpty
        }

        It 'returns null when only a JSON array is present' {
            $lines = @('noise', '[1,2,3]')
            (Find-JsonObjectLine -Lines $lines) | Should -BeNullOrEmpty
        }

        It 'returns null when only JSON primitives are present' {
            $lines = @('"x"', '1', 'true', 'false', 'null')
            (Find-JsonObjectLine -Lines $lines) | Should -BeNullOrEmpty
        }
    }

    # Validates whitespace and empty-input behavior. Candidates are trimmed before parsing. Empty
    # inputs and streams containing only blank lines produce no result.
    Context 'whitespace handling' {

        It 'handles whitespace around JSON object lines' {
            $lines = @(
                '   {"status":"ok"}   '
            )

            (Find-JsonObjectLine -Lines $lines) | Should -Be '{"status":"ok"}'
        }

        It 'returns null for empty input' {
            (Find-JsonObjectLine -Lines @()) | Should -BeNullOrEmpty
        }

        It 'returns null when only blank lines are present' {
            (Find-JsonObjectLine -Lines @('', '   ')) | Should -BeNullOrEmpty
        }
    }

    # Defines the JSON-object contract used by the implementation. An empty JSON object (`{}`) is
    # considered a valid JSON object line. If this behavior is not desired, the implementation
    # should enforce a non-empty property set and this test should be updated accordingly.
    Context 'object contract' {

        It 'accepts an empty JSON object line' {
            (Find-JsonObjectLine -Lines @('noise', '{}')) | Should -Be '{}'
        }
    }

    # Performs a lightweight property-style check over randomized mixed output. Generates arrays of
    # "noise" lines (logs, invalid JSON, arrays, and primitives), injects a valid JSON object line
    # at a random position (optionally padded with whitespace), and then computes the expected
    # result by scanning from bottom to top and selecting the first line that parses as a JSON
    # object.
    #
    # This test is intended to catch regressions in:
    # - reverse scanning
    # - trimming behavior
    # - correct filtering of arrays/primitives
    # - tolerance of invalid JSON
    Context 'randomized checks' {

        It 'finds the expected object line in randomized mixed output' {
            $random = [System.Random]::new(20260219)
            $noiseTokens = @('noise', '[1,2,3]', '"str"', 'true', 'null', '{bad')

            for ($case = 0; $case -lt 30; $case++) {
                $lineCount = $random.Next(3, 10)
                $lines = for ($i = 0; $i -lt $lineCount; $i++) {
                    $noiseTokens[$random.Next(0, $noiseTokens.Count)]
                }

                $objectLine = ('{{"case":{0},"ok":true}}' -f $case)
                $insertAt = $random.Next(0, $lineCount)
                $lines[$insertAt] = if ($random.NextDouble() -lt 0.5) {
                    "  $objectLine  "
                }
                else {
                    $objectLine
                }

                # Compute the expected result using the semantic contract: last (from the bottom)
                # trimmed line that parses as a JSON object.
                $expected = $null
                for ($i = $lines.Count - 1; $i -ge 0; $i--) {
                    $candidate = ($lines[$i] ?? '').Trim()
                    if ([string]::IsNullOrWhiteSpace($candidate)) { continue }
                    try {
                        $parsed = ConvertFrom-Json -InputObject $candidate -AsHashtable -ErrorAction Stop
                        if ($parsed -is [hashtable]) {
                            $expected = $candidate
                            break
                        }
                    }
                    catch { }
                }

                (Find-JsonObjectLine -Lines $lines) | Should -Be $expected
            }
        }
    }
}
