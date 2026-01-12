/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import tasks.SyncVersionPropertiesTask

/**
 * Configures the Gradle dependency updates task (ben-manes/gradle-versions-plugin).
 *
 * Policy:
 * - Rejects all pre-release versions (alpha, beta, RC, preview, snapshot, etc.)
 * - Only recommends stable releases for version upgrades
 * - Generates both JSON and plain text reports
 *
 * Constraints:
 * - Incompatible with Gradle configuration cache (inspects live configurations)
 * - Reports written to build/dependencyUpdates/
 * - Used by dependencyMaintenance composite task
 */
tasks.withType<DependencyUpdatesTask>().configureEach {
    rejectVersionIf {
        val v = candidate.version.lowercase()
        listOf("alpha", "beta", "rc", "cr", "m", "milestone", "preview", "eap", "snapshot")
            .any(v::contains)
    }

    checkForGradleUpdate = true
    outputFormatter = "json,plain"
    outputDir = layout.buildDirectory.dir("dependencyUpdates").get().asFile.toString()
    reportfileName = "report"
    notCompatibleWithConfigurationCache(
        "This task inspects configurations, which breaks configuration cache compatibility."
    )
}

/**
 * Mapping of gradle.properties entries to their corresponding version catalog keys.
 * Used by syncVersionProperties and syncBuildLogicVersionProperties tasks to keep
 * property files in sync with gradle/libs.versions.toml.
 */
val versionPropertyMappings = mapOf(
    "plugin.foojay-resolver.version" to "foojay-resolver"
)

/**
 * Composite task: Runs version catalog updates and generates dependency reports.
 *
 * Depends on:
 * - versionCatalogUpdate: Refreshes gradle/libs.versions.toml with latest versions
 * - dependencyUpdates: Generates upgrade reports (ben-manes plugin)
 *
 * Output: Reports in build/dependencyUpdates/, updated version catalog
 * Usage: ./gradlew dependencyMaintenance
 */
val dependencyMaintenance by tasks.registering {
    group = "dependencies"
    description = "Runs version catalog updates and dependency update reports."
    dependsOn("versionCatalogUpdate", "dependencyUpdates")
}

/**
 * Composite task: Comprehensive verification gate that must pass before release.
 *
 * Dynamically aggregates all subproject tasks:
 * - test: Unit and integration tests
 * - detekt: Static analysis and code quality checks
 * - apiCheck: Kotlin binary compatibility verification (against core.api)
 *
 * Configuration: gradle.projectsEvaluated {} block below populates dependencies
 * Usage: ./gradlew verifyAll (part of preflight)
 * CI/CD: This is the primary quality gate in build pipelines
 */
val verifyAll = tasks.register("verifyAll") {
    group = "verification"
    description = "Runs tests, static analysis, and API compatibility checks in one go."
}

/**
 * Synchronizes root gradle.properties with version catalog entries.
 *
 * Purpose: Keeps selected version variables in sync between gradle/libs.versions.toml
 * and gradle.properties to ensure consistency across build configuration.
 *
 * Constraint: Must run after versionCatalogUpdate to process newly updated versions
 * Mappings: Defined by versionPropertyMappings above
 * Output: Updated gradle.properties file
 */
val syncVersionProperties = tasks.register<SyncVersionPropertiesTask>("syncVersionProperties") {
    propertyMappings.set(versionPropertyMappings)
    versionCatalogFile.set(rootProject.layout.projectDirectory.file("gradle/libs.versions.toml"))
    propertiesFile.set(rootProject.layout.projectDirectory.file("gradle.properties"))
    mustRunAfter("versionCatalogUpdate")
}

/**
 * Synchronizes build-logic/gradle.properties with version catalog entries.
 *
 * Purpose: Same as syncVersionProperties but for the build-logic subproject,
 * ensuring convention plugins have consistent version definitions.
 *
 * Constraint: Must run after versionCatalogUpdate and syncVersionProperties
 * (allows root sync to complete first)
 * Output: Updated build-logic/gradle.properties file
 */
val syncBuildLogicVersionProperties = tasks.register<SyncVersionPropertiesTask>("syncBuildLogicVersionProperties") {
    propertyMappings.set(versionPropertyMappings)
    versionCatalogFile.set(rootProject.layout.projectDirectory.file("gradle/libs.versions.toml"))
    propertiesFile.set(rootProject.layout.projectDirectory.file("build-logic/gradle.properties"))
    mustRunAfter("versionCatalogUpdate", "syncVersionProperties")
}

/**
 * Lifecycle callback: Dynamically wires all subproject quality tasks into verifyAll.
 *
 * Runs after project evaluation phase to discover:
 * - All test tasks (unit/integration testing)
 * - All detekt tasks (static analysis)
 * - All apiCheck tasks (binary compatibility validation)
 *
 * Rationale: Subprojects may opt into convention plugins conditionally;
 * this ensures verifyAll adapts to actual project configuration without
 * hardcoding task paths in the root build script.
 *
 * Performance: Deduplicates discovered paths to avoid redundant task runs.
 */
gradle.projectsEvaluated {
    val detektPaths = subprojects.mapNotNull { it.tasks.findByName("detekt")?.path }
    val apiCheckPaths = subprojects.mapNotNull { it.tasks.findByName("apiCheck")?.path }
    val testPaths = subprojects.mapNotNull { it.tasks.findByName("test")?.path }
    val additional = (detektPaths + apiCheckPaths + testPaths).distinct()

    if (additional.isNotEmpty()) {
        tasks.named("verifyAll").configure {
            dependsOn(additional)
        }
    }
}

/**
 * Master composite task: Full pre-release workflow and dependency auditing.
 *
 * Orchestrates:
 * 1. verifyAll: Quality gates (tests, detekt, API checks)
 * 2. dependencyMaintenance: Version updates and reports
 * 3. syncVersionProperties: Sync root gradle.properties
 * 4. syncBuildLogicVersionProperties: Sync build-logic gradle.properties
 *
 * Use cases:
 * - Local pre-commit: Validate code quality before pushing
 * - CI/CD gate: Primary release readiness check
 * - Dependency review: Check for available updates with full verification
 *
 * Duration: Typically 1-2 minutes depending on test suite size
 * Output: Test reports, detekt reports, API diffs, dependency upgrade suggestions
 * Usage: ./gradlew preflight
 */
tasks.register("preflight") {
    group = "verification"
    description = "Runs verification gates and dependency maintenance helpers."
    dependsOn(verifyAll, dependencyMaintenance, syncVersionProperties, syncBuildLogicVersionProperties)
}
