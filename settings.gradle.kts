/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */
// Enable typesafe accessors for the version catalog (generated `libs` accessors)
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
// region PLUGIN MANAGEMENT

// Include local builds that define convention and build logic plugins.
//
// This enables the use of precompiled script plugins (e.g., `kalm.reproducible`) throughout the project without needing
// to publish them to a remote repository.
pluginManagement {
    includeBuild("build-logic") // Reusable precompiled Gradle plugins for project modules

    repositories {
        mavenCentral()                    // For dependencies from Maven Central
        gradlePluginPortal()              // For resolving external Gradle plugins
    }

    plugins {
        val foojayResolverVersion = providers.gradleProperty("plugin.foojay-resolver.version")
            .getOrElse("1.0.0")

        id("org.gradle.toolchains.foojay-resolver-convention") version foojayResolverVersion
    }
}

// endregion

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention")
}

@Suppress("UnstableApiUsage") // Incubating API used for repository mode and dependency resolution config
dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.PREFER_SETTINGS // Forces using only the repositories declared here

    repositories {
        mavenCentral()
    }

}

// Root project name used in logs and outputs
rootProject.name = "kalm"

// Include project modules
include(":core")
include(":platform")
