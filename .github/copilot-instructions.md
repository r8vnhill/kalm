# AI Assistant Instructions for KALM
Multi-module Kotlin/Gradle optimization sandbox with strict reproducibility and PowerShell-first automation.

## Hard rules
1. Never stage/commit/push or touch wiki remotes unless the user explicitly requests it.
2. Announce planned multi-file edits before touching them; keep diffs laser-focused.
3. Preserve published APIs (`core/api/core.api`) and Kotlin style unless breaking changes are requested + justified.
4. Read `dev-resources/AGENT_GUIDELINES.md` once per session and respect its shell guidance (already in pwsh).

## Architecture snapshot
- `core/` hosts algorithm contracts such as `OptimizationEngine` and the self-referential `Feature<T, F>` hierarchy; treat generics carefully to keep type safety.
- `platform/` exposes the BOM (`kalm-platform`) so consumers import aligned versions — don’t add dependencies directly to downstream modules.
- `build-logic/` defines convention plugins (`kalm.jvm`, `.library`, `.dependency-locking`, `.detekt-redmadrobot`, `.reproducible`) that modules opt into via `plugins {}`; never re-introduce `allprojects {}` blocks.
- `scripts/` PowerShell modules (see `scripts/README.md`) wrap Git, wiki, Gradle, Pester, and logging behaviors; reuse them instead of crafting ad-hoc automation.
- `wiki/` is a git submodule that stores design docs; update it only through the provided sync scripts.

## Day-to-day workflows
- Full guardrail: `./gradlew verifyAll` (tests + Detekt + API checks). `preflight` adds dependency reports.
- Module focus: `./gradlew :core:test`, `:core:detekt`, `:core:apiCheck`; regenerate dumps with `:core:apiDump` only when the user approves API changes.
- Static analysis: `detektAll` for everything, `detektDiff` for branch deltas, `detektFormat` to auto-fix. Config lives in `config/detekt/detekt.yml`.
- Dependency locking is strict (`gradle.lockfile`, `<module>/gradle.lockfile`, `settings-gradle.lockfile`); only rerun tasks with `--write-locks` when explicitly asked and commit every touched lockfile.
- Need a specific JDK? Run `./scripts/gradle/Invoke-GradleWithJdk.ps1 -JdkPath <path> -GradleArgument 'verifyAll'`.
- PowerShell quality gates: `./scripts/quality/Invoke-PSSA.ps1` for linting and `./scripts/testing/Invoke-PesterWithConfig.ps1` for tests (reads `scripts/testing/pester.config.psd1`).

## Git + wiki expectations
- Prefer script-first syncs: `./scripts/git/Sync-RepoAndWiki.ps1` for the whole repo, `Sync-WikiOnly.ps1 -UpdatePointer` for docs. Always dry-run with `-WhatIf` when touching remotes or submodules.
- Avoid redundant `pwsh -NoProfile` wrappers when already inside pwsh; consult `dev-resources/AGENT_GUIDELINES.md`.
- When wiki diverges, use the script’s `-PullStrategy merge|rebase` switches instead of manual git commands.

## Key references
- Version catalog: `gradle/libs.versions.toml`.
- Detekt + RedMadRobot tuning: `config/detekt/detekt.yml`, convention plugin sources in `build-logic/src/main/kotlin/`.
- Dependency-locking how-to: `dev-resources/DEPENDENCY_LOCKING.md`.
- Git standards & CI/CD expectations: `dev-resources/GIT_STANDARD.md`, `dev-resources/CI_CD.md`.
