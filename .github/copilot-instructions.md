# AI Assistant Instructions for KALM

Multi-module Gradle/Kotlin optimization framework with strict dependency locking, convention plugins, and automated Git/wiki workflows.

## Hard Rules
1. Never stage, commit, push, or open PRs unless explicitly requested
2. Explain multi-file changes before applying; keep diffs minimal
3. Preserve public APIs and coding style unless tests/user require changes
4. Read `dev-resources/AGENT_GUIDELINES.md` at session start for shell behavior tips

## Architecture Overview
**Multi-module structure:**
- `core/` — domain model, algorithms (`OptimizationEngine`, `Feature` with self-referential types)
- `platform/` — BOM for version alignment
- `build-logic/` — convention plugins (`kalm.jvm`, `kalm.library`, `kalm.detekt-redmadrobot`, `kalm.dependency-locking`)

**Key patterns:**
- Self-referential types in `Feature<T, F> where F : Feature<T, F>` for type-safe transformations
- Convention plugins apply toolchain, dependency locking, and Detekt — not via `allprojects {}`
- Version catalog at `gradle/libs.versions.toml` with provider-safe accessors

## Essential Commands
```powershell
# Full verification (tests + Detekt + API checks)
./gradlew verifyAll

# Preflight (verifyAll + dependency reports)
./gradlew preflight

# Module-specific
./gradlew :core:test
./gradlew :core:detekt

# Multi-module Detekt tasks (via RedMadRobot plugin)
./gradlew detektAll          # All modules
./gradlew detektDiff         # Only changes vs main
./gradlew detektFormat       # Auto-format

# Gradle with specific JDK (script-first)
./scripts/gradle/Invoke-GradleWithJdk.ps1 -JdkPath 'C:\Java\jdk-22' -GradleArgument 'verifyAll'
```

## Critical Traps & Conventions
**Dependency locking (strict mode):**
- Lock files: `gradle.lockfile`, `core/gradle.lockfile`, etc.
- **Never** regenerate without `--write-locks` explicitly requested by user
- Missing lock state? Run task with `--write-locks`: `./gradlew :core:test --write-locks`
- Detekt lock issues: `./gradlew :core:detekt --write-locks`
- See `dev-resources/DEPENDENCY_LOCKING.md` for troubleshooting

**Binary compatibility:**
- API dumps at `core/api/core.api` are source-of-truth
- Changes trigger `apiCheck` failures — regenerate with `apiDump` only when APIs intentionally change

**Detekt:**
- Config: `config/detekt/detekt.yml`
- Auto-format with `detektFormat`, incremental checks with `detektDiff`
- Thresholds intentionally low — justify suppressions with tests

**Git & wiki automation (PowerShell 7.4+):**
- Use `scripts/git/Sync-WikiOnly.ps1` for wiki changes, `scripts/git/Sync-RepoAndWiki.ps1` for full sync
- Always preview with `-WhatIf` before executing
- Handle divergence with `-PullStrategy merge|rebase` (default: `ff-only` fails fast)
- Avoid redundant pwsh wrappers when already in pwsh (see `dev-resources/AGENT_GUIDELINES.md`)

## Convention Plugin Architecture
Plugins in `build-logic/src/main/kotlin/`:
- `kalm.jvm` — Java 22 toolchain, Kotlin compiler options, `-Xjsr305=strict`
- `kalm.library` — Maven publishing, explicit API mode, test configuration (JUnit Platform)
- `kalm.dependency-locking` — applies `LockMode.STRICT` per module (not via `allprojects`)
- `kalm.detekt-redmadrobot` — wraps RedMadRobot plugin for multi-module Detekt tasks
- `kalm.reproducible` — byte-for-byte reproducible builds (archive metadata)

**Why convention plugins?**
- Avoids `allprojects {}` pitfalls (breaks configuration cache, implicit dependencies)
- Each module opts in explicitly via `plugins { id("kalm.jvm") }`

## Workflow Contracts
**Before edits:**
- Gather context (read files, check tests/API dumps)
- Verify lockfiles exist for affected modules

**After edits:**
- Run affected module's tests: `./gradlew :core:test`
- Check Detekt: `./gradlew :core:detekt`
- Verify API compatibility: `./gradlew :core:apiCheck`
- Report failures (build errors, lint violations, API mismatches)

## Key File References
- `core/src/main/kotlin/cl/ravenhill/kalm/engine/OptimizationEngine.kt` — engine contract
- `core/src/main/kotlin/cl/ravenhill/kalm/repr/Feature.kt` — self-referential type example
- `build-logic/src/main/kotlin/kalm.*.gradle.kts` — convention plugin implementations
- `gradle/libs.versions.toml` — version catalog
- `dev-resources/DEPENDENCY_LOCKING.md` — lockfile troubleshooting
- `dev-resources/AGENT_GUIDELINES.md` — shell/runtime tips
- `scripts/README.md` — Git/wiki automation documentation

## Integration Points
- **Wiki submodule:** `wiki/` tracks research docs (synced via `syncWiki` task or scripts)
- **PowerShell scripts:** `scripts/` for Git operations, Gradle JDK selection, PSScriptAnalyzer checks
- **CI verification:** `verifyAll` aggregates tests, Detekt, and API checks across all modules

## Quick Troubleshooting
- Missing lock state? → `./gradlew <task> --write-locks`
- API dump mismatch? → Regenerate with `./gradlew apiDump` (only if intentional API change)
- Detekt fails on new config? → `./gradlew :core:detekt --write-locks`
- Wiki diverged? → `./scripts/git/Sync-WikiOnly.ps1 -PullStrategy merge -WhatIf` (preview first)

Last updated: 2025-11-11
