# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.1] - TBD

### Added
- Hadolint...

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

[unreleased]: https://gitlab.com/r8vnhill/kalm/-/compare/v0.2.0...HEAD
[0.2.0]: https://gitlab.com/r8vnhill/kalm/-/compare/v0.1.0...v0.2.0
[0.1.0]: https://gitlab.com/r8vnhill/kalm/-/releases/v0.1.0
