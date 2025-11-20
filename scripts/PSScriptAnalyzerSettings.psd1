@{
    # Use default built-in rules plus the ones we list
    IncludeDefaultRules = $true

    # Start with a curated rule set
    IncludeRules        = @(
        'PSAvoidUsingWriteHost',
        'PSUseApprovedVerbs',
        'PSUseDeclaredVarsMoreThanAssignments',
        'PSPossibleIncorrectComparisonWithNull',
        'PSAvoidGlobalVars',
        'PSReviewUnusedParameter',
        'PSUseConsistentWhitespace',
        'PSUseConsistentIndentation',
        'PSPlaceOpenBrace',
        'PSPlaceCloseBrace',
        'PSAlignAssignmentStatement',
        'PSAvoidDefaultValueSwitchParameter',
        'PSAvoidUsingCmdletAliases',
        'PSUseShouldProcessForStateChangingFunctions',
        'PSUsePSCustomObjectCasing'
    )

    CustomRulePath     = @('quality/PSSARules/PSCustomObjectCasingRule.psm1')

    # Per-rule tuning lives here
    Rules               = @{
        PSAvoidUsingWriteHost                       = @{ Severity = 'Error' }
        PSUseApprovedVerbs                          = @{ Severity = 'Warning' }
        PSUseDeclaredVarsMoreThanAssignments        = @{ Severity = 'Error' }
        PSPossibleIncorrectComparisonWithNull       = @{ Severity = 'Error' }
        PSUsePSCustomObjectCasing                   = @{ Severity = 'Warning' }

        PSUseConsistentWhitespace                   = @{
            CheckInnerBrace = $true
            CheckOpenBrace  = $true
            CheckOpenParen  = $true
            CheckSeparator  = $true
            CheckPipe       = $true
            CheckParameter  = $true
        }

        PSUseConsistentIndentation                  = @{
            Kind                = 'space'
            IndentationSize     = 4
            PipelineIndentation = 'IncreaseIndentationAfterEveryPipeline'
        }

        PSPlaceOpenBrace                            = @{ Enable = $true; OnSameLine = $true; NewLineAfter = $true; IgnoreOneLineBlock = $true }
        PSPlaceCloseBrace                           = @{ Enable = $false }

        PSAlignAssignmentStatement                  = @{ Enable = $false }

        PSAvoidUsingCmdletAliases                   = @{ Severity = 'Information' }

        PSUseShouldProcessForStateChangingFunctions = @{ Severity = 'Warning' }

        # Removed custom long-line rule and any associated override; rely on
        # editor configuration (.editorconfig / VS Code rulers) and optional
        # future CI checks instead of a bespoke rule implementation.
    }

    # Optional global minimum severity filter (uncomment to hide infos/warnings)
    # Severity = @('Error')

    # Set a preferred maximum line length for PowerShell scripts. Some
    # analyzers/formatters read this value; PSScriptAnalyzer may require a
    # dedicated rule to enforce it. We include it here as a repository
    # convention and to make the intent explicit for contributors and CI.
        # NOTE: PSScriptAnalyzer does NOT accept a 'MaximumLineLength' setting in its
        # settings hashtable. Including that key causes Invoke-ScriptAnalyzer to fail
        # with "maximumlinelength is not a valid key". To enforce a 100-char line
        # width for PowerShell sources, use one of the following options:
        #
        # - Editor/IDE configuration: we use .editorconfig and .vscode settings to
        #   surface a 100-column ruler and configure formatters/linters for editors.
        # - CI/static check: add a lightweight line-length checker script (see
        #   scripts/ci/check-line-length.ps1) and run it in CI or via a repo helper.
        # - Custom PSScriptAnalyzer rule: implement a custom rule module and add its
        #   path to 'CustomRulePath' below if you need enforcement inside PSScriptAnalyzer.
        #
        # The key was removed to avoid PSScriptAnalyzer errors. Keep EditorConfig/VS
        # Code settings as the primary enforcement mechanism.
}
