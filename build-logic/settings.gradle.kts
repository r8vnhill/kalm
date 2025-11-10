/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

import org.gradle.api.GradleException

// # ROOT PROJECT NAME
// This name appears in IDEs, build output, and when publishing artifacts.
rootProject.name = "build-logic"

// # PLUGIN RESOLUTION MANAGEMENT
// Controls how Gradle locates plugins declared in `plugins {}` blocks, such as those in convention plugins or build logic scripts.
pluginManagement {
    repositories {
        gradlePluginPortal() // Primary source for official and community Gradle plugins
        mavenCentral()       // Fallback for plugins published to Maven Central
    }
}

// # DEPENDENCY RESOLUTION MANAGEMENT
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

// # TOOLCHAIN RESOLUTION
// Adds automatic resolution of JDKs from Foojay when using toolchains.
// Recommended in clean environments or CI where the JDK must be downloaded.
// See: https://docs.gradle.org/current/userguide/toolchains.html#sub:download_repositories
plugins {
    val foojayResolverVersion = providers.gradleProperty("plugin.foojay-resolver.version")
        .orNull
        ?: throw GradleException(
            "Property 'plugin.foojay-resolver.version' is required. " +
                "Define it in gradle.properties or run ':syncVersionProperties' to sync from gradle/libs.versions.toml."
        )

    id("org.gradle.toolchains.foojay-resolver-convention") version foojayResolverVersion
}
