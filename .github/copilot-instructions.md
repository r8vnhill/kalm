# AI Assistant Guide for this Repo (KALM)

Purpose: give agents the minimum, precise context to work productively here. Keep answers concrete and aligned with how this project is structured and built.

## Hard rules (must follow)
1) Never stage, commit, push, or open PRs unless the user explicitly asks in a message.
2) Explain intended multi-file changes before applying them; keep diffs minimal and scoped.
3) Preserve existing style and public API unless a change is requested or required by tests/fixes.

## Big picture architecture
- Multi-project Gradle Kotlin build:
  - `core`: domain model + minimal optimization abstractions (e.g., `OptimizationEngine`, `Feature`, `ScalarFeature`, `SimpleHillClimber`).
  - `platform`: BOM/platform alignment for consumers.
  - `build-logic`: precompiled convention plugins (applied by modules):
    - `kalm.detekt-redmadrobot`: shared Detekt workflow with RedMadRobot tasks.
    - `kalm.dependency-locking`: strict dependency locking policy.
- Source examples:
  - `core/src/main/kotlin/cl/ravenhill/kalm/engine/OptimizationEngine.kt` (engine contract)
  - `core/src/main/kotlin/cl/ravenhill/kalm/repr/Feature.kt` (representation contract)
  - `core/src/main/kotlin/cl/ravenhill/kalm/engine/SimpleHillClimber.kt` (example implementation)

## Developer workflows (use these)
- Aggregated verification:
  - `./gradlew verifyAll` runs tests, Detekt, and API checks (dynamically wires subproject tasks).
  - `./gradlew preflight` runs `verifyAll` plus dependency maintenance helpers.
- Static analysis (Detekt) is applied via `kalm.detekt-redmadrobot` and configured once:
  - Multi-format reports enabled per task: HTML (dev), SARIF (code scanning), TXT (CI logs).
  - Type-aware analysis: classpath includes `main` outputs and the `detekt` configuration; `jvmTarget` derives from the module toolchain.
- Reproducible builds via strict dependency locking (convention plugin `kalm.dependency-locking`):
  - Lock mode is STRICT; missing lock state must be written explicitly with `--write-locks` (only when the user asks).
  - See `dev-resources/DEPENDENCY_LOCKING.md` for exact commands and troubleshooting.
- Running Gradle with a specific JDK: prefer IDE; otherwise use `scripts/Invoke-GradleWithJdk.ps1` or `scripts/invoke_gradle_with_jdk.sh`.

## Project conventions and patterns
- Prefer convention plugins over `allprojects/subprojects` cross-configuration.
- Use the version catalog (libs.versions.toml) and provider-safe accessors; avoid eager resolution in build scripts.
- Detekt thresholds are intentionally modest to encourage short, focused code (e.g., CyclomaticComplexMethod=10, LongMethod=40, TooManyFunctions stricter for objects). Relax only when tests justify it.
- API surface is validated with the binary-compatibility validator (`apiCheck`/`apiDump`); API dumps under `<module>/api/*.api` are source-of-truth.

PowerShell/path portability
- When editing or adding PowerShell scripts, prefer cross-platform path helpers (for example: `Join-Path`, `Split-Path`, and `[IO.Path]` helpers) over hard-coded separators like `\` or `/`.
- Dot-sourcing or building paths should use `Join-Path -Path $PSScriptRoot -ChildPath 'lib'` and then `Join-Path` again for file names. This keeps scripts portable on Windows, macOS and Linux and avoids subtle bugs with path trimming or string concatenation.

## What to do / avoid when editing
- DO: propose diffs, cite exact files/symbols (e.g., `core/.../OptimizationEngine.kt`).
- DO: run verification locally (e.g., `:core:detekt` or `verifyAll`) and summarize results.
- AVOID: bumping versions, regenerating lockfiles, or altering CI without explicit user request.

Agent Git & automation guidance
- DO: prefer using the repository's tested automation scripts under `scripts/` (for example `Sync-RepoAndWiki.ps1`, `Sync-WikiOnly.ps1`, and `Invoke-GradleWithJdk.ps1`) when performing repetitive Git or submodule tasks. These scripts encapsulate safety checks, support `-WhatIf`/`-Confirm`, and are validated by PSScriptAnalyzer — they reduce risk and improve reproducibility across contributors.
- DO: when you need to stage/commit/push as part of a user's explicit request, run the scripts rather than invoking raw `git` directly. If you must construct git commands, explain why the script could not be used and document the exact command you will run.
- DO NOT: stage, commit, or push changes unless the user explicitly requests it in a message. Even when using the scripts, ask for explicit permission before making changes that will be committed or pushed.

Note: This repository may include a workspace-local reminder file at `dev-resources/AGENT_GUIDELINES.md` containing small run-time hints for agents (for example, shell/wrapper usage). Please read that file when starting interactive sessions — it complements but does not replace these core instructions.

## Quick references
- Root build: `build.gradle.kts` (defines `verifyAll`, `preflight`).
- Convention plugins: `build-logic/src/main/kotlin/*.gradle.kts`.
- Detekt config: `config/detekt/detekt.yml` (baseline optional under same folder).
- Dependency locking guide: `dev-resources/DEPENDENCY_LOCKING.md`.

— Keep responses terse and actionable. Ask one clarifying question only if truly blocked.

Last updated: 2025-11-07
