#Requires -Version 7.4
Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'

# Repository-wide defaults and synchronization primitives for the lightweight script logger. These
# are stored in the script scope so they are shared by all functions/classes defined in this module
# but are not exported to callers as top-level variables.

<#
.SYNOPSIS
    Default directory where log files are written when no explicit directory is provided to the
    logger. We compute this as the repository root (two levels up from this module file) + '/logs'.
    Consumers may override this per-logger via `KalmScriptLogOptions.Directory`.
#>
$script:KalmLoggerDefaultDirectory = Join-Path -Path (
    Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) -ChildPath 'logs'
<#
.SYNOPSIS
    A lightweight lock object used with [System.Threading.Monitor] to serialize concurrent
    writes/rotations to the same log files. Using a dedicated object avoids locking on primitive or
    global values.
#>
$script:KalmLoggerSyncRoot = [object]::new()
<#
.SYNOPSIS
    Holds the active logger instance (if any). Static helper methods in `KalmScriptLogger` read or
    update this value to implement global convenience behavior (e.g., `GetCurrent()`, `Reset()`, and
    `LogIfConfigured()`). Storing it in script scope keeps it process-local and avoids accidental
    export to callers.
#>
$script:KalmLoggerCurrent = $null

enum KalmLogLevel {
    Debug = 10
    Info = 20
    Warning = 30
    Error = 40
    Critical = 50
}

class KalmScriptLogOptions {
    [string] $Name
    [string] $Directory
    [int64]  $MaxFileSizeBytes = 5MB
    [int]    $MaxArchivedFiles = 5

    KalmScriptLogOptions([string] $name) {
        if ([string]::IsNullOrWhiteSpace($name)) {
            throw [System.ArgumentException]::new('Logger name cannot be null or empty.', 'name')
        }
        $this.Name = [KalmScriptLogger]::SanitizeName($name)
    }
}

class KalmScriptLogger {
    [string] $Name
    [string] $Directory
    [string] $BaseFileName
    [string] $FilePath
    [int64]  $MaxFileSizeBytes
    [int]    $MaxArchivedFiles
    [string] $RunId

    KalmScriptLogger([KalmScriptLogOptions] $options) {
        if (-not $options) {
            throw [System.ArgumentNullException]::new('options')
        }

        $this.Name = [KalmScriptLogger]::SanitizeName($options.Name)
        $this.Directory = [KalmScriptLogger]::ResolveDirectory($options.Directory)
        $this.BaseFileName = "$($this.Name).log"
        $this.FilePath = Join-Path -Path $this.Directory -ChildPath $this.BaseFileName
        $this.MaxFileSizeBytes = if ($options.MaxFileSizeBytes -gt 0) { $options.MaxFileSizeBytes } else { 5MB }
        $this.MaxArchivedFiles = if ($options.MaxArchivedFiles -gt 0) { $options.MaxArchivedFiles } else { 5 }
        $this.RunId = [guid]::NewGuid().ToString()

        $this.EnsureDirectory()
    }

    ############################################################
    # Encapsulated defaults & overrides
    #
    # These hidden static overrides allow tests or consumers to replace the
    # module-level defaults (directory, sync root, current instance) without
    # reaching into script scope. When set they are used in preference to the
    # script-scoped values; setters also keep the script-scoped values in sync
    # for backwards compatibility with any callers that still reference them.
    ############################################################

    static hidden [string] $DefaultDirectoryOverride
    static hidden [object] $SyncRootOverride
    static hidden [KalmScriptLogger] $CurrentOverride

    static hidden [void] SetDefaultDirectory([string] $dir) {
        [KalmScriptLogger]::DefaultDirectoryOverride = $dir
        if ($dir) { $script:KalmLoggerDefaultDirectory = $dir }
    }

    static hidden [string] GetDefaultDirectory() {
        if ([KalmScriptLogger]::DefaultDirectoryOverride) { return [KalmScriptLogger]::DefaultDirectoryOverride }
        return $script:KalmLoggerDefaultDirectory
    }

    static hidden [void] SetSyncRoot([object] $obj) {
        [KalmScriptLogger]::SyncRootOverride = $obj
        if ($obj) { $script:KalmLoggerSyncRoot = $obj }
    }

    static hidden [object] GetSyncRoot() {
        if ([KalmScriptLogger]::SyncRootOverride) { return [KalmScriptLogger]::SyncRootOverride }
        return $script:KalmLoggerSyncRoot
    }

    static [void] SetCurrent([KalmScriptLogger] $logger) {
        [KalmScriptLogger]::CurrentOverride = $logger
        $script:KalmLoggerCurrent = $logger
    }

    static [KalmScriptLogger] GetCurrent() {
        if ([KalmScriptLogger]::CurrentOverride) { return [KalmScriptLogger]::CurrentOverride }
        return $script:KalmLoggerCurrent
    }


