function Resolve-GitCommand {
    [CmdletBinding()]
    [OutputType([System.Management.Automation.CommandInfo])]
    param(
        [Parameter(ValueFromPipelineByPropertyName)]
        [System.Management.Automation.CommandInfo]
        $GitCommand,
        [Parameter(ValueFromPipelineByPropertyName)]
        [string]
        $Git,
        [bool]
        $ThrowIfMissing = $true,
        [switch]
        $Verify,
        [switch]
        $NoCache
    )

    begin {
        # TODO: Implement caching logic to avoid repeated command resolution.
    }

    process {
        $resolved = $GitCommand
        if (-not $resolved -and $Git) {
            $resolved = Get-Command -Name $Git -ErrorAction SilentlyContinue
        }
        if (-not $resolved) {
            if (-not $NoCache -and $cached) {
                $resolved = $cached
            } else {
                $resolved = Get-Command -Name 'git' -ErrorAction SilentlyContinue
            }
        }
        if (-not $resolved) {
            if ($ThrowIfMissing) {
                throw '❌ Git not found.'
            }
            return $null
        }
        if ($Verify) {
            $null = & $resolved --version 2>$null
            if ($LASTEXITCODE -ne 0) {
                if ($ThrowIfMissing) {
                    throw @(
                        "Found Git at '$($resolved.Source)'",
                        "but it failed to execute (--version exit $LASTEXITCODE)."
                    ) -join "`n"
                }
                return $null
            }
        }

        # Update cache
        if (-not $NoCache) { $script:__ResolvedGitCommand = $resolved }

        return $resolved
    }
}

function Script:Get-GitCommandCached {
    [CmdletBinding()]
    [OutputType([System.Management.Automation.CommandInfo])]
    param(
        [switch] $NoCache
    )

    if ($NoCache) { return $null }

    $cached = $script:__ResolvedGitCommand
    if ($cached -is [System.Management.Automation.CommandInfo]) {
        return $cached
    }

    # Explicitly return $null when there is no valid cache.
    return $null
}
