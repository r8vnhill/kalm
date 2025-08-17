/*
 * Copyright (c) 2025,
 * 2-Clause BSD License.
 *
 * This is the *included build* that produces convention/precompiled script plugins.
 * It is its own Gradle build, so it needs its own plugin/dependency repositories and its own toolchain resolver plugin.
 */

rootProject.name = "build-logic"

// === PLUGIN RESOLUTION MANAGEMENT ===
// Where Gradle resolves plugins declared in `plugins {}` blocks *for this build*.
pluginManagement {
    repositories {
        gradlePluginPortal() // Main source of Gradle plugins
        mavenCentral() // Fallback for plugins published to Maven Central
    }

    // Read the version from gradle.properties (falls back to 1.0.0)
    val foojayResolverVersion: String = providers
        .gradleProperty("version.foojay.resolver")
        .orElse("1.0.0")
        .apply {
            if (!isPresent) {
               logger.warn("No 'version.foojay.resolver' property found. Using default.")
            }
        }
        .get()

    // Declare the settings plugin and its version here
    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version foojayResolverVersion
    }
}

// === DEPENDENCY RESOLUTION ===
// Centralize repositories and version catalogs for *this* build.
//
// We choose FAIL_ON_PROJECT_REPOS to forbid repositories declared elsewhere.
// This is stricter than PREFER_SETTINGS and improves reproducibility.
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        gradlePluginPortal()
        mavenCentral()

        // Repository content filters to reduce accidental lookups
        mavenCentral {
            content {
                // Block plugin groups that should come from the plugin portal
                excludeGroupByRegex("org\\.gradle\\..*")
            }
        }
    }

    // Use the shared version catalog from the root build to keep versions aligned.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

// === TOOLCHAIN RESOLUTION ===
// Add Foojay resolver here so this included build can auto-resolve JDKs for its own toolchain usage, independent of the
// root build.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention")
}

// ===  BUILD CACHE ===
// Centralized cache config improves local + CI performance and reproducibility.
buildCache {
    local {
        isEnabled = true
    }
}
