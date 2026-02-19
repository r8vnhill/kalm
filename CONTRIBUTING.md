## 🤝 Contributing

Contributions and ideas are welcome! If you're planning to contribute, please:

- Read the documentation and contribution guidelines under [`dev-resources/`](./dev-resources/)
- Familiarize yourself with our [Code of Conduct](./CODE_OF_CONDUCT.md)
 - Review the repository's agent guidance and hard rules in `.github/copilot-instructions.md` (these contain important agent policies and workflow expectations).

Before opening a merge request:

- Run `./gradlew verifyAll` for routine contributions (tests + static analysis + API checks).
- Use `./gradlew preflight --write-locks` only when dependency versions change and lockfiles must be regenerated.
- Commit lockfile changes (`gradle.lockfile`, `settings-gradle.lockfile`, `<module>/gradle.lockfile`) alongside any dependency updates.

### Wiki Updates

The project wiki (`wiki/` submodule) documents research-oriented content: design rationale, algorithm analysis, and experimental methodology. To contribute:

#### Wiki initialization & editing (script-first)

If you need to work with the project wiki, prefer the automation provided in `scripts/` rather than manual git commands. Always run the scripts with `-WhatIf` first to preview actions.

If the wiki submodule is already initialized, use `Sync-WikiOnly.ps1` or `Sync-RepoAndWiki.ps1` (examples below) to push content and update the pointer. If the submodule is not initialized, the scripts will report the condition and suggest the minimal git submodule initialization step.

#### Automated Sync Tools

For convenience, use one of these automation options:

**Option 1: Gradle task** (pulls wiki and stages pointer):
```bash
./gradlew syncWiki
git commit -m "📚 docs: sync wiki to latest"
```

**Option 2: PowerShell scripts** (requires PowerShell 7.4+):

- **Sync entire repository + all submodules** (fetch, commit, push):
  ```powershell
  .\scripts/git/Sync-RepoAndWiki.ps1
  ```
  
  Common flags:
  - `-SkipPull`: Don't fetch from remote (only commit and push local changes)
  - `-SkipPush`: Don't push to remote (fetch and commit only)
  - `-SubmoduleOnly`: Skip main repo, only sync submodules
  - `-WhatIf`: Preview operations without making changes
  
  Example (dry-run before actual sync):
  ```powershell
  .\scripts/git/Sync-RepoAndWiki.ps1 -WhatIf
  .\scripts/git/Sync-RepoAndWiki.ps1
  ```

- **Sync only the wiki submodule** (focused workflow):
  ```powershell
  .\scripts/git/Sync-WikiOnly.ps1 -UpdatePointer
  ```
  
  Flags:
  - `-UpdatePointer`: Also stage and commit the pointer update in the main repo
  - `-SkipPull`, `-SkipPush`, `-WhatIf`: Same as above

Additional flag:
- `-IncludeRootChanges`: Stage and commit all changes in the root repository (useful after editing docs, scripts, or build files). Use with care; prefer running with `-WhatIf` first.
  
  Example (update wiki content and pointer):
  ```powershell
  # Edit wiki files, then:
  .\scripts/git/Sync-WikiOnly.ps1 -UpdatePointer
  ```

**When to use each tool:**
- `syncWiki` task: Quick read-only sync (pull latest wiki)
- `Sync-WikiOnly.ps1`: After editing wiki content (push changes)
- `Sync-RepoAndWiki.ps1`: Full project sync including all submodules

**Preferred workflow**

When possible, prefer using the PowerShell automation scripts under the `scripts/` directory for Git and submodule workflows (for example, `Sync-WikiOnly.ps1` and `Sync-RepoAndWiki.ps1`). These scripts encapsulate safety checks (clean working tree assertions), support `-WhatIf`/`-Confirm`, and handle common corner cases such as detecting submodule branches and staging pointer updates. Use the Gradle `syncWiki` task for quick pulls when you only need to refresh the pointer locally without pushing changes.

For Gradle-related operational workflows that need runtime arguments, prefer a `tools/` CLI plus a `scripts/` wrapper over parameterized Gradle tasks. Keep Gradle tasks primarily for deterministic wiring/orchestration.

