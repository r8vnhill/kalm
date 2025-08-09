#Requires -Version 7.0
Set-StrictMode -Version Latest
Write-Verbose "Initializing Keen.Git module..."

# Small helper that ONLY returns files to load. It does NOT dot‑source.
function Get-ModuleScriptFiles {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [ValidateSet('internal', 'public')]
        [string] $Subfolder
    )

    $root = Join-Path $PSScriptRoot $Subfolder
    if (-not (Test-Path -LiteralPath $root -PathType Container)) {
        Write-Verbose "No '$Subfolder' folder at $root (skipping)."
        return @()  # keep callers simple
    }

    Get-ChildItem -Path $root -Recurse -File -Filter '*.ps1' |
        Sort-Object FullName
}

# Load both trees with the same logic; keep dot‑sourcing in MODULE scope.
foreach ($file in (Get-ModuleScriptFiles -Subfolder 'internal')) {
    try {
        Write-Verbose "Dot-sourcing (internal): $($file.FullName)"
        $null = . $file.FullName
    } catch {
        throw "Error importing internal script '{0}': {1}" `
            -f $($file.FullName), $($_.Exception.Message)
    }
}

foreach ($file in (Get-ModuleScriptFiles -Subfolder 'public')) {
    try {
        Write-Verbose "Dot-sourcing (public): $($file.FullName)"
        $null = . $file.FullName
    } catch {
        throw "Error importing public script '$($file.FullName)': $($_.Exception.Message)"
    }
}
