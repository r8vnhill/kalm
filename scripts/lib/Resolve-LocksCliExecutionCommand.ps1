function Resolve-LocksCliExecutionCommand {
    <#
    .SYNOPSIS
        Normalizes a locks CLI command to be executable on the current platform.

    .DESCRIPTION
        On Windows, `cmd /c` cannot execute commands starting with `./`.
        This helper rewrites `./gradlew ...` to `gradlew.bat ...` so emitted commands run correctly.

    .PARAMETER Command
        Validated command emitted by the locks CLI.

    .PARAMETER WindowsMode
        Optional override for tests. Defaults to current `$IsWindows`.

    .OUTPUTS
        System.String. A platform-compatible command string.
    #>
    [CmdletBinding()]
    [OutputType([string])]
    param(
        [Parameter(Mandatory)]
        [string] $Command,

        [bool] $WindowsMode = $IsWindows
    )

    if (-not $WindowsMode) {
        return $Command
    }

    if ($Command -match '^\./gradlew(?:\.bat)?\b') {
        return ($Command -replace '^\./gradlew(?:\.bat)?\b', 'gradlew.bat')
    }

    $Command
}

