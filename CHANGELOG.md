# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased] - TBD

> This section summarizes the net state of the project since `0.1.0`. Entries reflect current functionality only (removed or superseded items are omitted). Commit hashes are included for traceability.

### Added
- Singleton dry-run state for PowerShell automation (`DryRunState.ps1`) — prevents any git invocation during `-WhatIf` (9832ac5c49d2).
- Explicit commit message parameters (`-WikiCommitMessage`, `-RootCommitMessage`) required for sync scripts (e71c1b07a28d).
- Type-aware Detekt configuration and improved plugin usage (925efefea358, ef1c5df8a116).
- Dynamic wiring of subproject `apiCheck` and `detekt` tasks in `verifyAll` (1ef72a767c39, 9ef0de174e81).
- Agent runtime & interaction guidelines (`.github/copilot-instructions.md` and workspace reminders) (ef1c5df8a116, 59301a98cad3, c242c2bc5f3a).

### Changed
- Centralized repository declarations in `settings.gradle.kts` for plugins & dependencies (59301a98cad3).
- Enforced strict dependency locking & regenerated lock states (0725233d875b, c504f84fe656, 6324b4b25cd1).
- Adopted canonical plugin alias form and provider-safe catalog lookups (7940104d8480, bf97e26e76b3).
- Enhanced build reproducibility and documentation of locking & automation (c504f84fe656, 6324b4b25cd1, c242c2bc5f3a).
- Improved PowerShell sync scripts: guarded git wrapper (`Invoke-Git`), output capture & clean status handling (9832ac5c49d2, e71c1b07a28d).

### Documentation
- Updated contribution & automation guidelines to prefer script-first workflows (cf04aa30a16f, 338070690435, c242c2bc5f3a).
- Added / refined dependency locking, agent guidance, and runtime reminders (c504f84fe656, 6324b4b25cd1, 59301a98cad3).

### Fixed
- Clean working tree detection logic for empty `git status` output (9832ac5c49d2).
- Detekt configuration correctness & JVM target derivation (925efefea358).

### Removed
- Obsolete commented changelog links and redundant pre-`0.1.0` script placeholders (6afde41b10e7).

### Notes
- Future release will bundle these changes; consider tagging once wiki & CLI test harness land.


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
