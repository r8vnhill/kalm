Describe 'Invoke-GradleWithJdk helper' {
    BeforeAll {
        $script:RepoRoot = (Resolve-Path -Path (Join-Path $PSScriptRoot '..' '..')).Path
    $script:HelperPath = Join-Path -Path $PSScriptRoot -ChildPath '..\..\gradle\Invoke-GradleWithJdk.ps1'
        $script:GradleJdkPath = $env:JAVA_HOME

        if (-not $script:GradleJdkPath -or -not (Test-Path -LiteralPath $script:GradleJdkPath -PathType Container)) {
            $script:InvokeGradleSkipReason = 'Set JAVA_HOME to a valid JDK installation to run these tests.'
        }
        else {
            $script:InvokeGradleSkipReason = $null
        }
    }

    It 'returns details when help task runs successfully' {
        if ($script:InvokeGradleSkipReason) {
            Set-ItResult -Skipped -Message $script:InvokeGradleSkipReason
            return
        }

        $result = & $HelperPath -JdkPath $script:GradleJdkPath -GradleArgument 'help' -WorkingDirectory $RepoRoot -Verbose:$false

        $result | Should -Not -BeNullOrEmpty
        $result.Success | Should -BeTrue
        $result.ExitCode | Should -Be 0
        $result.Command | Should -Match 'gradlew$'
        $result.Arguments | Should -Contain 'help'
        $result.WorkingDirectory | Should -Be $RepoRoot
    }

    It 'raises error for missing gradle wrapper' {
        if ($script:InvokeGradleSkipReason) {
            Set-ItResult -Skipped -Message $script:InvokeGradleSkipReason
            return
        }

        $missingPath = Join-Path $PSScriptRoot 'missing-dir'
        { & $HelperPath -JdkPath $script:GradleJdkPath -WorkingDirectory $missingPath } | Should -Throw
    }
}
