@file:Suppress("SpellCheckingInspection")

/*
 * ===============================
 * Root build configuration (KTS)
 * ===============================
 *
 * Purpose
 * -------
 * - Apply shared conventions and quality tooling that all subprojects benefit from.
 * - Centralize static analysis (Detekt), code coverage (Kover), dependency hygiene (Version Catalog Update + Ben
 *   Manes), and ABI checks (binary-compatibility).
 *
 * Design
 * ------
 * - Keep things configuration-cache friendly: prefer `configureEach`, avoid lambdas capturing outer scope, and stick to
 *   Provider/Property APIs.
 * - Push reusable conventions into build-logic; leave the root as orchestration glue.
 */

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import plugins.VerifyKeenJavaDefaultTask
import java.util.*

//#region Plugins ======================================================================================================
// - keen.reproducible: reproducible archives (stable timestamps/order).
// - kotlin binary compatibility: validates ABI changes for published modules.
// - detekt: static analysis for Kotlin (root defaults, applied in subprojects where needed).
// - version-catalog-update: keeps libs.versions.toml tidy and helps bump versions.
// - ben-manes versions: reports dependency updates (does NOT change files).
// - kover: multiplatform coverage (configured below).
// - doctor: sanity checks for Gradle builds (misconfig detection).
plugins {
    id("keen.reproducible")

    alias(libs.plugins.kotlin.bin.compatibility)

    // Centralize Detekt default config at root; subprojects opt-in to the plugin.
    alias(libs.plugins.detekt)

    alias(libs.plugins.version.catalog.update)
    alias(libs.plugins.ben.manes.versions)
    alias(libs.plugins.kover)
    alias(libs.plugins.doctor)
}
//#endregion

//#region Kotlin binary-compatibility validation =======================================================================
// Narrow scope to API-bearing projects so ABI checks are signal-y, not noisy.
apiValidation {
    ignoredProjects.addAll(
        listOf(
            projects.examples.name,
            projects.benchmark.name,
            projects.utils.testCommons.name
        )
    )
}
//#endregion

//#region Detekt (root defaults) =======================================================================================
// Root-wide defaults; subprojects applying detekt inherit these (see propagation below).
detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    parallel = true
}

val detektPluginId = libs.plugins.detekt.get().pluginId

//#region Propagate Detekt defaults and attach Kover to JVM modules ----------------------------------------------------
// Keeps individual module scripts small and consistent.
subprojects {
    plugins.withId(detektPluginId) {
        extensions.configure(DetektExtension::class) {
            buildUponDefaultConfig = true
            config.setFrom(rootProject.files("config/detekt/detekt.yml"))
            parallel = true
        }
    }
    // Apply Kover where Kotlin/JVM is present (avoids adding to non-JVM projects).
    plugins.withId("org.jetbrains.kotlin.jvm") {
        apply(plugin = "org.jetbrains.kotlinx.kover")
    }
}
//#endregion

//#region Convenience: `lint` runs all Detekt tasks (root + subprojects) -----------------------------------------------
// Handy for CI or local quick checks.
tasks.register("lint") {
    group = "verification"
    description = "Runs static analysis (Detekt) across the project."
    dependsOn(tasks.matching { it.name == "detekt" })
}
//#endregion
//#endregion

//#region Dependency Updates
// Produces PR-friendly tidy diffs and keeps unused entries during refactoring
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
//#endregion

//#region Kover defaults ===============================================================================================
// Global exclusions for generated/DI glue, etc. Per-module overrides still possible.
kover {
    reports {
        filters {
            excludes {
                packages(".*generated.*", ".*build.*")
                classes(".*Dagger.*", ".*Hilt.*", ".*Module.*", ".*Factory.*")
                annotatedBy("javax.annotation.Generated", "kotlinx.serialization.Serializable")
            }
        }
    }
}
//#endregion

//#region Default Java version consistency check (root ↔ build-logic) ==================================================
// Why: build-logic runs inside the Gradle JVM; we want one source of truth for the “default” Java major version (e.g.,
// 21/22) used by toolchains across the build.
// This fails early if root and build-logic disagree.
//
// Opt-out: -PskipJavaDefaultCheck=true
// Wires into all `assemble` tasks, so CI and local builds enforce it.
// =====================================================================================================================

// Single provider for the skip flag (avoid duplicate lookups).
val skipJavaDefaultCheck = providers.gradleProperty("skipJavaDefaultCheck")
    .map(String::toBoolean)
    .orElse(false)

// Register the verification task implemented in build-logic (plugin package).
val verifyKeenJavaDefault by tasks.registering(VerifyKeenJavaDefaultTask::class) {
    group = "verification"
    description = "Ensures keen.java.default is present in root and build-logic gradle.properties and that both match."

    // Input files tracked with RELATIVE path sensitivity by the task itself.
    rootProps.set(layout.projectDirectory.file("gradle.properties"))
    buildLogicProps.set(layout.projectDirectory.file("build-logic/gradle.properties"))

    // Honor the global skip flag; the task has a default false convention already.
    skipCheck.set(skipJavaDefaultCheck)
}

// Make every `assemble` in the build depend on the consistency check.
// This stays CC-friendly (no captured outer state) because we reference a named task.
allprojects {
    tasks.matching { it.name == "assemble" }.configureEach {
        dependsOn(rootProject.tasks.named("verifyKeenJavaDefault"))
    }
}
//#endregion