**Wiki conventions:**
- Use clear section headers and link liberally between pages.
- Include code examples from the main repo (reference specific files/lines).
- Document **alternatives considered** and **why** decisions were made (not just "what").
- Add benchmark results with reproducible setup (JVM version, problem size, hardware).

### Full add → commit → push workflows (recommended using scripts)

These examples show the recommended, script-driven workflows for common edit scenarios. Always run with `-WhatIf` first to preview the actions.

- Edit only the wiki (preferred):

  1. Edit files under `wiki/`.
  2. Commit & push using the script (no manual `git add/commit/push` needed):

    ```powershell
  # Dry-run first: commit wiki changes + update pointer (messages required when commits occur)
  .\scripts/git/Sync-WikiOnly.ps1 -WikiCommitMessage "📚 docs(wiki): explain feature X" -UpdatePointer -RootCommitMessage "📚 docs: update wiki pointer (feature X)" -WhatIf

  # Execute: commit wiki changes, push wiki, update pointer, push root
  .\scripts/git/Sync-WikiOnly.ps1 -WikiCommitMessage "📚 docs(wiki): explain feature X" -UpdatePointer -RootCommitMessage "📚 docs: update wiki pointer (feature X)"
    ```

  3. Alternatively, if you prefer the script to perform the wiki push and pointer update in one step, use the full sync script (runs submodule push then updates pointer):

    ```powershell
    .\scripts/git/Sync-RepoAndWiki.ps1 -SkipPull -IncludeRootChanges -RootCommitMessage "📚 docs: update wiki and pointer (feature X)" -WhatIf
    .\scripts/git/Sync-RepoAndWiki.ps1 -SkipPull -IncludeRootChanges -RootCommitMessage "📚 docs: update wiki and pointer (feature X)"
    ```

- Edit only files in the main repository (docs, scripts, build files):

  ```powershell
  # Preview staging, commit and push all root changes
  .\scripts/git/Sync-RepoAndWiki.ps1 -SkipPull -IncludeRootChanges -RootCommitMessage "🧹 chore(docs): update contributing and scripts" -WhatIf

  # Commit and push
  .\scripts/git/Sync-RepoAndWiki.ps1 -SkipPull -IncludeRootChanges -RootCommitMessage "🧹 chore(docs): update contributing and scripts"
  ```

- Edit both wiki and root (single workflow):

  ```powershell
  # Dry-run: will fetch/push submodule + stage/commit root changes when -IncludeRootChanges is set
  .\scripts/git/Sync-RepoAndWiki.ps1 -IncludeRootChanges -WhatIf

  # Execute (commits and pushes submodule changes, updates pointer, stages/commits root changes, and pushes)
  .\scripts/git/Sync-RepoAndWiki.ps1 -IncludeRootChanges -RootCommitMessage "🚀 chore(release): publish docs and scripts"
  ```

Notes:
- `-IncludeRootChanges` stages and commits all changes in the repository root (including untracked files). Use with caution and prefer `-WhatIf` first.
- `-SkipPull` is useful when you want to commit/push local changes without fetching remote updates first.
- The scripts will respect `-Confirm` and `-WhatIf` where applicable; prefer interactive confirmation for large or risky updates.

We follow the [Contributor Covenant v2.1](https://www.contributor-covenant.org/version/2/1/code_of_conduct.html), a code of conduct that fosters an inclusive, respectful, and harassment-free environment. It asks all participants to act with empathy and professionalism, and outlines consequences for unacceptable behavior.

By contributing to this project, you agree to uphold these values.

### Static Analysis & Code Quality

Static analysis is powered by the RedMadRobot Detekt Gradle plugin applied via the `kalm.detekt-redmadrobot` convention plugin. Common tasks:

```bash
./gradlew detektAll       # Full multi-module scan
./gradlew detektDiff      # Only changed files vs main (faster incremental check)
./gradlew detektFormat    # Auto-format Kotlin sources
```

Run `./gradlew verifyAll` before pushing to ensure Detekt, tests, and API checks all pass. Advanced configuration (e.g., configuring `checkOnlyDiffWithBranch`) is documented in `dev-resources/DOCUMENTATION_RULES.md`.
