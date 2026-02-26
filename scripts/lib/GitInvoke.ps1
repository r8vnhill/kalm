function Invoke-Git {
    [CmdletBinding(SupportsShouldProcess = $true)]
    param(
        [Parameter(Mandatory)] [string[]] $GitArgs,
        [Parameter()] [string] $WorkingDirectory = (Get-Location).Path,
        [Parameter()] [string] $Description,
        [Parameter()] [switch] $Quiet,
        [Parameter()] [switch] $NoThrow,
        [Parameter()] [switch] $CaptureOutput,
        [Parameter()] [switch] $ReturnExitCode
    )

    # Always ask ShouldProcess for safety so -WhatIf prevents any git invocation
    $exe = 'git'
    $argsList = @('-C', $WorkingDirectory) + $GitArgs
    $commandText = "git $($GitArgs -join ' ') (cwd: $WorkingDirectory)"
    if ($Description) { $commandText = "$commandText - $Description" }
    [KalmScriptLogger]::LogIfConfigured([KalmLogLevel]::Debug, "Prepared command: $commandText", 'GitInvoke')

    # If a top-level caller set the dry-run flag via the singleton, avoid any git invocation
    if ((Get-Command -Name Get-KalmDryRun -ErrorAction SilentlyContinue) -and (Get-KalmDryRun)) {
        if ($Description -and -not $Quiet) { Write-Information "[WhatIf] $Description" }
        [KalmScriptLogger]::LogIfConfigured([KalmLogLevel]::Info, "[DryRun] $commandText", 'GitInvoke')
        return $null
    }

    $target = "$($GitArgs -join ' ') in $WorkingDirectory"
    if (-not $PSCmdlet.ShouldProcess($target, 'git')) {
        if ($Description -and -not $Quiet) { Write-Information "[WhatIf] $Description" }
        [KalmScriptLogger]::LogIfConfigured([KalmLogLevel]::Info, "[WhatIf] Skipping command: $commandText", 'GitInvoke')
        return $null
    }

    if ($Description -and -not $Quiet) { Write-Information $Description }
    Write-Verbose ('Executing: {0}' -f ($argsList -join ' '))
    [KalmScriptLogger]::LogIfConfigured([KalmLogLevel]::Debug, "Executing command: $commandText", 'GitInvoke')

    if ($CaptureOutput) {
        $output = & $exe @argsList 2>&1
        $code = $LASTEXITCODE
        if ($code -ne 0 -and -not $NoThrow) { throw "git command failed ($code): $($argsList -join ' ') in '$WorkingDirectory'`nOutput: $($output -join "`n")" }
        if ($null -eq $output) { return [System.Array]::Empty[object]() }
        return , $output
    }
    else {
        & $exe @argsList
        $code = $LASTEXITCODE
        if ($code -ne 0 -and -not $NoThrow) { throw "git command failed ($code): $($argsList -join ' ') in '$WorkingDirectory'" }
        if ($ReturnExitCode) { return $code }
        # By default, avoid emitting numeric exit codes to caller output to keep -WhatIf and normal runs tidy.
        return $null
    }
}
