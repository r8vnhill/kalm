/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

import tasks.SyncVersionPropertiesTask

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

val versionCatalog = "gradle/libs.versions.toml"

/**
 * ## syncVersionProperties
 *
 * Synchronizes selected `gradle.properties` entries with the canonical version catalog:
 *
 *     gradle/libs.versions.toml
 *
 * ### Invariants
 *
 * - Version catalog is the single source of truth.
 * - Property files mirror selected aliases.
 * - No mutation outside mapped keys.
 *
 * ### Safety
 *
 * - Idempotent.
 * - Does not remove unrelated properties.
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
