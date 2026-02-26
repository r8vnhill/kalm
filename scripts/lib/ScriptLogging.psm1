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

##
# Severity levels used across the Kalm script logger API.
#
# These named levels are intentionally simple (no explicit numeric values are
# required) — callers should treat them as ordered severity buckets from
# low-to-high (Debug -> Critical). The levels are used both for human-readable
# output in the log files and for sinks that may switch behavior depending on
# the severity (for example: console vs error sinks).
##
enum KalmLogLevel {
    Debug
    Info
    Warning
    Error
    Critical
}

##
# Lightweight options object passed to create or initialize a logger. Tests and
# callers should construct this with a human-friendly logger name and may set
# an explicit Directory, MaxFileSizeBytes, and MaxArchivedFiles to tune
# rotation behaviour for the current process (tests commonly use small
# thresholds to force rotation deterministically).
##
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

##
# KalmScriptLogger
#
# Single-file, process-local script logger used by repository automation. Key
# design points:
# - Module-level defaults (directory, sync root, current logger) are stored in
#   script scope for compatibility with existing code. Prefer the exposed
#   Set*/Get* helpers to mutate these values in tests instead of reaching into
#   `$script:` directly.
# - The logger supports additional sinks (callbacks) so tests can capture
#   structured log records without parsing text files.
# - Rotation is file-size based and keeps a configurable number of archived
#   files (MaxArchivedFiles).
##
class KalmScriptLogger {
    [string] $Name
    [string] $Directory
    [string] $BaseFileName
    [string] $FilePath
    [int64]  $MaxFileSizeBytes
    [int]    $MaxArchivedFiles
    [string] $RunId
    hidden [System.Collections.Generic.List[ScriptBlock]] $AdditionalSinks

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
        $this.AdditionalSinks = [System.Collections.Generic.List[ScriptBlock]]::new()

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
        if ($dir) { $script:KalmLoggerDefaultDirectory = $dir }
    }

    static hidden [string] GetDefaultDirectory() {
        return $script:KalmLoggerDefaultDirectory
    }

    static hidden [void] SetSyncRoot([object] $obj) {
        if ($obj) { $script:KalmLoggerSyncRoot = $obj }
    }

    static hidden [object] GetSyncRoot() {
        return $script:KalmLoggerSyncRoot
    }

    static [void] SetCurrent([KalmScriptLogger] $logger) {
        $script:KalmLoggerCurrent = $logger
    }

    static [KalmScriptLogger] GetCurrent() {
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

    [void] AddSink([ScriptBlock] $Sink) {
        if (-not $Sink) {
            throw [System.ArgumentNullException]::new('Sink')
        }
        [System.Threading.Monitor]::Enter([KalmScriptLogger]::GetSyncRoot())
        try {
            $this.AdditionalSinks.Add($Sink) | Out-Null
        }
        finally {
            [System.Threading.Monitor]::Exit([KalmScriptLogger]::GetSyncRoot())
        }
    }

    [void] ClearSinks() {
        [System.Threading.Monitor]::Enter([KalmScriptLogger]::GetSyncRoot())
        try {
            $this.AdditionalSinks.Clear()
        }
        finally {
            [System.Threading.Monitor]::Exit([KalmScriptLogger]::GetSyncRoot())
        }
    }

    [void] AddConsoleSink() {
        $consoleSink = {
            param($record, $formatted)
            switch ($record.Level) {
                ([KalmLogLevel]::Debug)   { Write-Verbose $formatted }
                ([KalmLogLevel]::Info)    { Write-Information -MessageData $formatted -Tags @('KalmLogger', $record.Logger) }
                ([KalmLogLevel]::Warning) { Write-Warning $formatted }
                ([KalmLogLevel]::Error)   { Write-Error -Message $formatted -Category NotSpecified -ErrorAction Continue }
                ([KalmLogLevel]::Critical){ Write-Error -Message $formatted -Category NotSpecified -ErrorAction Continue }
                default                   { Write-Information -MessageData $formatted }
            }
        }

        $this.AddSink($consoleSink)
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
        $trimmed = $message.Trim()
        $entry = '{0} [{1}] ({2}) [RunId:{3}] {4}' -f $timestamp, $level, $this.Name, $this.RunId, $trimmed
        if ($category) {
            $entry = "$entry [$category]"
        }
        $record = [pscustomobject]@{
            Logger    = $this.Name
            RunId     = $this.RunId
            Timestamp = $timestamp
            Level     = $level
            Message   = $trimmed
            Category  = $category
        }
        $this.WriteEntry($entry)
        $this.WriteToAdditionalSinks($record, $entry)
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

    hidden [void] WriteToAdditionalSinks([psobject] $record, [string] $formatted) {
        if (-not $this.AdditionalSinks -or $this.AdditionalSinks.Count -eq 0) { return }

        $snapshot = $null
        [System.Threading.Monitor]::Enter([KalmScriptLogger]::GetSyncRoot())
        try {
            $snapshot = $this.AdditionalSinks.ToArray()
        }
        finally {
            [System.Threading.Monitor]::Exit([KalmScriptLogger]::GetSyncRoot())
        }

        foreach ($sink in $snapshot) {
            try {
                & $sink $record $formatted
            }
            catch {
                Write-Verbose ("Logger sink failed: {0}" -f $_.Exception.Message)
            }
        }
    }
}

