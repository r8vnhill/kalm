# KALM Scripts

PowerShell automation tools for Git, Gradle, and project maintenance workflows.

## Requirements

- **PowerShell 7.4+** (cross-platform)
- **Git 2.20+**
- **JDK** (for Gradle scripts)

## Git & Submodule Sync

### GitSync.psm1 Module

Reusable functions for Git operations (imported automatically by sync scripts):

| Function                      | Purpose                                            |
| ----------------------------- | -------------------------------------------------- |
| `Invoke-Git`                  | Safe git command wrapper with error handling       |
| `Get-GitCurrentBranch`        | Get current branch name for a repository           |
| `Test-GitClean`               | Check if working directory has uncommitted changes |
| `Get-GitSubmodules`           | Parse `.gitmodules` and return submodule objects   |
| `Sync-GitSubmodule`           | Pull, commit, and push a submodule                 |
| `Update-GitSubmodulePointers` | Stage and commit pointer updates in parent repo    |

### Sync-RepoAndWiki.ps1

Full repository sync: main repo + all submodules (pull → commit → push).

**Usage:**
```powershell
# Full sync (fetch, commit, push everything)
.\scripts\Sync-RepoAndWiki.ps1

# Preview without making changes
.\scripts\Sync-RepoAndWiki.ps1 -WhatIf

# Sync only submodules (skip main repo)
.\scripts\Sync-RepoAndWiki.ps1 -SubmoduleOnly

# Commit local changes without fetching
.\scripts\Sync-RepoAndWiki.ps1 -SkipPull

# Fetch and commit without pushing
.\scripts\Sync-RepoAndWiki.ps1 -SkipPush

# Custom remote
.\scripts\Sync-RepoAndWiki.ps1 -Remote upstream
```

**Parameters:**
- `-Remote <name>`: Git remote name (default: `origin`)
- `-SkipPull`: Don't fetch from remote
- `-SkipPush`: Don't push to remote
- `-SubmoduleOnly`: Operate only on submodules
- `-RootCommitMessage <msg>`: Custom commit message for pointer updates
- `-WhatIf`: Preview operations without executing
- `-Confirm`: Prompt before state-changing operations
- `-Verbose`: Show detailed git commands
- `-IncludeRootChanges`: When set, stage and commit all changes in the root repository (including untracked files). Use with `-WhatIf` first to preview.

### Sync-WikiOnly.ps1

Wiki-focused sync: operate only on the wiki submodule.

**Usage:**
```powershell
# Sync wiki content (pull, commit, push)
.\scripts\Sync-WikiOnly.ps1

# Sync wiki AND update pointer in main repo
.\scripts\Sync-WikiOnly.ps1 -UpdatePointer

# Preview pointer update
.\scripts\Sync-WikiOnly.ps1 -UpdatePointer -WhatIf

# Custom commit message for pointer
.\scripts\Sync-WikiOnly.ps1 -UpdatePointer -RootCommitMessage "docs: update wiki (new benchmark)"
```

**Parameters:**
- `-Remote <name>`: Git remote name (default: `origin`)
- `-UpdatePointer`: Also stage and commit pointer in main repo
- `-SkipPull`: Don't fetch from remote
- `-SkipPush`: Don't push to remote
- `-RootCommitMessage <msg>`: Custom commit message for pointer update
- `-WhatIf`, `-Confirm`, `-Verbose`: Standard PowerShell parameters

**When to use:**
- Use after editing wiki content locally
- Use with `-UpdatePointer` to reflect wiki changes in main repo
- Prefer over `Sync-RepoAndWiki.ps1` when only wiki changed

## Gradle with JDK

### Invoke-GradleWithJdk.ps1

Run Gradle with a specific JDK version (bypasses JAVA_HOME).

**Usage:**
```powershell
# Windows
.\scripts\Invoke-GradleWithJdk.ps1 -JdkPath 'C:\Program Files\Java\jdk-22' -GradleArgument 'clean', 'build'

# Pass through gradle arguments after JdkPath
.\scripts\Invoke-GradleWithJdk.ps1 -JdkPath '/usr/lib/jvm/java-22-openjdk' -GradleArgument '--no-daemon', 'verifyAll'

# Example: run tests with JDK 21
.\scripts\Invoke-GradleWithJdk.ps1 -JdkPath 'C:\Java\jdk-21' -GradleArgument 'test', '--rerun'
```

**Parameters:**
- `-JdkPath <path>`: Absolute path to JDK installation
- `-GradleArgument <args[]>`: Array of arguments to pass to Gradle

### invoke_gradle_with_jdk.sh

Bash/Zsh equivalent of the PowerShell script.

**Usage:**
```bash
# Unix/Linux/macOS
./scripts/invoke_gradle_with_jdk.sh --jdk /usr/lib/jvm/temurin-22 -- clean build

# Example with multiple gradle arguments
./scripts/invoke_gradle_with_jdk.sh --jdk ~/sdkman/candidates/java/22.0.2-tem -- test --info --no-daemon
```

**Arguments:**
- `--jdk <path>`: Path to JDK installation
- `-- <args...>`: Everything after `--` is passed to Gradle

## Remote Sync

### Sync-Remotes.ps1

Sync changes between multiple Git remotes (e.g., GitHub ↔ GitLab).

**Usage:**
```powershell
.\scripts\Sync-Remotes.ps1
```

See script header for configuration details.

## Code Analysis

### Invoke-PSSA.ps1

Run PSScriptAnalyzer on PowerShell scripts with project-specific rules.

**Usage:**
```powershell
# Analyze all scripts
.\scripts\Invoke-PSSA.ps1

# Analyze specific file
.\scripts\Invoke-PSSA.ps1 -Path .\scripts\Sync-RepoAndWiki.ps1
```

**Settings:** `scripts/PSScriptAnalyzerSettings.psd1`

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
# 1. Edit wiki content
cd wiki
# (make changes to markdown files)

# 2. Sync wiki and update pointer in one command
cd ..
.\scripts\Sync-WikiOnly.ps1 -UpdatePointer

# Equivalent manual workflow:
# cd wiki
# git add .
# git commit -m "docs: update design decisions"
# git push origin main
# cd ..
# git add wiki
# git commit -m "docs: update wiki submodule"
# git push origin main
```

### Full Project Sync
```powershell
# Preview all operations
.\scripts\Sync-RepoAndWiki.ps1 -WhatIf

# Execute full sync
.\scripts\Sync-RepoAndWiki.ps1 -Verbose
```

### Run Gradle with Specific JDK
```powershell
# Verify build with JDK 22
.\scripts\Invoke-GradleWithJdk.ps1 -JdkPath 'C:\Java\jdk-22' -GradleArgument 'verifyAll'

# Run benchmarks with JDK 21
.\scripts\Invoke-GradleWithJdk.ps1 -JdkPath 'C:\Java\jdk-21' -GradleArgument ':benchmark:jmh'
```

## Troubleshooting

**Script not found/permission denied:**
```powershell
# Set execution policy (once per machine)
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

**Module import errors:**
```powershell
# Verify module loads correctly
Import-Module .\scripts\GitSync.psm1 -Verbose
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
