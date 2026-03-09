/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */
package kalm

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class BuildTasksVerificationPluginFunctionalTest {

    @TempDir
    lateinit var testProjectDir: Path

    @Test
    fun `verifyAll aggregates matching tasks and ignores missing ones`() {
        createProjectDirectories("a", "b", "c")
        writeSettings(
            """
            rootProject.name = "fixture"
            include(":a", ":b", ":c")
            """.trimIndent()
        )
        writeBuildFile(
            """
            plugins {
                id("kalm.build-tasks.verification")
            }

            allprojects {
                tasks.register("unrelated") {
                    doLast {
                        throw GradleException("unrelated task should not be realized")
                    }
                }
            }
            """.trimIndent()
        )
        writeProjectBuildFile(
            "a",
            """
            tasks.register("test") {
                doLast { println("TASK_A_TEST") }
            }
            """.trimIndent()
        )
        writeProjectBuildFile(
            "b",
            """
            tasks.register("detekt") {
                doLast { println("TASK_B_DETEKT") }
            }
            tasks.register("apiCheck") {
                doLast { println("TASK_B_APICHECK") }
            }
            """.trimIndent()
        )
        writeProjectBuildFile("c", "")

        val result = run("verifyAll")

        assertTaskExecuted(result, ":verifyAll")
        assertTaskSucceeded(result, ":a:test")
        assertTaskSucceeded(result, ":b:detekt")
        assertTaskSucceeded(result, ":b:apiCheck")
        assertFalse(result.output.contains("unrelated task should not be realized"))
    }

    @Test
    fun `preflight depends only on verifyAll`() {
        createProjectDirectories("app")
        writeSettings(
            """
            rootProject.name = "fixture"
            include(":app")
            """.trimIndent()
        )
        writeBuildFile(
            """
            plugins {
                id("kalm.build-tasks.verification")
            }

            tasks.register("syncVersionProperties") {
                doLast { throw GradleException("syncVersionProperties should not run from preflight") }
            }
            tasks.register("syncBuildLogicVersionProperties") {
                doLast { throw GradleException("syncBuildLogicVersionProperties should not run from preflight") }
            }
            """.trimIndent()
        )
        writeProjectBuildFile(
            "app",
            """
            tasks.register("test") {
                doLast { println("VERIFY_ONLY") }
            }
            """.trimIndent()
        )

        val result = run("preflight")

        assertTaskExecuted(result, ":preflight")
        assertTaskSucceeded(result, ":app:test")
        assertFalse(result.output.contains("should not run from preflight"))
    }

    @Test
    fun `syncWorkspaceMetadata wires both sync tasks`() {
        writeSettings("rootProject.name = \"fixture\"")
        writeBuildFile(
            """
            plugins {
                id("kalm.build-tasks.verification")
            }

            tasks.register("syncVersionProperties") {
                doLast { println("SYNC_ROOT") }
            }
            tasks.register("syncBuildLogicVersionProperties") {
                doLast { println("SYNC_BUILD_LOGIC") }
            }
            """.trimIndent()
        )

        val result = run("syncWorkspaceMetadata")

        assertTaskSucceeded(result, ":syncWorkspaceMetadata")
        assertTaskSucceeded(result, ":syncVersionProperties")
        assertTaskSucceeded(result, ":syncBuildLogicVersionProperties")
    }

    @Test
    fun `configuration cache remains compatible for verifyAll`() {
        writeSettings("rootProject.name = \"fixture\"")
        writeBuildFile(
            """
            plugins {
                id("kalm.build-tasks.verification")
            }

            tasks.register("test") {
                doLast { println("TEST_EXECUTED") }
            }
            """.trimIndent()
        )

        val first = run("verifyAll", "--configuration-cache")
        val second = run("verifyAll", "--configuration-cache")

        assertTaskExecuted(first, ":verifyAll")
        assertTrue(
            first.output.contains("Configuration cache entry stored.") ||
                first.output.contains("Calculating task graph as no cached configuration is available")
        )
        assertTaskExecuted(second, ":verifyAll")
        assertTrue(second.output.contains("Configuration cache entry reused."))
    }

    private fun run(vararg arguments: String): BuildResult = GradleRunner.create()
        .withProjectDir(testProjectDir.toFile())
        .withArguments(*arguments, "--stacktrace")
        .withPluginClasspath()
        .forwardOutput()
        .build()

    private fun writeSettings(content: String) {
        testProjectDir.resolve("settings.gradle.kts").writeText(content)
    }

    private fun writeBuildFile(content: String) {
        testProjectDir.resolve("build.gradle.kts").writeText(content)
    }

    private fun writeProjectBuildFile(projectPath: String, content: String) {
        testProjectDir.resolve(projectPath).resolve("build.gradle.kts").writeText(content)
    }

    private fun createProjectDirectories(vararg paths: String) {
        paths.forEach { path ->
            testProjectDir.resolve(path).createDirectories()
        }
    }

    private fun assertTaskSucceeded(result: BuildResult, taskPath: String) {
        assertEquals(TaskOutcome.SUCCESS, result.task(taskPath)?.outcome, "Expected $taskPath to succeed")
    }

    private fun assertTaskExecuted(result: BuildResult, taskPath: String) {
        assertTrue(
            result.task(taskPath)?.outcome in setOf(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE, TaskOutcome.FROM_CACHE),
            "Expected $taskPath to complete successfully"
        )
    }
}
