# KALM Scripts

PowerShell automation tools for Git, Gradle, and project maintenance workflows.

## Recommended: Use Docker for Gradle Tasks

For maximum reproducibility, run Gradle tasks using the containerized environment:

```bash
# Run any Gradle task
docker compose run --rm kalm ./gradlew verifyAll

# Run with arguments
docker compose run --rm kalm ./gradlew :tools:test --tests "FullyQualifiedTestName"
```

This ensures consistent JDK version, dependencies, and environment across all contributors and CI.

See [dev-resources/CONTAINERS_AND_ENVIRONMENTS.md](../dev-resources/CONTAINERS_AND_ENVIRONMENTS.md) for more details.

## Requirements

- **PowerShell 7.4+** (cross-platform)
- **Git 2.20+**
- **JDK 22 or newer** (for Gradle scripts; JDK 25 not yet supported by Detekt)

For a deeper, maintainer-focused overview of how repository scripts are structured (logging, strict mode, dry-run
behavior, testing, and static analysis), see the wiki page **“PowerShell Scripting Practices (KALM)”** under the `wiki/`
submodule.

## Module Loading Guidelines

For scripts in this folder, prefer the following patterns:

- **`using module`** shall be used when types are required at parse time (for example, classes or enums defined in a
  `.psm1`, such as `KalmScriptLogger` in `./lib/ScriptLogging.psm1`). It must appear at the very top of the file and
  cannot be conditional.
- **`Import-Module`** shall be used for runtime and/or conditional loading (for example, inside functions, within
  `try/catch`, or when `-Scope`, `-Prefix`, or selective import is required). This mechanism loads functions and cmdlets
  but does not make PowerShell classes available at parse time.
- **`#Requires -Modules`** shall be used when a script cannot execute without a module and early failure with a clear
  diagnostic error is required. If parse-time access to classes from that module is also required, `#Requires -Modules`
  must be combined with `using module` at the top of the file.

## Logging

All entry-point scripts initialize the `KalmScriptLogger` class from `scripts/lib/ScriptLogging.psm1`. Logs are written
under `logs/<script>.log`, rotated at ~5 MB (five archives kept). Include the module via `using module` at the top of
any new script and emit log lines as needed:

```powershell
#Requires -Version 7.4
using module ./lib/ScriptLogging.psm1

$logger = [KalmScriptLogger]::Start('MyScript', $null)
$logger.LogInfo('Script started.', 'Startup')
[KalmScriptLogger]::LogIfConfigured([KalmLogLevel]::Debug, 'Detailed trace message', 'Diagnostics')
```

Logging is enabled even in `-WhatIf` scenarios so CI/local runs share a consistent audit trail.

## Git & Submodule Sync

### GitSync.psm1 Module

Reusable functions for Git operations (imported automatically by sync scripts):

| Function                      | Purpose                                            |
|-------------------------------|----------------------------------------------------|
| `Invoke-Git`                  | Safe git command wrapper with error handling       |
| `Get-GitCurrentBranch`        | Get current branch name for a repository           |
| `Test-GitClean`               | Check if working directory has uncommitted changes |
| `Get-GitSubmodules`           | Parse `.gitmodules` and return submodule objects   |
| `Sync-GitSubmodule`           | Pull, commit, and push a submodule                 |
| `Update-GitSubmodulePointers` | Stage and commit pointer updates in parent repo    |

### Sync-RepoAndWiki.ps1

Full repository sync: main repo and all submodules (pull → commit → push).

#### Usage

```powershell
# Full sync (fetch, commit, push everything)
.\scripts/git/Sync-RepoAndWiki.ps1

# Preview without making changes
.\scripts/git/Sync-RepoAndWiki.ps1 -WhatIf

# Sync only submodules (skip main repo)
.\scripts/git/Sync-RepoAndWiki.ps1 -SubmoduleOnly

# Commit local changes without fetching
.\scripts/git/Sync-RepoAndWiki.ps1 -SkipPull

# Fetch and commit without pushing
.\scripts/git/Sync-RepoAndWiki.ps1 -SkipPush

# Custom remote
.\scripts/git/Sync-RepoAndWiki.ps1 -Remote upstream
```

#### Parameters

- `-Remote <name>`: Git remote name (default: `origin`)
- `-SkipPull`: Don't fetch from remote
- `-SkipPush`: Don't push to remote
- `-SubmoduleOnly`: Operate only on submodules
- `-RootCommitMessage <msg>`: Custom commit message for pointer updates
- `-PullStrategy <ff-only|merge|rebase>`: How to resolve remote divergence when a fast-forward pull is not possible for
  the root repo and submodules. Default `ff-only` (fail). Use `merge` to auto-merge or `rebase` to rebase local commits.
- `-WhatIf`: Preview operations without executing
- `-Confirm`: Prompt before state-changing operations
- `-Verbose`: Show detailed git commands
- `-IncludeRootChanges`: When set, stage and commit all changes in the root repository (including untracked files). Use
  with `-WhatIf` first to preview.

### Sync-WikiOnly.ps1

