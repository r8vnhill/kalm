/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import tasks.SyncVersionPropertiesTask

// Configures the dependency update task; rejects prerelease versions.
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

val versionPropertyMappings = mapOf(
    "plugin.foojay-resolver.version" to "foojay-resolver"
)

val dependencyMaintenance = tasks.register("dependencyMaintenance") {
    group = "dependencies"
    description = "Runs version catalog updates and dependency update reports."
    dependsOn("versionCatalogUpdate", "dependencyUpdates")
}

val verifyAll = tasks.register("verifyAll") {
    group = "verification"
    description = "Runs tests, static analysis, and API compatibility checks in one go."
}

val syncVersionProperties = tasks.register<SyncVersionPropertiesTask>("syncVersionProperties") {
    propertyMappings.set(versionPropertyMappings)
    versionCatalogFile.set(rootProject.layout.projectDirectory.file("gradle/libs.versions.toml"))
    propertiesFile.set(rootProject.layout.projectDirectory.file("gradle.properties"))
    mustRunAfter("versionCatalogUpdate")
}

val syncBuildLogicVersionProperties = tasks.register<SyncVersionPropertiesTask>("syncBuildLogicVersionProperties") {
    propertyMappings.set(versionPropertyMappings)
    versionCatalogFile.set(rootProject.layout.projectDirectory.file("gradle/libs.versions.toml"))
    propertiesFile.set(rootProject.layout.projectDirectory.file("build-logic/gradle.properties"))
    mustRunAfter("versionCatalogUpdate", "syncVersionProperties")
}

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

tasks.register("preflight") {
    group = "verification"
    description = "Runs verification gates and dependency maintenance helpers."
    dependsOn(verifyAll, dependencyMaintenance, syncVersionProperties, syncBuildLogicVersionProperties)
}
