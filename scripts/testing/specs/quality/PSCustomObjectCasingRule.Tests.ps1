#Requires -Version 7.4
Set-StrictMode -Version 3.0

#Requires -Version 7.4
Set-StrictMode -Version 3.0

# Ensure the rule path is declared at script scope so PSScriptAnalyzer
# and Pester discover it as used when assigned in the BeforeAll block.
$script:rulePath = $null

Describe 'PSUsePSCustomObjectCasing rule' {
    BeforeAll {
        # TODO: Use helper to resolve repo root
        $script:rulePath = Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..' '..' 'scripts' 'quality' 'PSSARules' 'PSCustomObjectCasingRule.psm1')
    }

    It 'flags lowercase pscustomobject type literal' {
        $results = Invoke-ScriptAnalyzer -CustomRulePath $script:rulePath -ScriptDefinition '[pscustomobject]@{ a = 1 }'
        $results | Should -HaveCount 1
        $results.RuleName | Should -Be 'PSUsePSCustomObjectCasing'
        $results.Message  | Should -Match '\[PSCustomObject\]'
    }

    It 'ignores correctly cased PSCustomObject type literal' {
        $results = Invoke-ScriptAnalyzer -CustomRulePath $script:rulePath -ScriptDefinition '[PSCustomObject]@{ a = 1 }'
        $results | Should -BeNullOrEmpty
    }

    It 'flags lowercase array notation' {
        $results = Invoke-ScriptAnalyzer -CustomRulePath $script:rulePath -ScriptDefinition '[pscustomobject[]]$list = @()'
        $results | Should -HaveCount 1
        $results.RuleName | Should -Be 'PSUsePSCustomObjectCasing'
    }
}
