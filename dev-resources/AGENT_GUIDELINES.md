# Local agent guidelines (workspace reminder)

This file contains small runtime reminders for automated agents and contributors working in interactive sessions. It is intentionally lightweight and intended to complement the canonical `.github/copilot-instructions.md` file.

- When the active terminal/session is already PowerShell (pwsh), do NOT prefix commands with `pwsh -NoProfile -ExecutionPolicy Bypass`.
  - Use the wrapper only when you are not sure the target shell is pwsh (for example, when launching a new shell on a remote system or invoking from another shell type).
  - Rationale: avoid redundant wrappers, reduce quoting/escaping issues, and respect the interactive session environment.

Examples:
- Good (when already in pwsh):
  ```powershell
  ./scripts/Sync-RepoAndWiki.ps1 -IncludeRootChanges -RootCommitMessage "msg"
  ```

- Use wrapper (when uncertain):
  ```powershell
  pwsh -NoProfile -ExecutionPolicy Bypass -File ./scripts/Sync-RepoAndWiki.ps1 -IncludeRootChanges -RootCommitMessage "msg"
  ```

This document is a workspace-local reminder and may be updated with additional runtime tips. It does not replace the repository's main agent guidance at `.github/copilot-instructions.md`.
