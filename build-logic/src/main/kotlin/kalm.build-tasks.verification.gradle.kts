/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin

private enum class VerificationTask(val taskName: String) {
    VERIFY_ALL("verifyAll"),
}

/**
 * Canonical name of the root verification aggregation task.
 *
 * This task is intentionally declared as a constant so the name is defined in one place and can be reused consistently
 * across registrations and wiring logic.
 */
private val verifyAllTaskName = "verifyAll"

/**
 * Canonical name of the read-only release-readiness task.
 *
 * `preflight` is meant to provide a stable entry point for local validation and CI checks that
 * should not mutate the workspace.
 */
private val preflightTaskName = "preflight"

/**
 * Canonical name of the explicit workspace-synchronization task.
 *
 * Unlike [preflight], this task is allowed to orchestrate workspace mutations when metadata files
 * derived from the version catalog must be refreshed.
 */
private val syncWorkspaceMetadataTaskName = "syncWorkspaceMetadata"

/**
 * Names of verification tasks that may exist in subprojects and should be aggregated into
 * [verifyAllTaskName].
 *
 * The set is intentionally small and based on stable lifecycle-style task names so subprojects can
 * opt in simply by exposing tasks with these names.
 */
private val verificationTaskNames = setOf("test", "detekt", "apiCheck")

/**
 * Wires matching tasks from every subproject into an aggregate task.
 *
 * This helper scans all subprojects and connects any task whose name belongs to [taskNames] as a
 * dependency of [aggregate]. Missing tasks are ignored naturally, which keeps the build compatible
 * with optional plugins and heterogeneous subprojects.
 *
 * The wiring is performed lazily through task matching so the build does not need to eagerly realize
 * tasks or rely on late lifecycle hooks such as `projectsEvaluated`.
 *
 * ## Usage:
 * Use this function to create root-level lifecycle tasks that aggregate a common set of tasks exposed
 * by subprojects.
 *
 * ### Example 1: Aggregate verification tasks
 * ```kotlin
 * val verifyAll = tasks.register<DefaultTask>("verifyAll")
 * wireVerificationTasks(verifyAll, setOf("test", "detekt", "apiCheck"))
 * ```
 *
 * ### Example 2: Aggregate custom analysis tasks
 * ```kotlin
 * val analyseAll = tasks.register<DefaultTask>("analyseAll")
 * wireVerificationTasks(analyseAll, setOf("lint", "detekt"))
 * ```
 *
 * @param aggregate Root task that should depend on matching subproject tasks.
 * @param taskNames Names of subproject tasks that should be attached to [aggregate].
 */
fun Project.wireVerificationTasks(
    aggregate: TaskProvider<out Task>,
    taskNames: Set<String>
) {
    subprojects {
        tasks
            .matching { it.name in taskNames }
            .all {
                aggregate.configure {
                    dependsOn(this@all)
                }
            }
    }
}

/**
 * Root-level verification aggregation task.
 *
 * `verifyAll` acts as the main verification entry point for the workspace. Rather than requiring
 * contributors or CI jobs to invoke several quality-related tasks manually, it centralizes them
 * behind a single lifecycle task.
 *
 * Tasks are discovered dynamically in subprojects and attached only when they exist. This makes the
 * task safe for multi-project builds where some modules may not apply all verification-related
 * plugins.
 *
 * ### Aggregated task names
 * - `test`
 * - `detekt`
 * - `apiCheck`
 *
 * ### Design notes
 * - Avoids hardcoded subproject paths.
 * - Works with optional convention plugins.
 * - Preserves lazy configuration.
 * - Remains friendly to configuration caching.
 *
 * ### Typical use cases
 * - Local quality validation before pushing changes.
 * - CI quality gates for merge requests.
 * - Release validation.
 * - Dependency or lockfile maintenance workflows.
 */
val verifyAll = tasks.register<DefaultTask>(verifyAllTaskName) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs tests, static analysis, and API compatibility checks in one go."
}

/**
 * Lazily connects subproject verification tasks to [verifyAll].
 *
 * This call expands [verifyAll] into a workspace-wide verification entry point by wiring every
 * subproject task whose name appears in [verificationTaskNames].
 *
 * Because the wiring relies on task matching instead of eager lookup, it remains robust when:
 * - subprojects are added or removed,
 * - plugins are applied conditionally,
 * - not every module exposes the same verification tasks.
 */
wireVerificationTasks(verifyAll, verificationTaskNames)

/**
 * Read-only release-readiness lifecycle task.
 *
 * `preflight` represents the verification workflow that should pass before considering the current
 * state of the build ready for integration or release-oriented work. It is intentionally read-only:
 * it validates the workspace without updating generated files or synchronizing metadata.
 *
 * ### Current orchestration
 * 1. [verifyAllTaskName]
 *
 * ### What passing preflight means
 * - Tests pass.
 * - Static analysis checks pass.
 * - API compatibility checks pass when applicable.
 *
 * ### Typical use cases
 * - Local pre-push validation.
 * - CI merge gates.
 * - Validation before dependency updates are merged.
 */
tasks.register<DefaultTask>(preflightTaskName) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs release-readiness verification gates without modifying workspace files."
    dependsOn(verifyAll)
}

/**
 * Explicit workspace-mutation lifecycle task for metadata synchronization.
 *
 * `syncWorkspaceMetadata` groups tasks that refresh files derived from the version catalog or other
 * canonical workspace metadata sources. It exists to make mutation explicit: callers can choose
 * whether they want a read-only workflow such as [preflightTaskName] or a synchronization workflow
 * that updates tracked files.
 *
 * ### Current orchestration
 * 1. `syncVersionProperties`
 * 2. `syncBuildLogicVersionProperties`
 *
 * ### Typical use cases
 * - Refreshing mirrored version properties after catalog changes.
 * - Preparing the workspace before lockfile updates.
 * - Normalizing derived metadata before release preparation.
 *
 * ### Design notes
 * - Keeps workspace mutations behind an explicit lifecycle task.
 * - Improves discoverability of metadata synchronization steps.
 * - Separates validation concerns from file-updating concerns.
 */
tasks.register<DefaultTask>(syncWorkspaceMetadataTaskName) {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Synchronizes workspace metadata files derived from the version catalog."
    dependsOn(
        tasks.named("syncVersionProperties"),
        tasks.named("syncBuildLogicVersionProperties")
    )
}
