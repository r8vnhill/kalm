/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.kotlin.dsl.withType
import org.gradle.process.ExecOperations
import java.util.Locale
import javax.inject.Inject

/**
 * # Root Build Logic
 *
 * This file applies root-level plugins and conventions shared across all subprojects.
 * It enforces reproducible builds, dependency locking, and version maintenance routines.
 *
 * ## Summary
 * - Enables dependency locking with `LockMode.STRICT` across all projects.
 * - Configures binary compatibility validation.
 * - Adds helper tasks for dependency and verification maintenance.
 * - Restricts unstable dependency updates (alpha, beta, RC, etc.).
 *
 * ## Related documentation
 * - [Gradle Dependency Locking](https://docs.gradle.org/current/userguide/dependency_locking.html)
 * - [Ben Manes Versions Plugin](https://github.com/ben-manes/gradle-versions-plugin)
 * - [Version Catalog Update Plugin](https://github.com/littlerobots/version-catalog-update-plugin)
 * - [Kotlin Binary Compatibility Validator](https://github.com/Kotlin/binary-compatibility-validator)
 */

plugins {
    // Ensures byte-for-byte reproducible archives and metadata (via kalm.reproducible plugin).
    id("kalm.reproducible")

    // Applies dependency locking conventions without relying on allprojects{}.
    id("kalm.dependency-locking")

    // Provides API compatibility checks for public Kotlin APIs.
    alias(libs.plugins.kotlin.bin.compatibility)

    // Registers Detekt for subprojects without applying it to the root.
    alias(libs.plugins.detekt) apply false

    // Adds dependency maintenance helpers:
    alias(libs.plugins.version.catalog.update) // Version Catalog Update (VCU)
    alias(libs.plugins.ben.manes.versions) // Ben Manes dependency updates report
}

/**
 * # Kotlin Binary Compatibility Validation
 *
 * Ensures that changes to public APIs do not break binary compatibility.
 * Projects listed in `ignoredProjects` are skipped (e.g., test utilities or examples).
 */
apiValidation {
    ignoredProjects += listOf(
        // Uncomment to exclude optional modules from binary compatibility validation.
        // "test-utils", "examples"
    )
}

/**
 * # Version Catalog Update (VCU)
 *
 * Automatically sorts version keys alphabetically and preserves unused entries.
 * This helps maintain a clean, consistent `libs.versions.toml`.
 */
versionCatalogUpdate {
    sortByKey.set(true)
    keep {
        keepUnusedVersions.set(true)
    }
}

/**
 * # Dependency Updates (Ben Manes)
 *
 * Generates reports (JSON and plain text) of available dependency upgrades.
 * Non–config-cache compatible due to its introspective behavior.
 */
tasks.withType<DependencyUpdatesTask>().configureEach {
    // Reject non-stable versions: alpha, beta, RC, milestones, etc.
    rejectVersionIf {
        val v = candidate.version.lowercase(Locale.ROOT)
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
 * # dependencyMaintenance
 *
 * Runs both:
 * - `versionCatalogUpdate` (mutates the version catalog)
 * - `dependencyUpdates` (reports outdated dependencies)
 *
 * Used to keep `libs.versions.toml` and the dependency tree up to date.
 */
tasks.register("dependencyMaintenance") {
    group = "dependencies"
    description = "Runs version catalog updates and dependency update reports."
    dependsOn("versionCatalogUpdate", "dependencyUpdates")
}

/**
 * # verifyAll
 *
 * Aggregates verification tasks (tests, static analysis, API checks).
 * Detekt is applied per subproject (`apply false` in the root), so the task dynamically adds
 * dependencies only if those tasks exist.
 */
tasks.register("verifyAll") {
    group = "verification"
    description = "Runs tests, static analysis, and API compatibility checks in one go."
    // Dependencies are attached later after all subprojects are evaluated.
}

/**
 * Dynamically wires Detekt and API validation tasks into `verifyAll`.
 *
 * This ensures that `verifyAll` runs all verification gates only if the corresponding tasks are present in the
 * evaluated projects.
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
 * # preflight
 *
 * Combines verification and dependency maintenance helpers into a single entry point.
 * Ideal for CI or pre-release validation workflows.
 */
tasks.register("preflight") {
    group = "verification"
    description = "Runs verification gates and dependency maintenance helpers."
    dependsOn("verifyAll", "dependencyMaintenance")
}

/**
 * # syncWiki
 *
 * Updates the wiki submodule to latest master and stages the pointer in the main repo.
 * After running this task, commit the submodule update with an appropriate message.
 */
abstract class SyncWikiTask @Inject constructor(
    private val execOps: ExecOperations
) : DefaultTask() {
    
    @get:InputDirectory
    abstract val wikiDirectory: DirectoryProperty
    
    @get:InputDirectory
    abstract val rootDirectory: DirectoryProperty
    
    @TaskAction
    fun sync() {
        val wikiDir = wikiDirectory.asFile.get()
        
        if (!wikiDir.exists()) {
            throw GradleException(
                "Wiki submodule not initialized. Run: git submodule update --init --recursive"
            )
        }
        
        // Pull latest wiki changes
        execOps.exec {
            workingDir(wikiDir)
            commandLine("git", "pull", "origin", "main")
        }
        
        // Stage the submodule pointer
        execOps.exec {
            workingDir(rootDirectory.asFile.get())
            commandLine("git", "add", "wiki")
        }
        
        println("✓ Wiki synced to latest. Run 'git commit -m \"📚 docs: update wiki submodule\"' to finalize.")
    }
}

tasks.register<SyncWikiTask>("syncWiki") {
    group = "documentation"
    description = "Pulls latest wiki changes and stages the submodule pointer."
    wikiDirectory.set(rootProject.layout.projectDirectory.dir("wiki"))
    rootDirectory.set(rootProject.layout.projectDirectory)
}
