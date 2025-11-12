@{
    Run = @{
        # Execute every test file living under scripts/testing/specs to keep coverage in sync.
        # Path is relative to where Invoke-PesterWithConfig.ps1 is invoked (repo root).
        Path     = @('scripts/testing/specs/*.Tests.ps1')
        PassThru = $true
    }
    TestResult = @{
        Enabled      = $true
        # Output aggregated test results to build directory for CI reporting
        OutputPath   = 'build/test-results/pester/test-results.xml'
        OutputFormat = 'NUnitXml'
    }
}