Wiki-focused sync: operate only on the wiki submodule.

#### Usage

```powershell
# Sync wiki content (pull, commit, push)
.\scripts/git/Sync-WikiOnly.ps1

# Sync wiki AND update pointer in main repo
.\scripts/git/Sync-WikiOnly.ps1 -UpdatePointer

# Preview pointer update
.\scripts/git/Sync-WikiOnly.ps1 -UpdatePointer -WhatIf

# Custom commit messages (required when commits are needed)
# - Wiki commit (inside wiki submodule)
.\scripts/git/Sync-WikiOnly.ps1 -WikiCommitMessage "📚 docs(wiki): explain feature X"
# - Pointer commit (in main repo)
.\scripts/git/Sync-WikiOnly.ps1 -UpdatePointer -RootCommitMessage "📚 docs: update wiki (new benchmark)"
```

#### Parameters

- `-Remote <name>`: Git remote name (default: `origin`)
- `-UpdatePointer`: Also stage and commit pointer in main repo
- `-SkipPull`: Don't fetch from remote
- `-SkipPush`: Don't push to remote
- `-RootCommitMessage <msg>`: Custom commit message for pointer update (required if a pointer commit is made)
- `-WikiCommitMessage <msg>`: Custom commit message for wiki submodule changes (required if wiki has changes to commit)
- `-PullStrategy <ff-only|merge|rebase>`: How to resolve divergence if a fast-forward pull isn't possible. Default
  `ff-only` (fail). Use `merge` to auto-merge or `rebase` to rebase local commits.
- `-WhatIf`, `-Confirm`, `-Verbose`: Standard PowerShell parameters

#### When to use

- Use after editing wiki content locally
- Use with `-UpdatePointer` to reflect wiki changes in main repo
- Prefer over `Sync-RepoAndWiki.ps1` when only wiki changed

#### Handling diverged wiki branches

If the wiki has diverged from the remote (fast-forward not possible), run with a strategy:

```powershell
# Auto-merge remote/main into local main (no prompt, creates a merge commit if needed)
./scripts/git/Sync-WikiOnly.ps1 -PullStrategy merge -SkipPush

# Or rebase local commits on top of remote/main
./scripts/git/Sync-WikiOnly.ps1 -PullStrategy rebase -SkipPush
```

## Remote Sync

### Sync-Remotes.ps1

Sync changes between multiple Git remotes (e.g., GitHub ↔ GitLab).

#### Usage

```powershell
.\scripts/git/Sync-Remotes.ps1
```

See the script header for configuration details.

----

## Code Analysis

### Invoke-PSSA.ps1

Run PSScriptAnalyzer on PowerShell scripts with project-specific rules.

#### Usage

```powershell
# Analyze all scripts
.\scripts/quality/Invoke-PSSA.ps1

# Analyze specific file
.\scripts/quality/Invoke-PSSA.ps1 -Path .\scripts/git/Sync-RepoAndWiki.ps1
```

**Settings:** `scripts/PSScriptAnalyzerSettings.psd1`

### Invoke-Hadolint.ps1

Lint Dockerfiles with Hadolint via the Gradle task that runs `cl.ravenhill.kalm.tools.hadolint.HadolintCli`.

#### Usage

```powershell
# Lint default Dockerfile with default threshold (warning)
.\scripts\quality\Invoke-Hadolint.ps1

# Lint multiple Dockerfiles with stricter threshold
.\scripts\quality\Invoke-Hadolint.ps1 -Dockerfile 'Dockerfile', 'Dockerfile.dev' -FailureThreshold error

# Enable strict missing-file behavior
.\scripts\quality\Invoke-Hadolint.ps1 -Dockerfile 'Dockerfile' -StrictFiles
```

#### Parameters

- `-Dockerfile <path[]>`: Dockerfile path(s) to lint (default: `Dockerfile`)
- `-FailureThreshold <error|warning|info|style|ignore>`: Hadolint failure threshold (default: `warning`)
- `-StrictFiles`: Fail if any specified Dockerfile is missing

### Invoke-PesterWithConfig.ps1

Run Pester using the repository's canonical configuration file. This helper loads
`scripts/testing/pester.config.psd1`, constructs a, and
invokes `Invoke-Pester` so tests run consistently on developer machines and CI.

#### Requirements:

- Pester 5.x (the script uses `New-PesterConfiguration`)
- PowerShell 7.4+

#### Usage:

```powershell
# From the repository root
.\scripts\testing\Invoke-PesterWithConfig.ps1

# Explicit pwsh invocation (useful in CI)
pwsh -NoProfile -Command "./scripts/testing/Invoke-PesterWithConfig.ps1"
```

#### Notes:

- The script will report a clear error if `scripts/testing/pester.config.psd1`
  is missing.
- Use `-Verbose` when troubleshooting test runs to see the resolved settings path
  and Pester invocation details.

#### CI/CD Integration

The Pester test job is automatically executed in GitLab CI/CD on:

- Merge request events
- Commits to the default branch
- Git tag pushes

