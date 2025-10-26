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
        'PSUseShouldProcessForStateChangingFunctions'
    )

    # Per-rule tuning lives here
    Rules               = @{
        PSAvoidUsingWriteHost                       = @{ Severity = 'Error' }
        PSUseApprovedVerbs                          = @{ Severity = 'Warning' }
        PSUseDeclaredVarsMoreThanAssignments        = @{ Severity = 'Error' }
        PSPossibleIncorrectComparisonWithNull       = @{ Severity = 'Error' }

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
    }

    # Optional global minimum severity filter (uncomment to hide infos/warnings)
    # Severity = @('Error')
}
