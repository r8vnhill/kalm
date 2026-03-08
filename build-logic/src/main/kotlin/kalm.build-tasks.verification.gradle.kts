/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin

private val verifyAllTaskName = "verifyAll"
private val preflightTaskName = "preflight"
private val syncWorkspaceMetadataTaskName = "syncWorkspaceMetadata"
private val verificationTaskNames = setOf("test", "detekt", "apiCheck")

fun Project.wireVerificationTasks(
    aggregate: TaskProvider<out Task>,
    taskNames: Set<String>
) {
    subprojects {
        tasks.configureEach {
            if (name in taskNames) {
                aggregate.configure {
                    dependsOn(this@configureEach)
                }
            }
        }
    }
}

/**
 * ## verifyAll
 *
 * Root-level verification aggregation task.
 *
 * Dynamically wires subproject tasks:
 *
 * - test
 * - detekt
 * - apiCheck
 *
 * ### Design Principles
 *
 * - No hardcoded project paths.
 * - Compatible with optional convention plugins.
 * - Uses lazy task matching.
 * - Preserves configuration avoidance.
 *
 * ### CI Role
 *
 * Primary quality gate prior to:
 *
 * - Release
 * - Dependency updates
 * - Lockfile refresh
 */
val verifyAll = tasks.register<DefaultTask>(verifyAllTaskName) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs tests, static analysis, and API compatibility checks in one go."
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
wireVerificationTasks(verifyAll, verificationTaskNames)

/**
 * ## preflight
 *
 * Read-only release-readiness workflow.
 *
 * ### Orchestrates
 *
 * 1. `verifyAll`
 *
 * ### Intended Usage
 *
 * - Local pre-push validation
 * - CI merge gate
 * - Dependency review cycle
 *
 * ### Design Philosophy
 *
 * "If preflight passes, the build is releasable."
 *
 * Ensures:
 *
 * - Tests pass
 * - Static analysis is clean
 * - API compatibility holds
 */
tasks.register<DefaultTask>(preflightTaskName) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs release-readiness verification gates without modifying workspace files."
    dependsOn(verifyAll)
}

/**
 * ## syncWorkspaceMetadata
 *
 * Explicit workspace-mutation lifecycle task for version/property synchronization.
 *
 * ### Orchestrates
 *
 * 1. `syncVersionProperties`
 * 2. `syncBuildLogicVersionProperties`
 *
 * ### Intended Usage
 *
 * - Refreshing mirrored version properties after catalog changes
 * - Preparing the workspace before lockfile or release workflows
 */
tasks.register<DefaultTask>(syncWorkspaceMetadataTaskName) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Synchronizes workspace metadata files derived from the version catalog."
    dependsOn(
        tasks.named("syncVersionProperties"),
        tasks.named("syncBuildLogicVersionProperties")
    )
}
