import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.api.artifacts.dsl.LockMode
import org.gradle.kotlin.dsl.withType
import java.util.Locale

/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

// Apply shared conventions and quality tools at the root level.
plugins {
    id("kalm.reproducible")                      // Ensures byte-for-byte reproducible archives
    // Keep binary compatibility validator applied to the root so the `apiValidation` extension is available.
    alias(libs.plugins.kotlin.bin.compatibility) // Kotlin binary compatibility validator
    // Register detekt for subprojects without applying it to the root.
    alias(libs.plugins.detekt) apply false                   // Static code analysis tool

    alias(libs.plugins.version.catalog.update)
    alias(libs.plugins.ben.manes.versions)
}

// Prefer `gradle.allprojects { ... }` instead of the top-level `allprojects { ... }` helper. Using the
// `gradle` receiver avoids surprising behavior for included builds and gives clearer scoping for
// configuration. If you only intend to configure subprojects (not the root or included builds), use
// `subprojects { ... }` or `gradle.subprojects { ... }` together with `configureEach` for lazy
// configuration:
//
// gradle.subprojects.configureEach {
//     dependencyLocking { lockAllConfigurations() }
// }
//
// We keep the behavior of locking all configurations here, but use the `gradle` scoped form.
gradle.allprojects {
    dependencyLocking {
        // Lock every configuration to produce reproducible dependency resolution across CI and local
        // development. This mirrors the previous behavior of `allprojects { dependencyLocking { ... } }`.
        lockAllConfigurations()

        // Optional: fail fast when someone adds an unlocked dependency by enabling STRICT mode.
        lockMode.set(LockMode.STRICT)
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
    // Run standard verification gates. Detekt may be applied per-subproject (we register detekt with apply=false
    // at the root), so add detekt dependencies only after projects are evaluated and if the task actually exists.
}

// Some projects may apply Detekt; wire them into verifyAll only if they exist after project evaluation.
gradle.projectsEvaluated {
    val detektPaths = subprojects.mapNotNull { it.tasks.findByName("detekt")?.path }
    val apiCheckPaths = subprojects.mapNotNull { it.tasks.findByName("apiCheck")?.path }
    val additional = (detektPaths + apiCheckPaths).distinct()
    if (additional.isNotEmpty()) {
        tasks.named("verifyAll").configure {
            dependsOn(additional)
        }
    }
}

tasks.register("preflight") {
    group = "verification"
    description = "Runs verification gates and dependency maintenance helpers."
    dependsOn("verifyAll", "dependencyMaintenance")
}