    static [KalmScriptLogger] Initialize([KalmScriptLogOptions] $options) {
        $logger = [KalmScriptLogger]::new($options)
        [KalmScriptLogger]::SetCurrent($logger)
        return $logger
    }

    static [KalmScriptLogger] Start([string] $name, [string] $directory) {
        $opts = [KalmScriptLogOptions]::new($name)
        if ($directory) { $opts.Directory = $directory }
        return [KalmScriptLogger]::Initialize($opts)
    }

    static [void] Reset() {
        [KalmScriptLogger]::SetCurrent($null)
    }

    

    static [void] LogIfConfigured([KalmLogLevel] $level, [string] $message, [string] $category) {
        $logger = [KalmScriptLogger]::GetCurrent()
        if ($logger) {
            $logger.Log($level, $message, $category)
        }
    }

    static [string] SanitizeName([string] $name) {
        $trimmed = $name.Trim()
        $safe = [System.Text.RegularExpressions.Regex]::Replace($trimmed, '[<>:"/\\|?*]', '-')
        if ([string]::IsNullOrWhiteSpace($safe)) {
            throw [System.ArgumentException]::new('Logger name must contain valid characters.', 'name')
        }
        return $safe
    }

    static hidden [string] ResolveDirectory([string] $directory) {
        $target = if ([string]::IsNullOrWhiteSpace($directory)) {
            [KalmScriptLogger]::GetDefaultDirectory()
        }
        else {
            $directory
        }

        if (-not $target) {
            $target = Join-Path -Path ([System.IO.Path]::GetTempPath()) -ChildPath 'kalm-logs'
        }

        return [System.IO.Path]::GetFullPath($target)
    }

    hidden [void] EnsureDirectory() {
        if (-not (Test-Path -LiteralPath $this.Directory)) {
            New-Item -ItemType Directory -Path $this.Directory -Force | Out-Null
        }
        # Create the log file if it doesn't exist so it's immediately available
        if (-not (Test-Path -LiteralPath $this.FilePath)) {
            New-Item -ItemType File -Path $this.FilePath -Force | Out-Null
        }
    }

    [void] Log([KalmLogLevel] $level, [string] $message, [string] $category) {
        if ([string]::IsNullOrWhiteSpace($message)) { return }
        $timestamp = (Get-Date).ToUniversalTime().ToString('o')
        $entry = '{0} [{1}] ({2}) [RunId:{3}] {4}' -f $timestamp, $level, $this.Name, $this.RunId, $message.Trim()
        if ($category) {
            $entry = "$entry [$category]"
        }
        $this.WriteEntry($entry)
    }

    [void] LogDebug([string] $message, [string] $category) { $this.Log([KalmLogLevel]::Debug, $message, $category) }
    [void] LogInfo([string] $message, [string] $category) { $this.Log([KalmLogLevel]::Info, $message, $category) }
    [void] LogWarning([string] $message, [string] $category) { $this.Log([KalmLogLevel]::Warning, $message, $category) }
    [void] LogError([string] $message, [string] $category) { $this.Log([KalmLogLevel]::Error, $message, $category) }
    [void] LogCritical([string] $message, [string] $category) { $this.Log([KalmLogLevel]::Critical, $message, $category) }

    hidden [void] WriteEntry([string] $entry) {
        [System.Threading.Monitor]::Enter([KalmScriptLogger]::GetSyncRoot())
        try {
            $this.RotateIfNeeded()
            Add-Content -Path $this.FilePath -Value $entry -Encoding UTF8
        }
        finally {
            [System.Threading.Monitor]::Exit([KalmScriptLogger]::GetSyncRoot())
        }
    }

    hidden [void] RotateIfNeeded() {
        if (-not (Test-Path -LiteralPath $this.FilePath)) { return }

        $length = (Get-Item -LiteralPath $this.FilePath).Length
        if ($length -lt $this.MaxFileSizeBytes) { return }

        for ($index = $this.MaxArchivedFiles; $index -ge 1; $index--) {
            $source = if ($index -eq 1) {
                $this.FilePath
            }
            else {
                Join-Path -Path $this.Directory -ChildPath ('{0}.{1}' -f $this.BaseFileName, ($index - 1))
            }

            $destination = Join-Path -Path $this.Directory -ChildPath ('{0}.{1}' -f $this.BaseFileName, $index)

            if (-not (Test-Path -LiteralPath $source)) { continue }
            if (Test-Path -LiteralPath $destination) {
                Remove-Item -LiteralPath $destination -Force -ErrorAction SilentlyContinue
            }

            Move-Item -LiteralPath $source -Destination $destination -Force
        }
    }
}
