#Requires -Version 7.4
Set-StrictMode -Version 3.0

Describe 'Resolve-LocksCliExecutionCommand' {
    BeforeAll {
        $modulePath = Join-Path $PSScriptRoot '..' '..' '..' 'lib' 'Resolve-LocksCliExecutionCommand.psm1'
        Test-Path -LiteralPath $modulePath | Should -BeTrue
        Import-Module $modulePath -Force
    }

    AfterAll {
        Remove-Module Resolve-LocksCliExecutionCommand -ErrorAction SilentlyContinue
    }

    It 'rewrites command prefixes correctly on Windows: <CommandText>' -TestCases @(
        @{
            CommandText = './gradlew test'
            Expected = 'gradlew.bat test'
        }
        @{
            CommandText = './gradlew.bat test'
            Expected = 'gradlew.bat test'
        }
        @{
            CommandText = 'gradlew.bat test'
            Expected = 'gradlew.bat test'
        }
        @{
            CommandText = 'gradlew test'
            Expected = 'gradlew test'
        }
        @{
            CommandText = ' ./gradlew test'
            Expected = ' gradlew.bat test'
        }
        @{
            CommandText = '"./gradlew test"'
            Expected = '"gradlew.bat test"'
        }
        @{
            CommandText = "'./gradlew test'"
            Expected = "'gradlew.bat test'"
        }
    ) {
        param($CommandText, $Expected)
        Resolve-LocksCliExecutionCommand -Command $CommandText -ForceWindows $true |
            Should -Be $Expected
    }

    It 'keeps commands unchanged on non-Windows mode' {
        $input = './gradlew preflight --write-locks --no-parallel'
        Resolve-LocksCliExecutionCommand -Command $input -ForceWindows $false |
            Should -Be $input
    }

    It 'accepts pipeline input by value' {
        $inputs = @(
            './gradlew test',
            'git diff -- **/gradle.lockfile settings-gradle.lockfile'
        )

        $result = $inputs | Resolve-LocksCliExecutionCommand -ForceWindows $true

        $result | Should -HaveCount 2
        $result[0] | Should -Be 'gradlew.bat test'
        $result[1] | Should -Be 'git diff -- **/gradle.lockfile settings-gradle.lockfile'
    }

    It 'accepts pipeline input by property name' {
        $inputs = @(
            [pscustomobject]@{ Command = './gradlew.bat test' },
            [pscustomobject]@{ Command = 'gradlew test' }
        )

        $result = $inputs | Resolve-LocksCliExecutionCommand -ForceWindows $true

        $result | Should -HaveCount 2
        $result[0] | Should -Be 'gradlew.bat test'
        $result[1] | Should -Be 'gradlew test'
    }

    It 'leaves non-gradlew command prefixes unchanged in Windows mode (PBT)' {
        $random = [System.Random]::new(12345)
        $chars = @('a', 'b', 'c', '.', '/', '-', '_', ' ')
        for ($i = 0; $i -lt 128; $i++) {
            $length = $random.Next(1, 40)
            $valueBuilder = [System.Text.StringBuilder]::new()
            for ($j = 0; $j -lt $length; $j++) {
                [void]$valueBuilder.Append($chars[$random.Next(0, $chars.Length)])
            }
            $input = $valueBuilder.ToString()
            $rewritten = Resolve-LocksCliExecutionCommand -Command $input -ForceWindows $true
            if (
                -not $input.TrimStart().StartsWith('./gradlew', [System.StringComparison]::OrdinalIgnoreCase) -and
                -not $input.TrimStart().StartsWith('"./gradlew', [System.StringComparison]::OrdinalIgnoreCase) -and
                -not $input.TrimStart().StartsWith("'./gradlew", [System.StringComparison]::OrdinalIgnoreCase)
            ) {
                $rewritten | Should -Be $input
            }
        }
    }

    It 'rewrites ./gradlew + suffix to gradlew.bat + suffix in Windows mode (PBT)' {
        $suffixes = @(
            '',
            ' test',
            ' --write-locks --no-parallel',
            '"',
            "'"
        )
        foreach ($suffix in $suffixes) {
            $input = "./gradlew$suffix"
            $expected = "gradlew.bat$suffix"
            Resolve-LocksCliExecutionCommand -Command $input -ForceWindows $true |
                Should -Be $expected
        }
    }

    It 'builds a structured execution spec with executable and arguments' {
        $spec = ConvertTo-LocksCliExecutionSpec -Command './gradlew test --scan' -ForceWindows $true
        $spec.Executable | Should -Be 'gradlew.bat'
        $spec.Arguments | Should -Be @('test', '--scan')
    }
}
