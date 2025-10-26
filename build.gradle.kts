import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import nl.littlerobots.gradle.versioncatalogupdate.VersionCatalogUpdateExtension

/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

// Apply shared conventions and quality tools at the root level.
plugins {
    id("kalm.reproducible")                         // Ensures byte-for-byte reproducible archives
    alias { libs.plugins.kotlin.bin.compatibility } // Kotlin binary compatibility validator
    alias { libs.plugins.detekt }                   // Static code analysis tool
    alias { libs.plugins.dependency.updates }       // Reports available dependency upgrades
    alias { libs.plugins.version.catalog.update }   // Writes updated coordinates to the version catalog
}

// Configure Kotlin binary compatibility validation
apiValidation {
    ignoredProjects += listOf(
        // Uncomment when needed
        // "test-utils", "examples"
    )
}

extensions.configure<VersionCatalogUpdateExtension>("versionCatalogUpdate") {
    sortByKey.set(true)
    catalogFile.set(layout.projectDirectory.file("gradle/libs.versions.toml"))
    keep {
        keepUnusedVersions.set(true)
        keepUnusedLibraries.set(true)
    }
}

tasks.withType<DependencyUpdatesTask>().configureEach {
    checkForGradleUpdate = true
    gradleReleaseChannel = "current"
    outputFormatter = "plain"
    rejectVersionIf {
        val candidateStable = candidate.version.isStable()
        val currentStable = currentVersion.isStable()
        candidateStable.not() && currentStable
    }
}

tasks.named("versionCatalogUpdate") {
    mustRunAfter("dependencyUpdates")
}

tasks.register("updateDependencies") {
    group = "dependency management"
    description = "Runs dependency reports and refreshes the version catalog."
    dependsOn("dependencyUpdates", "versionCatalogUpdate")
}

private fun String.isStable(): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { contains(it, ignoreCase = true) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    return stableKeyword || regex.matches(this)
}
