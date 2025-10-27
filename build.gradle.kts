import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.kotlin.dsl.withType
import java.util.Locale

/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

// Apply shared conventions and quality tools at the root level.
plugins {
    id("kalm.reproducible")                         // Ensures byte-for-byte reproducible archives
    alias { libs.plugins.kotlin.bin.compatibility } // Kotlin binary compatibility validator
    alias { libs.plugins.detekt }                   // Static code analysis tool

    alias(libs.plugins.version.catalog.update)
    alias(libs.plugins.ben.manes.versions)
}

allprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}

// Configure Kotlin binary compatibility validation
apiValidation {
    ignoredProjects += listOf(
        // Uncomment when needed
        // "test-utils", "examples"
    )
}

versionCatalogUpdate {
    sortByKey.set(true)
    keep { keepUnusedVersions.set(true) }
}

// Generates JSON/plain reports of available updates; pairs well with VCU.
// Note: Not config-cache compatible by the nature of its work.
tasks.withType<DependencyUpdatesTask>().configureEach {
    // Accept stable candidates only (mirrors VCU policy).
    rejectVersionIf {
        val v = candidate.version.lowercase(Locale.ROOT)
        listOf("alpha", "beta", "rc", "cr", "m", "milestone", "preview", "eap", "snapshot")
            .any(v::contains)
    }
    checkForGradleUpdate = true
    outputFormatter = "json,plain"
    outputDir = layout.buildDirectory.dir("dependencyUpdates").get().asFile.toString()
    reportfileName = "report"
    notCompatibleWithConfigurationCache("This task inspects configurations, breaking configuration cache.")
}

// Runs both VCU and the updates report.
tasks.register("dependencyMaintenance") {
    group = "dependencies"
    description = "Runs version catalog updates and dependency update reports."
    dependsOn("versionCatalogUpdate", "dependencyUpdates")
}

tasks.register("verifyAll") {
    group = "verification"
    description = "Runs tests, static analysis, and API compatibility checks in one go."
    dependsOn("check", "detekt", "apiCheck")
}

tasks.register("preflight") {
    group = "verification"
    description = "Runs verification gates and dependency maintenance helpers."
    dependsOn("verifyAll", "dependencyMaintenance")
}
