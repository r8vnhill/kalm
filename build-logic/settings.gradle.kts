/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

// region ROOT PROJECT NAME
// This name appears in IDEs, build output, and when publishing artifacts.
rootProject.name = "build-logic"
// endregion

// region PLUGIN RESOLUTION MANAGEMENT
// Controls how Gradle locates plugins declared in `plugins {}` blocks,
// such as those in convention plugins or build logic scripts.
pluginManagement {
    repositories {
        gradlePluginPortal() // Primary source for official and community Gradle plugins
        mavenCentral()       // Fallback for plugins published to Maven Central
    }
}
// endregion

// region DEPENDENCY RESOLUTION MANAGEMENT

// Configures central repository resolution and version catalogs.
// Ensures consistent dependency resolution across all modules.
@Suppress("UnstableApiUsage") // Needed for `repositoriesMode`, which is still incubating
dependencyResolutionManagement {
    // Prefer repositories declared here over those declared in individual build scripts.
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    // Define a shared version catalog named `libs`, making dependency and plugin versions consistent.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
// endregion

// region TOOLCHAIN RESOLUTION

// Toolchain resolution support via Foojay API
//
// Adds automatic resolution of JDKs from Foojay when using toolchains.
// Recommended in clean environments or CI where the JDK must be downloaded.
// See: https://docs.gradle.org/current/userguide/toolchains.html#sub:download_repositories
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
// endregion
