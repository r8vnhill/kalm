#Requires -Version 7.4
Set-StrictMode -Version 3.0

<#
.SYNOPSIS
    Pester specification for the `Join-QuotedArgs` helper.

.DESCRIPTION
    Validates the behavior of `Join-QuotedArgs`, a helper that converts an array of argument tokens
    into a single string suitable for Gradle's `{sh} --args=` forwarding.

    ## The specification covers:

    - **Quoting:** each input token becomes exactly one quoted segment.
    - **Escaping:** backslashes and embedded double quotes are escaped to preserve token boundaries.
    - **Null and empty handling:** empty strings are supported and `$null` tokens are normalized to
      empty.
    - **Lightweight property checks:** randomized inputs ensure one output segment per input token.

.NOTES
    - This spec assumes PowerShell 7.4+ to keep consistency with the rest of the codebase.
    - The test file dot-sources the implementation from `{ps1} lib/Join-QuotedArgs.ps1`.
#>

Describe 'Join-QuotedArgs' {

    # Resolves `{ps1} lib/Join-QuotedArgs.ps1` using the repo root resolver, asserts that the
    # script exists (to fail fast with a clear message), and dot-sources it so `Join-QuotedArgs` is
    # available in the current session.
    BeforeAll {
        . (Join-Path $PSScriptRoot '..' '..' '..' 'lib' 'Get-KalmRepoRoot.ps1')
        $repoRoot = Get-KalmRepoRoot -StartPath $PSScriptRoot
        $scriptPath = Join-Path $repoRoot 'scripts' 'lib' 'Join-QuotedArgs.ps1'
        Test-Path -LiteralPath $scriptPath | Should -BeTrue
        . $scriptPath
    }

    # Verifies token-to-segment quoting and joining semantics.
    Context 'quoting' {

        It 'joins a single token' {
            Join-QuotedArgs -Arguments @('a') | Should -Be '"a"'
        }

        It 'joins multiple tokens with a single space separator' {
            Join-QuotedArgs -Arguments @('a', 'b') | Should -Be '"a" "b"'
        }

        It 'preserves ordering across tokens' {
            Join-QuotedArgs -Arguments @('first', 'second', 'third') | Should -Be '"first" "second" "third"'
        }

        It 'preserves spaces inside tokens' {
            Join-QuotedArgs -Arguments @('a b', 'c') | Should -Be '"a b" "c"'
        }
    }

    # Verifies escaping rules for characters that commonly break argument boundaries.
    Context 'escaping' {

        It 'escapes embedded double quotes' {
            Join-QuotedArgs -Arguments @('a"b') | Should -Be '"a\"b"'
        }

        It 'escapes backslashes' {
            Join-QuotedArgs -Arguments @('C:\x\y') | Should -Be '"C:\\x\\y"'
        }

        It 'handles token ending with backslash' {
            Join-QuotedArgs -Arguments @('C:\x\') | Should -Be '"C:\\x\\"'
        }

        # Uses a local "oracle" to compute the expected escaping behavior.
        #
        # For mixed content (quotes + backslashes), this test derives the expected output using a
        # simple implementation of the escaping rule:
        # - `$null` tokens become empty strings.
        # - `\` becomes `\\`
        # - `"` becomes `\"`
        # This avoids brittle, manually-typed expectation strings.
        It 'handles tokens with both quotes and backslashes' {
            $tokens = @('a"b\c')
            $expected = $tokens |
                ForEach-Object {
                    $token = if ($null -eq $_) { '' } else { [string]$_ }
                    '"' + $token.Replace('\', '\\').Replace('"', '\"') + '"'
                } |
                Join-String -Separator ' '
            Join-QuotedArgs -Arguments $tokens | Should -Be $expected
        }

        It 'handles token that is only a quote' {
            Join-QuotedArgs -Arguments @('"') | Should -Be '"\""'
        }
    }

    # Verifies behavior for empty and null tokens.
    #
    # These tests codify the contract that empty strings are preserved and `$null` tokens are
    # normalized to empty strings, ensuring callers can pass optional tokens safely.
    Context 'null and empty handling' {

        It 'supports empty strings' {
            Join-QuotedArgs -Arguments @('') | Should -Be '""'
        }

        It 'normalizes null tokens to empty strings' {
            Join-QuotedArgs -Arguments @('a', $null) | Should -Be '"a" ""'
        }
    }

    # Performs a lightweight property-style check over randomized inputs.
    #
    # Generates random token lists (including spaces, quotes, and backslashes) and asserts that the
    # output contains exactly one quoted segment per input token.
    #
    # This does not attempt to fully parse Gradle's argument forwarding rules; instead, it checks
    # a stable and implementation-specific invariant that guards against regressions in quoting
    # and joining.
    Context 'lightweight property checks' {

        It 'produces exactly one quoted segment per input token for randomized inputs' {
            $random = [System.Random]::new(1337)
            $alphabet = @(
                [char]'a', [char]'b', [char]'c', [char]'d', [char]'e',
                [char]' ', [char]'"', [char]'\'
            )

            for ($case = 0; $case -lt 50; $case++) {
                $tokenCount = $random.Next(1, 6)
                $tokens = @()
                for ($i = 0; $i -lt $tokenCount; $i++) {
                    $len = $random.Next(0, 8)
                    $chars = for ($j = 0; $j -lt $len; $j++) {
                        $alphabet[$random.Next(0, $alphabet.Count)]
                    }
                    $tokens += (-join $chars)
                }

                $output = Join-QuotedArgs -Arguments $tokens

                # Matches a quoted segment where \" and \\ (and other escaped chars) are allowed.
                $segments = [regex]::Matches($output, '"(?:\\.|[^"])*"')
                $segments.Count | Should -Be $tokens.Count
            }
        }
    }
}
