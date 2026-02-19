# DryRunState module

Lightweight module that provides a process-wide "dry-run" flag for scripts in this repository.

Location
- `scripts/lib/DryRunState.psm1`

Supported PowerShell
- PowerShell 7.4+ (module uses thread-safe Interlocked operations)

Imported symbols
- Set-KalmDryRun ([bool]) — Set the dry-run flag (true = dry-run)
- Get-KalmDryRun () -> [bool] — Read the current dry-run value
- Join-QuotedArgs ([string[]]) -> [string] — Quote and join args for Gradle `--args=` forwarding (dot-sourced helper in `scripts/lib/Join-QuotedArgs.ps1`)
- Find-JsonObjectLine ([string[]]) -> [string|null] — Find the last valid JSON object line in mixed command output (dot-sourced helper in `scripts/lib/Find-JsonObjectLine.ps1`)

Notes
- `Reset-KalmDryRun` exists as an internal test helper but is not exported by the module's public API.
- The repository loader `scripts/git/GitSync.psm1` imports this module automatically when present.

Examples

Import the module from the repo root:

```powershell
# From repository root
Import-Module ./scripts/lib/DryRunState.psm1 -Force

Set-KalmDryRun $true
if (Get-KalmDryRun) { Write-Output 'dry-run: skipping side-effects' }
Set-KalmDryRun $false
```

Run tests (Pester helper):

```powershell
pwsh -NoProfile -Command "./scripts/testing/Invoke-PesterWithConfig.ps1"
```

Why use the module
- Using a module makes the dry-run API explicit and avoids accidental global state collisions or repeated dot-sourcing.
