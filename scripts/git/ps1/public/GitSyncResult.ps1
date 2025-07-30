<#
.SYNOPSIS
Lightweight result carrier for a Git sync workflow.

.DESCRIPTION
`GitSyncResult` encapsulates the outcome of a sync operation (branch, upstream, mode, and whether fetch/push happened).
It is designed to be simple to log, pipe, or return from orchestration functions such as `Invoke-GitSync`.

.NOTES
- The re-definition guard prevents "Cannot redefine class" errors when a script or module is re-imported.
  In PowerShell, classes are immutable once defined in a session.
  The guard checks if the type already exists and only defines it once.
- Constructors:
  * Hashtable/IDictionary-based: convenient when building from a computed map.
  * Positional: explicit construction when all values are available.
- `ToString()` produces a compact, log-friendly summary.

.EXAMPLE
# Construct via positional constructor
$result = [GitSyncResult]::new(
    'feature/foo',        # Branch
    'origin/feature/foo', # UpstreamRef
    'Rebase',             # Mode
    $true,                # Fetched
    $false,               # Pushed
    'C:\src\my-repo'      # Repository
)

.EXAMPLE
# Construct from a hashtable (keys are optional; missing ones remain default)
$h = @{
  Branch      = 'main'
  UpstreamRef = 'origin/main'
  Mode        = 'FFOnly'
  Fetched     = $true
  Repository  = '/repos/app'
}
$result = [GitSyncResult]::new($h)
#>

# Guard to avoid "Cannot redefine class" on re-imports.
# In an interactive session or when a module is reloaded, attempting to redefine a
# class throws.
# This check makes the class definition idempotent for the session.
if (-not ('GitSyncResult' -as [type])) {

    class GitSyncResult {
        # ----------------------------
        # Public data (simple DTO)
        # ----------------------------

        # The local branch name associated with the sync (e.g., 'main', 'feature/x').
        [string] $Branch

        # The upstream reference for the branch (e.g., 'origin/main').
        [string] $UpstreamRef

        # The sync mode used (e.g., 'FFOnly', 'Rebase', 'Merge').
        [string] $Mode

        # Indicates whether a fetch actually occurred during the sync.
        [bool]   $Fetched

        # Indicates whether a push actually occurred during the sync.
        [bool]   $Pushed

        # Physical path of the repository where the sync was performed.
        [string] $Repository

        # -------------------------------------------
        # Constructor: IDictionary-based initializer
        # -------------------------------------------
        # Accept any IDictionary so callers can pass Hashtable, OrderedDictionary,
        # or generic dictionaries. Values are assigned only if present.
        GitSyncResult([System.Collections.IDictionary] $Info) {

            if ($null -eq $Info) {
                return
            }

            # Assign only keys that exist; ignore extras to stay forward-compatible.
            if ($Info.Contains('Branch')) { 
                $this.Branch = [string]$Info['Branch'] 
            }
            if ($Info.Contains('UpstreamRef')) { 
                $this.UpstreamRef = [string]$Info['UpstreamRef'] 
            }
            if ($Info.Contains('Mode')) { 
                $this.Mode = [string]$Info['Mode'] 
            }
            if ($Info.Contains('Fetched')) { 
                $this.Fetched = [bool]  $Info['Fetched'] 
            }
            if ($Info.Contains('Pushed')) { 
                $this.Pushed = [bool]  $Info['Pushed'] 
            }
            if ($Info.Contains('Repository')) { 
                $this.Repository = [string]$Info['Repository'] 
            }
        }

        # ------------------------------------------------
        # Constructor: explicit positional initialization
        # ------------------------------------------------
        GitSyncResult(
            [string] $branch,
            [string] $upstreamRef,
            [string] $mode,
            [bool]   $fetched,
            [bool]   $pushed,
            [string] $repository
        ) {
            # Direct assignment keeps construction simple and explicit.
            $this.Branch = $branch
            $this.UpstreamRef = $upstreamRef
            $this.Mode = $mode
            $this.Fetched = $fetched
            $this.Pushed = $pushed
            $this.Repository = $repository
        }

        # ----------------------------
        # Human-readable summary
        # ----------------------------
        [string] ToString() {
            # Build a compact, stable summary suitable for logs and verbose output.
            $parts = @(
                "Repo=$($this.Repository)"
                "Branch=$($this.Branch)"
                "Upstream=$($this.UpstreamRef)"
                "Mode=$($this.Mode)"
                "Fetched=$($this.Fetched)"
                "Pushed=$($this.Pushed)"
            )
            return ($parts -join ', ')
        }
    }
}
