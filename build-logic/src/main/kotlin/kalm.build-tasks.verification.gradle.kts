/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

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
val verifyAll: TaskProvider<Task> = tasks.register("verifyAll") {
    group = "verification"
    description = "Runs tests, static analysis, and API compatibility checks in one go."
}

val verifyTaskNames = setOf("test", "detekt", "apiCheck")

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
subprojects {
    tasks.matching { it.name in verifyTaskNames }.configureEach {
        rootProject.tasks.named("verifyAll").configure {
            dependsOn(this@configureEach)
        }
    }
}

/**
 * ## preflight
 *
 * Master release-readiness workflow.
 *
 * ### Orchestrates
 *
 * 1. `verifyAll`
 * 2. `syncVersionProperties`
 * 3. `syncBuildLogicVersionProperties`
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
 * - Version properties are synchronized
 */
tasks.register("preflight") {
    group = "verification"
    description = "Runs verification gates and dependency maintenance helpers."
    dependsOn(
        verifyAll,
        tasks.named("syncVersionProperties"),
        tasks.named("syncBuildLogicVersionProperties")
    )
}
