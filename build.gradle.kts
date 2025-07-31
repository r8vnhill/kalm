@file:Suppress("SpellCheckingInspection")

/*
 * === Root build configuration (build.gradle.kts) ===
 * - Apply shared conventions and quality tools that the whole build benefits from.
 * - Centralize static analysis (Detekt) and dependency maintenance helpers (VCU + ben-manes).
 */

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import java.util.*

// === Plugins ===
// - keen.reproducible: convention plugin that makes all archives byte-for-byte reproducible.
// - kotlin binary compatibility: validates ABI changes for published modules.
// - detekt: static analysis for Kotlin.
// - version-catalog-update: helps bump versions in libs.versions.toml.
// - ben-manes versions: produces a dependency update report (does not change files).
plugins {
    id("keen.reproducible")

    alias(libs.plugins.kotlin.bin.compatibility)

    // Apply detekt at the root so we can centralize defaults; subprojects still need to apply the plugin.
    alias(libs.plugins.detekt)

    alias(libs.plugins.version.catalog.update)
    alias(libs.plugins.ben.manes.versions)
}

// === Kotlin binary compatibility validation ===
// Ignore non-API modules so ABI validation focuses on published libraries.
apiValidation {
    ignoredProjects += listOf(
        "examples",
        "benchmark"
        // "test-utils"
    )
}

// === Detekt (root defaults) ===
// These defaults are applied to the root project. To propagate to subprojects where the detekt plugin is applied, see
// the `subprojects { plugins.withId("detekt") { … } }` block further below.
detekt {
    // Start from Detekt’s defaults, then layer your config
    buildUponDefaultConfig = true

    config.setFrom(rootProject.files("config/detekt/detekt.yml"))

    // Speed up on multicore machines
    parallel = true
}

val detektPluginId = libs.plugins.detekt.get().pluginId

// Propagate Detekt defaults to subprojects **that apply detekt**.
// This keeps each module’s build file small and consistent.
subprojects {
    plugins.withId(detektPluginId) {
        extensions.configure(DetektExtension::class) {
            buildUponDefaultConfig = true
            config.setFrom(rootProject.files("config/detekt/detekt.yml"))
            parallel = true
        }
    }
}

// Convenience wrapper so `./gradlew lint` runs Detekt (root and subprojects where applied).
tasks.register("lint") {
    group = "verification"
    description = "Runs static analysis (Detekt) across the project."
    dependsOn(tasks.matching { it.name == "detekt" })
}

// === Version Catalog Update (VCU) ===
// Keeps libs.versions.toml tidy and helps auto-bump versions.
versionCatalogUpdate {
    // Sort keys for smaller diffs and readability
    sortByKey.set(true)

    // Keep unused entries while refactoring
    keep { keepUnusedVersions.set(true) }
}

// === Dependency Updates report (ben-manes) ===
// Produces a JSON/Plain report of available updates; pairs nicely with VCU.
tasks.withType<DependencyUpdatesTask>().configureEach {
    // Only accept stable candidates (mirror VCU’s policy)
    rejectVersionIf {
        val v = candidate.version.lowercase(Locale.ROOT)
        listOf("alpha", "beta", "rc", "cr", "m", "milestone", "preview", "eap", "snapshot")
            .any(v::contains)
    }

    // Also report Gradle wrapper updates
    checkForGradleUpdate = true

    outputFormatter = "json,plain"
    outputDir = layout.buildDirectory.dir("dependencyUpdates").get().asFile.toString()
    reportfileName = "report"

    notCompatibleWithConfigurationCache("This task inspects configurations, breaking configuration cache.")
}

// === Dependency maintenance umbrella task ===
// Runs both: update the catalog and generate a report of what’s available.
tasks.register("dependencyMaintenance") {
    group = "dependencies"
    description = "Runs version catalog updates and dependency update reports."
    dependsOn("versionCatalogUpdate")
    dependsOn("dependencyUpdates")
}
