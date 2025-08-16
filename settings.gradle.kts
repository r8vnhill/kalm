/*
 * Copyright (c) 2025
 * 2-Clause BSD License.
 *
 * === Purpose ===
 * Centralizes project-wide settings that affect *all* included builds and subprojects:
 *  - Plugin resolution (where plugins come from + versions for settings plugins)
 *  - Dependency resolution policy and repositories
 *  - Toolchain resolver (foojay) at settings scope
 *  - Build cache configuration
 *  - Project structure (root name + modules)
 */

rootProject.name = "keen-op"

// === FEATURE PREVIEWS ===
// Enables type-safe project accessors so you can reference modules as `projects.core` instead of string paths in build
// scripts. This only changes the generated accessors; it’s safe to enable.
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// === PLUGIN MANAGEMENT ===
pluginManagement {
    // Include the build that provides your precompiled/convention plugins.
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    // Version the *settings-level* plugin(s) here so the top-level `plugins {}` can be versionless.
    // Allows centralizing versions via gradle.properties.
    val foojayResolverVersion = providers
        .gradleProperty("version.foojay.resolver")
        .orElse("1.0.0")
        .get()

    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version foojayResolverVersion
    }
}

// === DEPENDENCY RESOLUTION MANAGEMENT (for subprojects) ===
// - Controls repositories for *project dependencies* (not plugins).
// - `FAIL_ON_PROJECT_REPOS` ensures modules cannot add adhoc repositories, improving reproducibility.
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS

    repositories {
        mavenCentral()
    }
}

// === SETTINGS PLUGINS ===
// Foojay resolver lets Gradle automatically provision JDKs for toolchains on CI/clean machines.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention")
}

// === BUILD CACHE ===
buildCache {
    local {
        isEnabled = true
    }
}

include(
    ":dependency-constraints",  // BOM for consistent versions across modules

    ":utils:test-commons",      // Common test utilities for Keen modules
    ":utils:math",              // Efficient math utilities (JVM-only, uses Math.fma, Vector API)

    ":core",                    // Core Keen library; contains the main Problem-solving API

    ":benchmark",               // Benchmarking utilities for Keen

    ":examples"                 // Example applications using Keen
)
