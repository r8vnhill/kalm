@{
    Run = @{
        # Use the tests living under scripts/tests for script-local helpers.
        Path     = @('scripts/tests/DryRunState.Tests.ps1')
        PassThru = $true
    }
    TestResult = @{
        Enabled      = $true
        OutputPath   = 'build/test-results/pester/dry-run-state.xml'
        OutputFormat = 'NUnitXml'
    }
}
