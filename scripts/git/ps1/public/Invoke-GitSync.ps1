function Invoke-GitSync {
    [Alias('Git-Sync')]
    [CmdletBinding(SupportsShouldProcess, ConfirmImpact = 'Medium')]
    [OutputType([string])]
    param (
        [switch] $Push,
        [switch] $PassThru,

        [string] $RepositoryPath = (Get-Location).Path,

        [ValidateSet('FFOnly', 'Merge', 'Rebase')]
        [string] $Mode = 'Merge',

        [ValidateNotNullOrEmpty()]
        [string] $Remote = 'origin',

        [ValidateNotNullOrEmpty()]
        [string] $UpstreamRef,

        [switch] $NoFetch
    )

    $git = Get-CommandOrElse -Command git -Else {
        throw '❌ Git is required to perform sync operations.'
    }

    $RepositoryPath = Convert-RepositoryPath `
        -GitCommand $git `
        -RepositoryPath $RepositoryPath `
        -EnsureDirectory `
        -EnsureGitRepository

    if (-not $NoFetch) {
        if ($PSCmdlet.ShouldProcess($RepositoryPath, 'git fetch --all --prune --tags')) {
            Invoke-GitFetch `
                -GitCommand $git `
                -RepositoryPath $RepositoryPath `
                -All `
                -Prune `
                -Tags `
                -PassThru:$PassThru | ForEach-Object { if ($PassThru) { $_ } }
        }
    }

    $currentBranch = Get-CurrentBranchName `
        -GitCommand $git `
        -DetachedBehavior Error `
        -RepositoryPath $RepositoryPath `
        -EnsureGitRepository

    $detected = $null
    if (-not $UpstreamRef) {
        $detected = & $git `
            -C $RepositoryPath rev-parse `
            --abbrev-ref `
            --symbolic-full-name '@{u}' 2>$null
        if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($detected)) {
            $UpstreamRef = $detected.Trim()
            Write-Verbose "🔎 Detected upstream: $UpstreamRef"
        }
    }

    $hadConfiguredUpstream = -not [string]::IsNullOrWhiteSpace($detected)
    $UpstreamRef ??= "$Remote/$currentBranch"
    if (-not $hadConfiguredUpstream) {
        Write-Verbose "ℹ️ No upstream configured; falling back to '$UpstreamRef'."
    }

    Script:Sync-BranchWithUpstream `
        -GitCommand $git `
        -CurrentBranch $currentBranch `
        -RepositoryPath $RepositoryPath `
        -Mode $Mode `
        -UpstreamRef $UpstreamRef `
        -Remote $Remote `
        -PassThru:$PassThru | ForEach-Object { if ($PassThru) { $_ } }

    if ($Push.IsPresent) {
        $pushRemote = ($UpstreamRef.Split('/'))[0]
        $setUpstream = -not $hadConfiguredUpstream

        Push-Branch `
            -GitCommand $git `
            -RepositoryPath $RepositoryPath `
            -CurrentBranch $currentBranch `
            -PushRemote $pushRemote `
            -SetUpstream:$setUpstream `
            -PassThru:$PassThru | ForEach-Object { if ($PassThru) { $_ } }
    }

    if ($PassThru) {
        return [GitSyncResult]::new(@{
                Branch      = $currentBranch
                UpstreamRef = $UpstreamRef
                Mode        = $Mode
                Fetched     = -not $NoFetch
                Pushed      = [bool] $Push
                Repository  = $RepositoryPath
            })
    }
}

function Script:Sync-BranchWithUpstream {
    [CmdletBinding(SupportsShouldProcess)]
    [OutputType([string])]
    param (
        [Parameter(Mandatory)]
        [System.Management.Automation.CommandInfo] $GitCommand,

        [Parameter(Mandatory)]
        [ValidateNotNullOrEmpty()]
        [string] $CurrentBranch,

        [ValidateNotNullOrEmpty()]
        [string] $RepositoryPath = (Get-Location).Path,

        [ValidateNotNullOrEmpty()]
        [string] $UpstreamRef,

        [ValidateNotNullOrEmpty()]
        [string] $Remote = 'origin',

        [ValidateSet('FFOnly', 'Merge', 'Rebase')]
        [string] $Mode = 'Merge',

        [switch] $PassThru
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

        $gitArgs += @(
            'pull'
            $modeMap[$Mode]
            $up.Remote
            $up.Branch
        )

    } else {
        $gitArgs += @(
            'merge'
            $UpstreamRef
        )
    }

    $cmdPreview = "git $($gitArgs -join ' ')"
    if (-not $PSCmdlet.ShouldProcess("$RepositoryPath", $cmdPreview)) { return }

    Write-Verbose "🔀 Sync mode: $Mode | Target: $UpstreamRef | Branch: $CurrentBranch"
    $output = & $GitCommand @gitArgs 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "❌ git sync failed (exit $LASTEXITCODE). Command: $cmdPreview`n$output"
    }

    if ($PassThru) { $output }
}

function Resolve-GitUpstreamRef {
    [CmdletBinding()]
    [OutputType('Git.UpstreamRef', [string[]])]
    param(
        [Parameter(
            Mandatory,
            Position = 0,
            ValueFromPipeline,
            ValueFromPipelineByPropertyName
        )]
        [ValidateNotNullOrEmpty()]
        [string] $UpstreamRef,

        [ValidateNotNullOrEmpty()]
        [string] $DefaultBranch,

        [ValidateNotNullOrEmpty()]
        [string] $DefaultRemote,

        [switch] $AsArray
    )

    process {
        $parts = $UpstreamRef.Split('/', 2, [System.StringSplitOptions]::None)

        if ($parts.Count -eq 2 -and [string]::IsNullOrWhiteSpace($parts[0]) -eq $false) {
            $remote = $parts[0]
            $branch = $parts[1]
        } else {
            if (-not $DefaultRemote -or -not $DefaultBranch) {
                throw @(
                    "The value '$UpstreamRef' does not contain a remote separator ('/').",
                    'Provide -DefaultRemote and -DefaultBranch.'
                ) -join ' '
            }
            $remote = $DefaultRemote
            $branch = $DefaultBranch
        }

        if ($AsArray) {
            return @($remote, $branch)
        }

        # Preferred, self-describing shape
        [PSCustomObject]@{
            PSTypeName = 'Git.UpstreamRef'
            Remote     = $remote
            Branch     = $branch
        }
    }
}
