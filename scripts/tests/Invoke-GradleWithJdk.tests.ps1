Describe 'Invoke-GradleWithJdk helper' {
    It 'returns details when help task runs successfully' {
        $repoRoot = (Resolve-Path -Path (Join-Path $PSScriptRoot '..\..')).Path
        $scriptPath = Join-Path -Path $PSScriptRoot -ChildPath '..\..\scripts\Invoke-GradleWithJdk.ps1'
        $result = & $scriptPath -GradleArgument 'help' -WorkingDirectory $repoRoot -Verbose:$false

        $result | Should -Not -BeNullOrEmpty
        $result.Success | Should -BeTrue
        $result.ExitCode | Should -Be 0
        $result.Command | Should -Match 'gradlew$'
        $result.Arguments | Should -Contain 'help'
        $result.WorkingDirectory | Should -Not -BeNullOrEmpty
    }

    It 'raises error for missing gradle wrapper' {
        $scriptPath = Join-Path -Path $PSScriptRoot -ChildPath '..\..\scripts\Invoke-GradleWithJdk.ps1'
        { & $scriptPath -WorkingDirectory (Join-Path $PSScriptRoot 'missing-dir') } | Should -Throw
    }
}
