# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.2] - 2026-02-15

### Added
- Kotlin-based Hadolint CLI under `tools` with typed options/results, runner abstraction (`binary`/`docker`), and JSON output contract.
- Dedicated Hadolint module support (`tools/build.gradle.kts`, lockfile updates) and Dockerized Hadolint runtime (`scripts/docker/Dockerfile.hadolint-kts`).
- PowerShell wrappers for Hadolint and container smoke testing (`scripts/quality/Invoke-Hadolint.ps1`, `scripts/Invoke-ContainerSmokeTest.ps1`).
- Shared PowerShell helper for repository-root discovery (`scripts/lib/Get-KalmRepoRoot.ps1`).
- Comprehensive Hadolint CLI test suite (`tools/src/test/kotlin/cl/ravenhill/kalm/tools/hadolint/HadolintCliTest.kt`).

### Changed
- Improved GitLab CI reliability and speed: pinned Docker/Hadolint images, BuildKit + buildx local cache, interruptible/retry behavior, and explicit PowerShell container entrypoint.
- Moved smoke-test logic to a reusable script (`scripts/smoke.ps1`) and simplified CI command quoting.
- Updated dependency/tooling baselines (Kotlin/Kotest catalog updates, Gradle wrapper/properties, lockfiles).
- Updated docs for CI/CD, containers, and script usage (`README.md`, `dev-resources/*`, `scripts/README.md`).

---

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
-
---

## [0.2.0] - 2026-01-07

### Added
- Docker container image (`kalm-env`) for reproducible builds, tests, and scientific experiments (Dockerfile, docker-compose.yml)
- GitLab CI/CD pipeline with container smoke test to verify Java, PowerShell, and Pester availability
- Pester PowerShell testing framework installed in container image
- Comprehensive PowerShell automation scripts:
  - Git and wiki submodule synchronization with pull strategy options (ff-only, merge, rebase)
  - Git staging preview and commit automation
  - Pester test harness with module discovery and validation
  - ScriptLogging module for standardized error/warning/info output
- Documentation for container usage, Dockerfile linting (Hadolint), and PowerShell scripting practices
- Custom PSScriptAnalyzer rule for PsCustomObject casing validation

### Changed
- Centralized repository declarations and agent runtime guidelines
- Improved Detekt convention: enabled type-aware analysis with dynamic jvmTarget
- Enhanced Pester test discovery with better glob enumeration handling
- Refactored DryRunState from script to module implementation
- Stabilized Pester test harness with extracted C# test generation component
- Updated wiki submodule pointers to reflect latest documentation

### Fixed
- Container smoke test quoting issues in GitLab CI
- Resolve-PesterSettings helper coverage improvements
- Detekt plugin usage and verification tasks now resilient when not applied to root
- PSScriptAnalyzer rule violations in sync scripts

### Deprecated
- None

### Removed
- None

### Security
- Enforced strict dependency locking across all modules (gradle.lockfile, settings-gradle.lockfile, module-level lockfiles)
- Added non-root `builder` user in Docker image for improved security

---

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

---

[unreleased]: https://gitlab.com/r8vnhill/kalm/-/compare/v0.2.2...HEAD
[0.2.2]: https://gitlab.com/r8vnhill/kalm/-/compare/v0.2.1...v0.2.2
[0.2.1]: https://gitlab.com/r8vnhill/kalm/-/compare/v0.2.0...v0.2.1
[0.2.0]: https://gitlab.com/r8vnhill/kalm/-/compare/v0.1.0...v0.2.0
[0.1.0]: https://gitlab.com/r8vnhill/kalm/-/releases/v0.1.0
