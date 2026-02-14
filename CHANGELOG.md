# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),  
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased] - TBD

## [0.2.1] - 2026-02-12

### Added
- New `SyncVersionPropertiesTask` (`build-logic/src/main/kotlin/tasks/SyncVersionPropertiesTask.kt`) to synchronize selected `gradle.properties` entries from `gradle/libs.versions.toml`.
- Dedicated `build-logic/gradle.properties` for build-logic-scoped mirrored version properties and defaults.
- New root tasks `syncVersionProperties` and `syncBuildLogicVersionProperties` for catalog-to-properties synchronization.
- New `dependencyUpdatesNoParallel` helper task for dependency report runs that require `--no-parallel`.

### Changed
- Gradle wrapper upgraded from `9.1.0` to `9.3.1`.
- Build logic now reads Foojay resolver version frgit pom mirrored properties in both root and included build settings, failing fast when missing.
- `preflight` workflow now runs verification + version-property synchronization (`verifyAll`, `syncVersionProperties`, `syncBuildLogicVersionProperties`) instead of invoking dependency-maintenance tasks.
- `verifyAll` task wiring moved to lazy task matching (`tasks.matching { ... }.configureEach`) instead of `projectsEvaluated`.
- Dependency update policy for `dependencyUpdates` now uses stricter pre-release filtering and lazier output directory resolution.
- Dependency locking convention now activates locking only on resolvable configurations while keeping strict lock mode.
- Build-logic toolchain configuration was unified around a property-driven Java version (`buildlogic.java.version`) and aligned Kotlin/Java toolchains.
- Build-logic functional test task metadata is normalized for better discoverability in Gradle task listings.
- Version catalog and lockfiles were refreshed for current build state (including JUnit `6.0.2` and additional plugin/library aliases used by build logic).
- Automation and docs updates:
  - `Invoke-GradleWithJdk.ps1` now resolves platform-specific wrapper names and executes Gradle via `System.Diagnostics.Process`.
  - Dependency update documentation now reflects automatic property synchronization flow.
  - README requirements were clarified for supported JDK range.
  - Wiki submodule pointer updated to include current Gradle build/task documentation.

### Removed
- Removed `syncWiki` Gradle task from build logic (wiki synchronization remains a Git/submodule workflow).

## [0.0.3] – 2025-07-23

### ✨ Added

- Introduced `CHANGELOG.md` following Keep a Changelog and SemVer standard.
- Added `Keen.Git` PowerShell module for standardized Git operations.
- Introduced `EnableGitUtilities.ps1` to load the `Keen.Git` module into the session, and `DisableGitUtilities.ps1` to unload it.
- Added `Invoke-GitCheckout` wrapper for safer and validated Git branch checkout.

## [0.0.2.2] – 2025-04-30

### Added
- `keen.reproducible` convention plugin to configure reproducible archives across the project.
- Centralized Detekt and binary compatibility validator plugin configuration in the root build.
- `.gitignore` updated to allow `gradle-wrapper.jar` in convention plugins.

### Changed
- Moved reproducibility logic from root build script to reusable plugin.
- Project version bumped to `0.0.2.2`.

---

## [0.0.2.1] – 2025-04-30

### Changed
- Updated CI workflow for cleaner branch matching and Gradle cache configuration.
- Improved naming and ordering of GitHub Actions steps.
- Removed redundant version check logic in CI.
- Bumped project version to `0.0.2.1`.

---

## [0.0.2] – 2025-04-29

### Added
- CI check to enforce version update in `gradle.properties`.
- Clear message for version check failures: “✅ Version change detected. Please update the version in gradle.properties.”

---

## [0.0.1] – 2025-04-28

### Added
- Convention plugins: `keen.kotlin`, `keen.jvm`, `keen.library`.
- Core module with shared build logic via convention plugins.
- GitHub Actions CI workflow for build and test.
- README badges: CI status, Kotlin/Gradle version, license, project status.
- `.gitattributes`, `.gitignore`, `gradle.properties`, and `.github` CI structure.
- Pre-push Git hook to ensure version updates.

### Changed
- Renamed published coordinates and source packages to the `cl.ravenhill.kalm` namespace and refreshed repository references to the new project name.
- Updated build logic conventions, wiring for the version-catalog-update plugin, and centralized Foojay resolver configuration.
- Refined issue and merge request templates, `.gitattributes`, `.gitignore`, and blame-ignore settings to match the new repo layout.

### Removed
- Dropped legacy PowerShell Git helper scripts in favor of the consolidated `Sync-Remotes` workflow.

[unreleased]: https://gitlab.com/r8vnhill/kalm/-/compare/v0.2.1...HEAD
[0.2.1]: https://gitlab.com/r8vnhill/kalm/-/compare/v0.2.0...v0.2.1
[0.2.0]: https://gitlab.com/r8vnhill/kalm/-/compare/v0.1.0...v0.2.0
[0.1.0]: https://gitlab.com/r8vnhill/kalm/-/releases/v0.1.0
