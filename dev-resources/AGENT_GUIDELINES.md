# Local agent guidelines (workspace reminder)

This file contains small runtime reminders for automated agents and contributors working in interactive sessions. It is intentionally lightweight and intended to complement the canonical `.github/copilot-instructions.md` file.

- When the active terminal/session is already PowerShell (pwsh), do NOT prefix commands with `pwsh -NoProfile -ExecutionPolicy Bypass`.
  - Use the wrapper only when you are not sure the target shell is pwsh (for example, when launching a new shell on a remote system or invoking from another shell type).
  - Rationale: avoid redundant wrappers, reduce quoting/escaping issues, and respect the interactive session environment.
- Default to TDD: write/adjust a failing test that expresses the expected behavior first, then implement or modify code to make it pass. Only skip this when the user explicitly opts out.

Examples:
- Good (when already in pwsh):
  ```powershell
  ./scripts/git/Sync-RepoAndWiki.ps1 -IncludeRootChanges -RootCommitMessage "msg"
  ```

- Use wrapper (when uncertain):
  ```powershell
  pwsh -NoProfile -ExecutionPolicy Bypass -File ./scripts/git/Sync-RepoAndWiki.ps1 -IncludeRootChanges -RootCommitMessage "msg"
  ```

This document is a workspace-local reminder and may be updated with additional runtime tips. It does not replace the repository's main agent guidance at `.github/copilot-instructions.md`.

---

## Handling diverged submodules/wiki (quick examples)
If a submodule (commonly `wiki/`) cannot be fast-forwarded, choose a strategy:

- Merge (auto-merge remote into local; creates merge commit):

```powershell
./scripts/git/Sync-WikiOnly.ps1 -PullStrategy merge -SkipPush -WikiCommitMessage "chore(wiki): merge remote"
```

- Rebase (replay local commits on top of remote; keeps linear history):

```powershell
./scripts/git/Sync-WikiOnly.ps1 -PullStrategy rebase -SkipPush -WikiCommitMessage "chore(wiki): rebase onto remote"
```

Use `ff-only` (the default) in automation or CI to avoid unexpected history changes; opt-in to `merge`/`rebase` interactively when you understand the divergence.
