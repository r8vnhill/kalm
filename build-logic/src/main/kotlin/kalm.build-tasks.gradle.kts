/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.withType
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

/**
 * Shared wiki sync task used by the root project.
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

        execOps.exec {
            workingDir(wikiDir)
            commandLine("git", "pull", "origin", "main")
        }

        execOps.exec {
            workingDir(rootDirectory.asFile.get())
            commandLine("git", "add", "wiki")
        }

        println("✓ Wiki synced to latest. Run 'git commit -m \"📚 docs: update wiki submodule\"' to finalize.")
    }
}

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

val dependencyMaintenance = tasks.register("dependencyMaintenance") {
    group = "dependencies"
    description = "Runs version catalog updates and dependency update reports."
    dependsOn("versionCatalogUpdate", "dependencyUpdates")
}

val verifyAll = tasks.register("verifyAll") {
    group = "verification"
    description = "Runs tests, static analysis, and API compatibility checks in one go."
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
    dependsOn(verifyAll, dependencyMaintenance)
}

tasks.register<SyncWikiTask>("syncWiki") {
    group = "documentation"
    description = "Pulls latest wiki changes and stages the submodule pointer."
    wikiDirectory.set(project.layout.projectDirectory.dir("wiki"))
    rootDirectory.set(project.layout.projectDirectory)
}