Test results are published as JUnit XML artifacts for pipeline reporting. See `.gitlab-ci.yml` and
`dev-resources/CI_CD.md` for pipeline configuration details.

----

## Design Principles

All scripts follow these principles:

1. **Safe**: `-WhatIf` and `-Confirm` support for state-changing operations
2. **Validated**: Pass PSScriptAnalyzer strict rules
3. **Extensible**: Module pattern minimizes code duplication
4. **Dynamic**: Parse configuration files (e.g., `.gitmodules`) instead of hardcoding
5. **Informative**: Use `-Verbose` to see detailed operations
6. **Cross-platform**: PowerShell 7.4+ works on Windows, macOS, Linux

## Examples

### Typical Wiki Workflow

```powershell
# Edit wiki content locally (edit files under `wiki/` using your editor)
# Then use the script to push changes and update the pointer (dry-run first):
.\scripts/git/Sync-WikiOnly.ps1 -WikiCommitMessage "📚 docs(wiki): explain feature X" -UpdatePointer -RootCommitMessage "📚 docs: update wiki pointer (feature X)" -WhatIf
.\scripts/git/Sync-WikiOnly.ps1 -WikiCommitMessage "📚 docs(wiki): explain feature X" -UpdatePointer -RootCommitMessage "📚 docs: update wiki pointer (feature X)"
```

### Full Project Sync

```powershell
# Preview all operations
.\scripts/git/Sync-RepoAndWiki.ps1 -WhatIf

# Execute full sync
.\scripts/git/Sync-RepoAndWiki.ps1 -Verbose
```

### Full add → commit → push workflows (script-first)

The repository provides script-driven workflows to safely stage, commit, and push changes. These examples show the
common scenarios.

- Edit only the wiki (preferred):

  ```powershell
  # Use the script-first approach to push wiki content and update pointer (dry-run first)
  .\scripts/git/Sync-WikiOnly.ps1 -WikiCommitMessage "📚 docs(wiki): explain algorithm X" -UpdatePointer -RootCommitMessage "📚 docs: update wiki pointer (algorithm X)" -WhatIf
  .\scripts/git/Sync-WikiOnly.ps1 -WikiCommitMessage "📚 docs(wiki): explain algorithm X" -UpdatePointer -RootCommitMessage "📚 docs: update wiki pointer (algorithm X)"
  ```

- Edit only root files (docs, scripts, build files):

  ```powershell
  # Preview
  .\scripts/git/Sync-RepoAndWiki.ps1 -SkipPull -IncludeRootChanges -RootCommitMessage "🧹 chore(docs): update contributing and scripts" -WhatIf

  # Commit & push
  .\scripts/git/Sync-RepoAndWiki.ps1 -SkipPull -IncludeRootChanges -RootCommitMessage "🧹 chore(docs): update contributing and scripts"
  ```

- Edit both wiki and root (single command):

  ```powershell
  .\scripts/git/Sync-RepoAndWiki.ps1 -IncludeRootChanges -WhatIf
  .\scripts/git/Sync-RepoAndWiki.ps1 -IncludeRootChanges -RootCommitMessage "🚀 chore(release): publish docs and scripts"
  ```

Notes:

- Always run with `-WhatIf` first to preview actions.
- `-IncludeRootChanges` will stage and commit untracked files in the root repository — use cautiously.
- Use `-SkipPull` when you intentionally don't want to fetch remote updates before committing.

## Troubleshooting

**Script wasn’t found/permission denied:**

```powershell
# Set execution policy (once per machine)
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

**Module import errors:**

```powershell
# Verify module loads correctly
Import-Module .\scripts/git/GitSync.psm1 -Verbose
Get-Command -Module GitSync
```

**Git authentication issues:**

- Ensure SSH keys configured or credential helper enabled
- Test: `git fetch --all --dry-run`

**Submodule branch mismatch:**

- Scripts read branch from `.gitmodules` file
- Verify: `cat .gitmodules` shows correct `branch = main` (or `master`)

## Further Reading

- **Contribution guide:** [`CONTRIBUTING.md`](../CONTRIBUTING.md)
- **Git workflows:** [`dev-resources/GIT_STANDARD.md`](../dev-resources/GIT_STANDARD.md)
- **Dependency locking:** [`dev-resources/DEPENDENCY_LOCKING.md`](../dev-resources/DEPENDENCY_LOCKING.md)

## Testing (Pester)

- Run all tests from Windows PowerShell (pwsh):

```powershell
./scripts/testing/Invoke-PesterWithConfig.ps1
```

- Run tests inside WSL using pwsh (useful for cross-platform verification):

```powershell
wsl.exe -e bash -lc 'cd "$(wslpath -a .)" && pwsh -NoLogo -NoProfile -File ./scripts/testing/Invoke-PesterWithConfig.ps1'
```

Notes:

- The harness isolates each test file in its own pwsh process to avoid class redefinition issues and writes results to
  `build/test-results/pester`.
- If `wsl.exe -e pwsh` fails due to PATH, invoking via `bash -lc` ensures the shell environment is initialized so `pwsh`
  is resolvable.
