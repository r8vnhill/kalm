/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

/**
 * # Root Build Logic
 *
 * This file applies root-level plugins and conventions shared across all subprojects.
 * It enforces reproducible builds, dependency locking, and version maintenance routines.
 *
 * ## Summary
 * - Enables dependency locking with `LockMode.STRICT` across all projects.
 * - Configures binary compatibility validation.
 * - Adds helper tasks for dependency and verification maintenance.
 * - Restricts unstable dependency updates (alpha, beta, RC, etc.).
 *
 * ## Related documentation
 * - [Gradle Dependency Locking](https://docs.gradle.org/current/userguide/dependency_locking.html)
 * - [Ben Manes Versions Plugin](https://github.com/ben-manes/gradle-versions-plugin)
 * - [Version Catalog Update Plugin](https://github.com/littlerobots/version-catalog-update-plugin)
 * - [Kotlin Binary Compatibility Validator](https://github.com/Kotlin/binary-compatibility-validator)
 */

plugins {
    // Ensures byte-for-byte reproducible archives and metadata (via kalm.reproducible plugin).
    id("kalm.reproducible")

    // Applies dependency locking conventions without relying on allprojects {}.
    id("kalm.dependency-locking")

    id("kalm.build-tasks")

    // Provides API compatibility checks for public Kotlin APIs.
    alias(libs.plugins.kotlin.bin.compatibility)

    // Registers Detekt for subprojects without applying it to the root.
    alias(libs.plugins.detekt) apply false
    // Applies Dokka at root to enable aggregated multi-module documentation.
    alias(libs.plugins.dokka)

    // Adds dependency maintenance helpers:
    alias(libs.plugins.version.catalog.update) // Version Catalog Update (VCU)
    alias(libs.plugins.ben.manes.versions) // Ben Manes dependency updates report
}

dependencies {
    // Include JVM modules in root Dokka aggregation.
    dokka(projects.core)
    dokka(projects.tools)
}

/**
 * # Kotlin Binary Compatibility Validation
 *
 * Ensures that changes to public APIs do not break binary compatibility.
 * Projects listed in `ignoredProjects` are skipped (e.g., test utilities or examples).
 */
apiValidation {
    ignoredProjects += listOf(
        // Uncomment to exclude optional modules from binary compatibility validation.
        // "test-utils", "examples"
    )
}

/**
 * # Version Catalog Update (VCU)
 *
 * Automatically sorts version keys alphabetically and preserves unused entries.
 * This helps maintain a clean, consistent `libs.versions.toml`.
 */
versionCatalogUpdate {
    sortByKey.set(true)
    keep {
        keepUnusedVersions.set(true)
    }
}
