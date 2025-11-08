# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased] - TBD

### Added
- PowerShell automation scripts for Git, Gradle, and project workflows:
  - `scripts/GitSync.psm1` module (loader) with reusable Git/submodule helpers split into `scripts/lib/` (`GitInvoke.ps1`, `GitHelpers.ps1`, `GitSubmodule.ps1`, `DryRunState.ps1`).
  - `scripts/Sync-RepoAndWiki.ps1`, `scripts/Sync-WikiOnly.ps1`, `scripts/Sync-Remotes.ps1` for syncing repo and wiki submodules with `-WhatIf` support.
  - `scripts/Invoke-GradleWithJdk.ps1` and Bash equivalent for running Gradle with a specific JDK.
  - `scripts/Invoke-PSSA.ps1` for PSScriptAnalyzer linting.
  - `scripts/README.md` documenting usage, examples, and the script-first workflow.
- `syncWiki` Gradle task to sync wiki submodule via PowerShell script.
- `.github/copilot-instructions.md` providing concise AI agent guidance for the repo.
- `dev-resources/DEPENDENCY_LOCKING.md` documenting strict dependency locking usage, commands, and troubleshooting.
- `kalm.dependency-locking` convention plugin enforcing `LockMode.STRICT` and `lockAllConfigurations()` for reproducible builds.

### Changed
- Build system improvements:
  - Detekt tasks now perform type-aware analysis by including `main` outputs and `detekt` configuration in classpath; `jvmTarget` derived from Java toolchain instead of hard-coded.
  - Plugin declarations use canonical `alias(...)` form; detekt registered with `apply false` in root for subproject opt-in.
  - `verifyAll` task wires subproject `apiCheck` and `detekt` tasks dynamically after project evaluation.
  - Enabled `TYPESAFE_PROJECT_ACCESSORS` and made catalog lookups provider-safe using `orElseThrow()` instead of eager `.get()`.
  - Replaced legacy `allprojects` dependency locking with `gradle.allprojects` and enabled strict mode.
- Documentation updates:
  - `CONTRIBUTING.md`, `dev-resources/GIT_STANDARD.md`, `README.md` recommend PowerShell scripts over raw git commands.
  - Added usage examples and workflows for sync scripts, Gradle with JDK, and dependency locking.
- PowerShell script enhancements:
  - Sync scripts require explicit commit messages via `-WikiCommitMessage` and `-RootCommitMessage` parameters (no defaults).
  - `Invoke-Git` wrapper supports `-CaptureOutput`, `-ReturnExitCode`, `-NoThrow` and respects singleton dry-run state.
  - Top-level sync scripts set shared dry-run singleton when invoked with `-WhatIf` to short-circuit nested git execution.

### Fixed
- `NoNameShadowing` violation in `SimpleHillClimber` by naming lambda parameter to avoid implicit `it` shadowing.
- Empty output from `git status --porcelain` now treated as clean working tree (avoids false failures during dry-run).

## [0.1.0] - 2025-10-27

### Added
- Introduced the `platform` module with BOM support to simplify dependency alignment for consumers.
- Added cross-platform helper scripts for running Gradle with pinned JDKs and for synchronizing remotes with ShouldProcess-aware logging.
- Documented streamlined dependency update procedures and pre-flight contributor checks.

### Changed
- Renamed published coordinates and source packages to the `cl.ravenhill.kalm` namespace and refreshed repository references to the new project name.
- Updated build logic conventions, wiring for the version-catalog-update plugin, and centralized Foojay resolver configuration.
- Refined issue and merge request templates, `.gitattributes`, `.gitignore`, and blame-ignore settings to match the new repo layout.

### Removed
- Dropped legacy PowerShell Git helper scripts in favor of the consolidated `Sync-Remotes` workflow.

[unreleased]: https://gitlab.com/r8vnhill/kalm/-/compare/v0.1.0...HEAD
[0.1.0]: https://gitlab.com/r8vnhill/kalm/-/releases/v0.1.0
