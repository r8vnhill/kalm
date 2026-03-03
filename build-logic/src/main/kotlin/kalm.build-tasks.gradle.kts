/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

/**
 * ## Build Tasks Aggregation Plugin
 *
 * Composable root-level convention plugin that bundles together three orthogonal build task domains:
 *
 * - **Dependency Maintenance**: Manages upgradable dependency detection and unstable allowlists
 * - **Version Synchronization**: Enforces a single source of truth for version catalog and gradle.properties
 * - **Verification**: Aggregates quality gates (tests, static analysis, API checks)
 *
 * ### Purpose
 *
 * Centralizes CI/CD task configuration in one place, enabling consistent build behavior across:
 *
 * - Root project
 * - All subprojects
 * - Local development
 * - Continuous integration pipelines
 *
 * ### Applied Plugins
 *
 * #### `kalm.build-tasks.dependency-maintenance`
 *
 * - Registers gradle-versions-plugin tasks for detecting upgradable dependencies
 * - Provides a configurable allowlist for intentional pre-release/unstable versions
 * - Filters upgrades by pattern matching (group:name wildcards)
 * - Defers parsing via Provider for configuration avoidance
 *
 * #### `kalm.build-tasks.version-sync`
 *
 * - Synchronizes `gradle.properties` entries with `gradle/libs.versions.toml` (version catalog)
 * - Prevents version drift between multiple property files
 * - **Single source of truth:** version catalog is canonical
 * - Lazily validates on task execution
 *
 * #### `kalm.build-tasks.verification`
 *
 * - Registers `verifyAll` aggregate task for root-level quality checks
 * - Dynamically wires subproject tasks: `test`, `detekt`, `apiCheck`
 * - No hardcoded project paths; adapts to project structure
 * - Primary quality gate before release, updates, and lockfile refresh
 *
 * ### Design Rationale
 *
 * - **Composability**: Each plugin focuses on a single responsibility
 * - **Configuration Avoidance**: Uses Gradle Provider API to defer expensive computation
 * - **CI-Friendly**: Gradle properties can override behavior in automation
 * - **Zero Hardcoding**: Dynamic task discovery and lazy wiring
 */

plugins {
    id("kalm.build-tasks.dependency-maintenance")
    id("kalm.build-tasks.version-sync")
    id("kalm.build-tasks.verification")
}
