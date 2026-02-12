/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import tasks.SyncVersionPropertiesTask

/**
 * ## Dependency Updates Policy (ben-manes/gradle-versions-plugin)
 *
 * Centralized configuration for all [DependencyUpdatesTask] instances.
 *
 * ### Purpose:
 *
 * - Enforce a *stable-only* upgrade policy.
 * - Generate structured upgrade reports for review.
 * - Integrate with the `dependencyMaintenance` composite workflow.
 *
 * ### Version Filtering Policy:
 *
 * - Rejects all known pre-release qualifiers: alpha, beta, rc, cr, milestone, preview, eap, snapshot, mX, etc.
 * - Only stable versions are recommended in upgrade reports.
 *
 * ### Reproducibility Rationale:
 *
 * - Pre-release versions introduce instability and non-determinism.
 * - Stable-only upgrades align with strict dependency locking.
 *
 * ### Reporting:
 *
 * - JSON output -> machine-readable (automation, CI parsing).
 * - Plain output -> human-readable (local inspection).
 * - Written to: build/dependencyUpdates/
 *
 * ### Configuration Cache:
 *
 * - Explicitly marked incompatible.
 * - The task inspects live configurations, which violates configuration cache constraints.
 *
 * ### Workflow:
 *
 *     ./gradlew dependencyUpdates
 *     ./gradlew dependencyMaintenance
 */
tasks.withType<DependencyUpdatesTask>().configureEach {

    /**
     * Regex detecting common pre-release qualifiers.
     *
     * Matches tokens are separated by: `. - +`
     *
     * ## Examples rejected:
     *
     * - 1.0.0-alpha
     * - 2.0.0-RC1
     * - 3.1.0-M2
     * - 4.0.0-preview
     * - 5.0.0-SNAPSHOT
     */
    val preRelease = Regex(
        """(?i)(?:^|[.\-+])(?:alpha|beta|rc|cr|m\d*|milestone|preview|eap|snapshot)(?:$|[.\-+])"""
    )

    rejectVersionIf {
        val v = candidate.version.lowercase()
        preRelease.containsMatchIn(v)
    }

    /**
     * Also checks for newer Gradle releases. Useful for proactively tracking wrapper updates.
     */
    checkForGradleUpdate = true

    /**
     * Dual-format output:
     * - JSON for CI tooling
     * - Plain text for humans
     */
    outputFormatter = "json,plain"
    reportfileName = "report"

    /**
     * Lazily resolves the output directory at execution time (avoids provider realization during configuration).
     */
    doFirst {
        outputDir = layout.buildDirectory
            .dir("dependencyUpdates")
            .get()
            .asFile
            .absolutePath
    }

    notCompatibleWithConfigurationCache(
        "This task inspects configurations, which breaks configuration cache compatibility."
    )
}

/**
 * ## Version Property Synchronization
 *
 * Maps selected gradle.properties entries to version catalog aliases.
 *
 * ### Purpose:
 *
 * - Maintain a single source of truth: gradle/libs.versions.toml
 * - Avoid version drift between:
 *     - version catalog
 *     - root gradle.properties
 *     - build-logic/gradle.properties
 *
 * ### Example mapping:
 *
 *     "plugin.foojay-resolver.version" -> alias "foojay-resolver"
 *
 * ### Behavior:
 *
 * - Reads version from catalog
 * - Updates the property file if out-of-sync
 * - Preserves other properties
 */
val versionPropertyMappings = mapOf(
    "plugin.foojay-resolver.version" to "foojay-resolver"
)

val versionCatalogUpdate = "versionCatalogUpdate"

/**
 * ## dependencyMaintenance
 *
 * Composite lifecycle task for dependency review.
 *
 * ### Responsibilities:
 *
 * 1. Update the version catalog (via versionCatalogUpdate).
 * 2. Generate upgrade reports (dependencyUpdates).
 *
 * ### Output:
 *
 * - Updated gradle/libs.versions.toml
 * - build/dependencyUpdates/report.{json,txt}
 *
 * ### Usage:
 *
 *     ./gradlew dependencyMaintenance
 *
 * ### Intended for:
 *
 * - Scheduled dependency audits
 * - Manual version review
 */
