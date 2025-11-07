## 🤝 Contributing

Contributions and ideas are welcome! If you're planning to contribute, please:

- Read the documentation and contribution guidelines under [`dev-resources/`](./dev-resources/)
- Familiarize yourself with our [Code of Conduct](./CODE_OF_CONDUCT.md)

Before opening a merge request:

- Run `./gradlew preflight` (or `preflight --write-locks`) to execute tests, static analysis, and refresh Gradle lockfiles.
- Commit lockfile changes (`gradle.lockfile`, `settings-gradle.lockfile`, `<module>/gradle.lockfile`) alongside any dependency updates.

### Wiki Updates

The project wiki (`wiki/` submodule) documents research-oriented content: design rationale, algorithm analysis, and experimental methodology. To contribute:

#### Manual Git Workflow

1. **Fetch the wiki submodule** (if not already initialized):
   ```bash
   git submodule update --init --recursive
   ```

2. **Edit wiki content**:
   ```bash
   cd wiki
   git checkout main
   git pull origin main
   # Edit files (e.g., vim Design-Decisions.md)
   git add .
   git commit -m "📝 docs: clarify feature self-types rationale"
   git push origin main
   ```

3. **Update the submodule pointer** in the main repository:
   ```bash
   cd ..
   git add wiki
   git commit -m "📚 docs: update wiki submodule (feature self-types)"
   ```

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
  .\scripts\Sync-RepoAndWiki.ps1
  ```
  
  Common flags:
  - `-SkipPull`: Don't fetch from remote (only commit and push local changes)
  - `-SkipPush`: Don't push to remote (fetch and commit only)
  - `-SubmoduleOnly`: Skip main repo, only sync submodules
  - `-WhatIf`: Preview operations without making changes
  
  Example (dry-run before actual sync):
  ```powershell
  .\scripts\Sync-RepoAndWiki.ps1 -WhatIf
  .\scripts\Sync-RepoAndWiki.ps1
  ```

- **Sync only the wiki submodule** (focused workflow):
  ```powershell
  .\scripts\Sync-WikiOnly.ps1 -UpdatePointer
  ```
  
  Flags:
  - `-UpdatePointer`: Also stage and commit the pointer update in the main repo
  - `-SkipPull`, `-SkipPush`, `-WhatIf`: Same as above

Additional flag:
- `-IncludeRootChanges`: Stage and commit all changes in the root repository (useful after editing docs, scripts, or build files). Use with care; prefer running with `-WhatIf` first.
  
  Example (update wiki content and pointer):
  ```powershell
  # Edit wiki files, then:
  .\scripts\Sync-WikiOnly.ps1 -UpdatePointer
  ```

**When to use each tool:**
- `syncWiki` task: Quick read-only sync (pull latest wiki)
- `Sync-WikiOnly.ps1`: After editing wiki content (push changes)
- `Sync-RepoAndWiki.ps1`: Full project sync including all submodules

**Preferred workflow**

When possible, prefer using the PowerShell automation scripts under the `scripts/` directory for Git and submodule workflows (for example, `Sync-WikiOnly.ps1` and `Sync-RepoAndWiki.ps1`). These scripts encapsulate safety checks (clean working tree assertions), support `-WhatIf`/`-Confirm`, and handle common corner cases such as detecting submodule branches and staging pointer updates. Use the Gradle `syncWiki` task for quick pulls when you only need to refresh the pointer locally without pushing changes.

**Wiki conventions:**
- Use clear section headers and link liberally between pages.
- Include code examples from the main repo (reference specific files/lines).
- Document **alternatives considered** and **why** decisions were made (not just "what").
- Add benchmark results with reproducible setup (JVM version, problem size, hardware).

We follow the [Contributor Covenant v2.1](https://www.contributor-covenant.org/version/2/1/code_of_conduct.html), a code of conduct that fosters an inclusive, respectful, and harassment-free environment. It asks all participants to act with empathy and professionalism, and outlines consequences for unacceptable behavior.

By contributing to this project, you agree to uphold these values.
