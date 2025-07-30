# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),  
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased] - TBD

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
- Switched `kotlin.gradle.plugin` to `implementation` scope.
- Refined project naming from `keen-ii` to `keen-go` across files.
- Improved internal build clarity through inline documentation in:
    - `build.gradle.kts`
    - `settings.gradle.kts`
    - `libs.versions.toml`
- Removed legacy `lib` module and test files.
- Centralized plugin management and version catalogs.
- Enhanced reproducibility with archive task settings.
