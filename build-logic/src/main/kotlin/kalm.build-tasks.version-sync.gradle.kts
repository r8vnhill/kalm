/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

import tasks.SyncVersionPropertiesTask
import org.gradle.api.file.RegularFile
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

/**
 * ## Version property synchronization
 *
 * This script wires tasks that **copy versions from the version catalog** (`gradle/libs.versions.toml`) into one or
 * more `gradle.properties` files.
 *
 * The goal is to keep *selected* properties in sync with catalog aliases while leaving everything else in those
 * property files untouched.
 *
 * ## What gets synced
 *
 * Only the keys present in [versionPropertyMappings] are updated. Each mapping links to a:
 *
 * - `gradle.properties` key (left)
 * - version catalog alias (right)
 *
 * ### Example
 *
 * ```
 * "plugin.foojay-resolver.version" -> alias "foojay-resolver"
 * ```
 *
 * ## Why this exists
 *
 * - **Single source of truth:** the version catalog remains canonical.
 * - **Avoid drift:** prevents root and build-logic version properties from diverging.
 * - **Convention plugin safety:** build-logic (convention plugins) often needs the same versions as the main build.
 *
 * ## Behavior & guarantees
 *
 * - Reads versions from the version catalog.
 * - Writes the mapped keys to the target `gradle.properties`.
 * - Preserves unrelated keys and formatting as much as the underlying task implementation allows.
 * - Designed to be **idempotent**: running twice should produce no further changes after the first sync.
 *
 * ## Task graph design notes
 *
 * This plugin keeps task relationships minimal:
 *
 * - Direct task dependencies are declared only for the aggregate `syncVersions` task.
 * - Leaf sync tasks are independent to preserve parallelism and avoid hidden coupling.
 * - Each sync task is configured via providers and local task registration helpers.
 *
 * ## References
 *
 * - Best Practices for Tasks. Retrieved March 4, 2026,
 *   from https://docs.gradle.org/current/userguide/best_practices_tasks.html
 * - Cédric Champeau’s blog: A Gradle quickie: Properly using dependsOn. Retrieved March 4, 2026,
 *   from https://melix.github.io/blog/2021/10/gradle-quickie-dependson.html
 * - Task Basics. Retrieved March 4, 2026,
 *   from https://docs.gradle.org/current/userguide/task_basics.html
 *
 *
 * ## Tasks created
 *
 * - [syncVersionProperties]: syncs the root `gradle.properties`.
 * - [syncBuildLogicVersionProperties]: syncs `build-logic/gradle.properties`.
 * - `syncVersions`: convenience aggregator task that runs both.
 */
val versionPropertyMappings = mapOf(
    "plugin.foojay-resolver.version" to "foojay-resolver"
)

/**
 * Relative path (from the root project directory) of the canonical version catalog used as the single source of truth
 * for versions.
 */
val versionCatalog = "gradle/libs.versions.toml"

val projectDir = rootProject.layout.projectDirectory
val catalogFile = projectDir.file(versionCatalog)

/**
 * Root project's `gradle.properties`. This file typically carries versions and build-wide flags.
 */
val rootPropertiesFile = projectDir.file("gradle.properties")

/**
 * `gradle.properties` used by build logic (convention plugins).
 *
 * Keeping this file aligned with the root catalog prevents situations where build logic resolves different
 * plugin/library versions than the main build.
 */
val buildLogicPropertiesFile = projectDir.file("build-logic/gradle.properties")

/**
 * Registers a [SyncVersionPropertiesTask] that synchronizes a target properties file against the canonical version
 * catalog.
 *
 * ## Usage
 *
 * Use this helper to register additional sync tasks when more `gradle.properties` files need to mirror the catalog.
 *
 * ### Example 1: Register a sync task for another properties file
 *
 * ```kotlin
 * val extraProps = projectDir.file("some/other/gradle.properties")
 *
 * val syncExtra = tasks.registerSync(
 *   name = "syncExtraVersionProperties",
 *   propertiesFile = extraProps
 * )
 * ```
 *
 * @receiver Container used to register the task.
 * @param name Task name.
 * @param propertiesFile Target `gradle.properties` file to update.
 * @return The registered task provider.
 */
fun TaskContainer.registerSync(
    name: String,
    propertiesFile: RegularFile
): TaskProvider<SyncVersionPropertiesTask> = register<SyncVersionPropertiesTask>(name) {
    propertyMappings.set(versionPropertyMappings)
    versionCatalogFile.set(catalogFile)
    this.propertiesFile.set(propertiesFile)
}

/**
 * ## syncVersionProperties
 *
 * Synchronizes mapped entries in the root `gradle.properties` with versions in the canonical version catalog
 * (`gradle/libs.versions.toml`).
 *
 * ## Invariants
 *
 * - The version catalog is the single source of truth.
 * - The root `gradle.properties` mirrors *only* the mapped keys.
 * - Unmapped keys are not modified.
 *
 * ## Safety
 *
 * - **Idempotent:** if values already match, no changes should be produced.
 * - **Non-destructive:** unrelated properties remain intact.
 */
val syncVersionProperties = tasks.registerSync(
    name = "syncVersionProperties",
    propertiesFile = rootPropertiesFile
)

/**
 * ## syncBuildLogicVersionProperties
 *
 * Synchronizes mapped entries in `build-logic/gradle.properties` with the same canonical catalog used by the main
 * build.
 *
 * ## Rationale
 *
 * Convention plugins and build logic must stay aligned with the versions declared by the root build to avoid:
 *
 * - compiling build logic with different plugin/library versions
 * - inconsistent behavior between build logic and the main project
 *
 * ## Execution model
 *
 * This task is intentionally independent from [syncVersionProperties]. Both read from the same canonical catalog and
 * write to different files, so no direct ordering constraint is needed.
 */
val syncBuildLogicVersionProperties = tasks.registerSync(
    name = "syncBuildLogicVersionProperties",
    propertiesFile = buildLogicPropertiesFile
)

/**
 * Aggregates all version synchronization tasks into a single entry point.
 *
 * Running `syncVersions` ensures both the root and build-logic `gradle.properties` files are aligned with the version
 * catalog.
 */
tasks.register("syncVersions") {
    group = "versioning"
    description = "Synchronize mapped gradle.properties values with the version catalog."
    dependsOn(syncVersionProperties, syncBuildLogicVersionProperties)
}
