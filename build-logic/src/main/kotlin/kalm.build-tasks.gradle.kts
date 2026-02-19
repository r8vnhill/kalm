/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import tasks.SyncVersionPropertiesTask

val allowUnstableCoordinates: List<String> = providers
    .gradleProperty("kalm.dependencyUpdates.unstableAllowlist")
    .orNull
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?: emptyList()

fun String.matchesCoordinatePattern(group: String, name: String): Boolean {
    val parts = split(":", limit = 2)
    if (parts.size != 2) {
        return false
    }
    val groupPattern = parts[0]
    val namePattern = parts[1]
    val groupMatches = groupPattern == "*" || groupPattern == group
    val nameMatches = namePattern == "*" || namePattern == name
    return groupMatches && nameMatches
}

fun isAllowedUnstableCoordinate(group: String, name: String): Boolean =
    allowUnstableCoordinates.any { it.matchesCoordinatePattern(group, name) }

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
        val isPreRelease = preRelease.containsMatchIn(v)
        val isAllowlisted = isAllowedUnstableCoordinate(candidate.group, candidate.module)
        isPreRelease && !isAllowlisted
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
val dependencyUpdatesNoParallel = "dependencyUpdatesNoParallel"

val dependencyUpdatesNoParallelTask: TaskProvider<Task> = tasks.register(
    dependencyUpdatesNoParallel
) {
    group = "dependencies"
    description = "Runs dependencyUpdates. Use --no-parallel at invocation time when required."
    dependsOn(tasks.named("dependencyUpdates"))
}

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
        dependencyUpdatesNoParallelTask
    )
}

/**
 * ## Dependency Locking Helpers
 *
 * These tasks encode common lockfile workflows so contributors do not need to memorize long commands.
 * They print copy-paste commands because `--write-locks` is a command-line switch, not a task input.
 */
tasks.register("locksWriteAll") {
    group = "dependencies"
    description = "Prints the recommended command to refresh all dependency lockfiles."
    notCompatibleWithConfigurationCache("Guidance task; no need to store configuration cache state.")
    doLast {
        logger.lifecycle("./gradlew preflight --write-locks --no-parallel")
    }
}

tasks.register("locksCliHelp") {
    group = "dependencies"
    description = "Prints examples for the lock workflow CLI wrapper."
    notCompatibleWithConfigurationCache("Guidance task; no need to store configuration cache state.")
    doLast {
        logger.lifecycle("./scripts/gradle/Invoke-LocksCli.ps1 write-module --module :core")
        logger.lifecycle(
            "./scripts/gradle/Invoke-LocksCli.ps1 write-configuration --module :core --configuration testRuntimeClasspath"
        )
    }
}

tasks.register("locksDiff") {
    group = "dependencies"
    description = "Prints a git diff command for lockfiles."
    notCompatibleWithConfigurationCache("Guidance task; no need to store configuration cache state.")
    doLast {
        logger.lifecycle("git diff -- **/gradle.lockfile settings-gradle.lockfile")
    }
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
 * - Reads the current version catalog state.
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
 * 2. syncVersionProperties
 * 3. syncBuildLogicVersionProperties
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
        syncVersionProperties,
        syncBuildLogicVersionProperties
    )
}