val dependencyMaintenance: TaskProvider<Task> = tasks.register("dependencyMaintenance") {
    group = "dependencies"
    description = "Runs version catalog updates and dependency update reports."
    dependsOn(
        tasks.named(versionCatalogUpdate),
        tasks.named("dependencyUpdates")
    )
}

/**
 * ## verifyAll
 *
 * Root-level verification gate.
 *
 * Lazily aggregates matching subproject tasks:
 * - test
 * - detekt
 * - apiCheck
 *
 * ### Design Goals:
 *
 * - No hardcoded task paths.
 * - Adapts to optional convention plugins.
 * - Avoids eager task realization.
 *
 * ### Usage:
 *
 *     ./gradlew verifyAll
 *
 * ### CI Role:
 *
 * - Primary quality gate before release.
 */
val verifyAll: TaskProvider<Task> = tasks.register("verifyAll") {
    group = "verification"
    description = "Runs tests, static analysis, and API compatibility checks in one go."
}

val versionCatalog = "gradle/libs.versions.toml"

/**
 * ## syncVersionProperties
 *
 * Synchronizes root gradle.properties with version catalog.
 *
 * ### Guarantees:
 *
 * - Catalog remains canonical.
 * - Property files mirror selected aliases.
 *
 * ### Execution Order:
 *
 * - Depends on versionCatalogUpdate.
 *
 * ### Usage:
 *
 *     ./gradlew syncVersionProperties
 */
val syncVersionProperties by tasks.registering(SyncVersionPropertiesTask::class) {
    propertyMappings.set(versionPropertyMappings)
    versionCatalogFile.set(
        rootProject.layout.projectDirectory.file(versionCatalog)
    )
    propertiesFile.set(
        rootProject.layout.projectDirectory.file("gradle.properties")
    )
    dependsOn(tasks.named(versionCatalogUpdate))
}

/**
 * ## syncBuildLogicVersionProperties
 *
 * Same as syncVersionProperties but scoped to build-logic.
 *
 * ### Rationale:
 *
 * - Convention plugins must remain version-aligned with the root version catalog.
 *
 * ### Execution Order:
 *
 * - Depends on syncVersionProperties
 */
val syncBuildLogicVersionProperties = tasks.register<SyncVersionPropertiesTask>("syncBuildLogicVersionProperties") {
    propertyMappings.set(versionPropertyMappings)
    versionCatalogFile.set(
        rootProject.layout.projectDirectory.file(versionCatalog)
    )
    propertiesFile.set(
        rootProject.layout.projectDirectory.file("build-logic/gradle.properties")
    )
    dependsOn(syncVersionProperties)
}

/**
 * ## Dynamic Subproject Wiring
 *
 * Lazily connects quality-related tasks from all subprojects into verifyAll.
 *
 * ### Matching task names:
 *
 * - test
 * - detekt
 * - apiCheck
 *
 * ### Implementation Notes:
 *
 * - Uses `tasks.matching { }.configureEach { }`
 * - Avoids projectsEvaluated lifecycle hook.
 * - Preserves configuration cache friendliness.
 */
subprojects {
    listOf("test", "detekt", "apiCheck").forEach { taskName ->
        tasks.matching { it.name == taskName }.configureEach {
            rootProject.tasks.named("verifyAll").configure {
                dependsOn(this@configureEach)
            }
        }
    }
}

/**
 * ## preflight
 *
 * Master release-readiness workflow.
 *
 * ### Orchestrates:
 *
 * 1. verifyAll
 * 2. dependencyMaintenance
 * 3. syncVersionProperties
 * 4. syncBuildLogicVersionProperties
 *
 * ### Intended Use Cases:
 *
 * - Local pre-push validation
 * - CI release gate
 * - Dependency audit cycle
 *
 * ### Command:
 *
 *     ./gradlew preflight
 *
 * ### Produces:
 *
 * - Test reports
 * - Static analysis results
 * - API compatibility verification
 * - Dependency upgrade reports
 * - Synchronized property files
 */
tasks.register("preflight") {
    group = "verification"
    description = "Runs verification gates and dependency maintenance helpers."
    dependsOn(
        verifyAll,
        dependencyMaintenance,
        syncVersionProperties,
        syncBuildLogicVersionProperties
    )
}
