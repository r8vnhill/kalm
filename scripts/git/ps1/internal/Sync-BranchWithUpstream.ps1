function Sync-BranchWithUpstream {
    [CmdletBinding(SupportsShouldProcess)]
    [OutputType([string])]
    param (
        [Parameter(Mandatory)]
        [System.Management.Automation.CommandInfo]
        $GitCommand,

        [Parameter(Mandatory)]
        [ValidateNotNullOrEmpty()]
        [string]
        $CurrentBranch,

        [ValidateNotNullOrEmpty()]
        [string]
        $RepositoryPath = (Get-Location).Path,

        [ValidateNotNullOrEmpty()]
        [string]
        $UpstreamRef,

        [ValidateNotNullOrEmpty()]
        [string]
        $Remote = 'origin',

        [ValidateSet('FFOnly', 'Merge', 'Rebase')]
        [string]
        $Mode = 'Merge',

        [switch]
        $PassThru
    )

    $gitArgs = @('-C', $RepositoryPath)

    $modeMap = @{
        FFOnly = '--ff-only'
        Rebase = '--rebase'
    }

    if ($modeMap.ContainsKey($Mode)) {
        $up = Resolve-GitUpstreamRef -UpstreamRef $UpstreamRef `
            -DefaultBranch $CurrentBranch `
            -DefaultRemote $Remote

        $gitArgs += @('pull', $modeMap[$Mode], $up.Remote, $up.Branch)

    } else {
        $gitArgs += @('merge', $UpstreamRef)
    }

    $cmdPreview = "git $($gitArgs -join ' ')"
    if (-not $PSCmdlet.ShouldProcess("$RepositoryPath", $cmdPreview)) { return }

    Write-Verbose "🔀 Sync mode: $Mode | Target: $UpstreamRef | Branch: $CurrentBranch"
    $output = Invoke-GitSafely `
        -Git $GitCommand `
        -Arguments $gitArgs `
        -Context "Sync branch '$CurrentBranch' with upstream '$UpstreamRef'"

    if ($PassThru) { $output }
}
