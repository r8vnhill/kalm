# Keen.Git.psm1 - Module Entry Point for Keen Git Utilities
# 
# This script dynamically loads all internal and public function scripts from the
# 'internal/' and 'public/' directories.
# It enables modular, extensible, and maintainable organization of the module's
# functionality.

# ────────────────────────────────────────────────────────────────────────────────────────
# 🛠️ Load Internal (Private) Functions
# These scripts contain helper logic or internal utilities that are NOT exposed
# to users via exported functions or aliases.
# Used internally by public commands.
# ────────────────────────────────────────────────────────────────────────────────────────

$internalDir = Join-Path -Path $PSScriptRoot -ChildPath 'internal'

# Check if the internal directory exists before attempting to load
if (Test-Path $internalDir) {
    $internalScripts = Get-ChildItem -Path $internalDir -Filter '*.ps1' -File

    # Load internal scripts in alphabetical order to ensure predictable resolution
    foreach ($script in $internalScripts | Sort-Object Name) {
        try {
            . $script.FullName
            Write-Verbose "✅ Imported internal: $($script.Name)"
        } catch {
            Write-Warning "⚠️ Failed to import internal script $($script.Name): $_"
        }
    }
}

# ────────────────────────────────────────────────────────────────────────────────────────
# 📣 Load Public (Exported) Functions
# These scripts define the public interface of the module.
# Their functions should be explicitly listed in the module manifest
# (`FunctionsToExport`) and may have corresponding aliases in `AliasesToExport`.
# ────────────────────────────────────────────────────────────────────────────────────────

$publicDir = Join-Path -Path $PSScriptRoot -ChildPath 'public'

# Get all public script files and stop on error if directory is missing or unreadable
$publicScripts = Get-ChildItem -Path $publicDir -Filter '*.ps1' -File -ErrorAction Stop

# Load public scripts in alphabetical order for consistency
foreach ($script in $publicScripts | Sort-Object Name) {
    try {
        . $script.FullName
        Write-Verbose "✅ Imported public: $($script.Name)"
    } catch {
        Write-Warning "⚠️ Failed to import public script $($script.Name): $_"
    }
}
