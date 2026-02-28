/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import tasks.SyncVersionPropertiesTask

/**
 * ## Unstable Dependency Allowlist
 *
 * Lazily resolves a comma-separated Gradle property: `kalm.dependencyUpdates.unstableAllowlist`
 *
 * ### Format
 *
 * Each entry must follow: `group:name`
 *
 * Wildcards are supported per segment:
 *
 * - `*:*` -> allow all unstable upgrades (discouraged)
 * - `com.example:*` -> allow all modules in a group
 * - `*:core` -> allow all groups for a given module
 *
 * Multiple entries are comma-separated: `com.example:*, org.foo:bar`
 *
 * ### Design Rationale
 *
 * - Uses [Provider] to preserve configuration avoidance.
 * - Parsing is deferred until required.
 * - Keeps dependency policy declarative and CI-configurable.
 *
 * ### Safety
 *
 * - Invalid entries (missing `:`) are ignored by pattern matching.
 * - Empty values are filtered out.
 */
val allowUnstableCoordinates: Provider<List<String>> = providers
    .gradleProperty("kalm.dependencyUpdates.unstableAllowlist")
    .map { raw ->
        raw
            .split(",")
            .map(String::trim)
            .filter(String::isNotEmpty)
    }
    .orElse(emptyList())

/**
 * Matches a `group:name` pattern against the provided coordinates.
 *
 * ## Pattern Rules
 *
 * - Exactly two segments separated by `:`.
 * - `*` matches any value in that segment.
 * - Matching is exact (no partial or regex matching).
 *
 * ## Examples
 *
 *     "com.foo:*".matchesCoordinatePattern("com.foo", "core") == true
 *     "*:core".matchesCoordinatePattern("com.bar", "core") == true
 *     "com.foo:core".matchesCoordinatePattern("com.foo", "api") == false
 *
 * ## Design Constraints
 *
 * - No regex for predictability and performance.
 * - Strict format avoids ambiguous matching behavior.
 */
fun String.matchesCoordinatePattern(group: String, name: String): Boolean {
    val parts = split(":", limit = 2)
    if (parts.size != 2) {
        return false
    }
    val groupPattern = parts[0]
    val namePattern = parts[1]
    val groupMatches = groupPattern == "*" || groupPattern == group
    val nameMatches = namePattern == "*" || namePattern == name
    return groupMatches && nameMatches
}

/**
 * Determines whether a given coordinate is explicitly allowed to receive unstable version recommendations.
 *
 * ## Behavior
 *
 * - Evaluates against the configured allowlist.
 * - Uses exact matching with wildcard support.
 * - Returns `false` if no allowlist entries exist.
 *
 * ## Configuration Cache
 *
 * - Calls `.get()` intentionally inside task configuration logic.
 * - Value is stable for the duration of a build invocation.
 */
fun isAllowedUnstableCoordinate(group: String, name: String): Boolean =
    allowUnstableCoordinates.get().any { it.matchesCoordinatePattern(group, name) }

/**
 * ## Dependency Updates Policy (ben-manes/gradle-versions-plugin)
 *
 * Centralized configuration applied to all [DependencyUpdatesTask] instances.
 *
 * ## Policy Objectives
 *
 * - Enforce a stable-only upgrade policy.
 * - Preserve reproducibility.
 * - Produce structured reports for human + CI review.
 *
 * ## Stability Detection Strategy
 *
 * A version is considered **stable** if:
 *
 * 1. It does NOT contain a known unstable qualifier.
 * 2. OR it contains a known stable qualifier (e.g., GA, FINAL).
 * 3. OR it matches a purely numeric semantic-like pattern.
 *
 * A version is considered **unstable** if it contains tokens like:
 *
 * alpha, beta, rc, cr, mX, milestone, preview, eap, snapshot, dev, nightly, canary
 *
 * Matching is case-insensitive and token-boundary-aware.
 *
 * ## Allowlist Override
 *
 * Coordinates listed in `kalm.dependencyUpdates.unstableAllowlist` are allowed to bypass unstable filtering.
 *
 * ## Reporting
 *
 * ### Outputs:
 *
 * - JSON -> machine-readable for CI automation.
 * - Plain text -> human-readable inspection.
 *
 * ### Location:
 *
 *     build/dependencyUpdates/report.{json,txt}
 *
 * ## Configuration Cache
 *
 * Marked incompatible because:
 *
 * - The task inspects live configurations.
 * - Dependency resolution state is not cache-safe.
 *
 * ## Usage
 *
 *     ./gradlew dependencyUpdates
 *     ./gradlew dependencyMaintenance
 */
tasks.withType<DependencyUpdatesTask>().configureEach {

    /**
     * Regex detecting known unstable qualifiers.
     *
     * Tokens must be separated by: `.`, `-`, or `+`
     *
     * Prevents false positives like: "architect" containing "rc"
     */
    val unstableQualifier = Regex(
        """(?i)(?:^|[.\-+])(?:alpha|beta|rc|cr|m\d*|milestone|preview|eap|snapshot|dev|nightly|canary)(?:$|[.\-+])"""
    )

    /**
     * Explicitly stable markers.
     */
    val stableQualifier = Regex("""(?i)(?:^|[.\-+])(?:final|ga|release)(?:$|[.\-+])""")

    /**
     * Matches numeric or semver-like versions:
     *
     * - 1.0.0
     * - 2.3.1
     * - 1.0.0+meta
     */
    val numericVersion = Regex("""^[0-9]+(?:\.[0-9]+)*(?:[-+][0-9A-Za-z.]+)?$""")

    /**
     * Determines version stability using layered heuristics.
     *
     * ### Order of Evaluation
     *
     * 1. Reject if an unstable token is detected.
     * 2. Accept if an explicit stable token is detected.
     * 3. Accept if purely numeric/semver-like.
     * 4. Otherwise -> unstable.
     */
    fun isStableVersion(version: String): Boolean {
        val normalized = version.trim()
        if (unstableQualifier.containsMatchIn(normalized)) {
            return false
        }
        if (stableQualifier.containsMatchIn(normalized)) {
            return true
        }
        return numericVersion.matches(normalized)
    }

    rejectVersionIf {
        val isPreRelease = !isStableVersion(candidate.version)
        val isAllowlisted = isAllowedUnstableCoordinate(candidate.group, candidate.module)
        isPreRelease && !isAllowlisted
    }

    /**
     * Also checks for newer Gradle releases. Useful for proactively tracking wrapper updates.
     */
    checkForGradleUpdate = true

    /**
     * Dual-format output:
     * - JSON for CI tooling
     * - Plain text for humans
     */
    outputFormatter = "json,plain"
    reportfileName = "report"

    /**
     * Lazily resolves the output directory at execution time (avoids provider realization during configuration).
     */
    doFirst {
        outputDir = layout.buildDirectory
            .dir("dependencyUpdates")
            .get()
            .asFile
            .absolutePath
    }

    notCompatibleWithConfigurationCache(
        "This task inspects configurations, which breaks configuration cache compatibility."
    )
}

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

val versionCatalogUpdate = "versionCatalogUpdate"
val dependencyUpdatesNoParallel = "dependencyUpdatesNoParallel"

val dependencyUpdatesNoParallelTask: TaskProvider<Task> = tasks.register(
    dependencyUpdatesNoParallel
) {
    /**
     * ## dependencyUpdatesNoParallel
     *
     * Wrapper task enforcing non-parallel execution for dependencyUpdates.
     *
     * ### Rationale
     *
     * The versions plugin may exhibit nondeterministic behavior under parallel project execution.
     *
     * This task:
     *
     * - Depends on `dependencyUpdates`
     * - Fails fast if parallel execution is enabled
     *
     * ### Correct Invocation
     *
     *     ./gradlew dependencyUpdatesNoParallel --no-parallel
     */
    group = "dependencies"
    description = "Runs dependencyUpdates and fails fast if parallel project execution is enabled."
    doFirst {
        check(!gradle.startParameter.isParallelProjectExecutionEnabled) {
            "Run dependencyUpdatesNoParallel with --no-parallel to avoid tooling reliability issues."
        }
    }
    dependsOn(tasks.named("dependencyUpdates"))
}

/**
 * ## dependencyMaintenance
 *
 * Composite lifecycle task for dependency governance.
 *
 * ### Responsibilities
 *
 * 1. Run `versionCatalogUpdate`
 * 2. Run `dependencyUpdates` (non-parallel)
 *
 * ### Guarantees
 *
 * - Version catalog remains current.
 * - Upgrade candidates are reported.
 * - Stable-only policy enforced.
 *
 * ### Intended Usage
 *
 * - Scheduled audits
 * - Manual upgrade review
 * - CI dependency checks
 */
val dependencyMaintenance: TaskProvider<Task> = tasks.register("dependencyMaintenance") {
    group = "dependencies"
    description = "Runs version catalog updates and dependency update reports."
    dependsOn(
        tasks.named(versionCatalogUpdate),
        dependencyUpdatesNoParallelTask
    )
}

/**
 * ## Dependency Locking Helpers
 *
 * Guidance-only tasks that print recommended commands.
 *
 * ### Why Not Automate?
 *
 * `--write-locks` is a CLI flag, not a task input. Encoding it directly in a task would break Gradle's execution model.
 *
 * Therefore, these tasks:
 *
 * - Preserve reproducibility
 * - Avoid side effects
 * - Provide copy-paste guidance
 *
 * They are intentionally marked: `notCompatibleWithConfigurationCache(...)`
 */
tasks.register("locksWriteAll") {
    group = "dependencies"
    description = "Prints the recommended command to refresh all dependency lockfiles."
    notCompatibleWithConfigurationCache("Guidance task; no need to store configuration cache state.")
    doLast {
        logger.lifecycle("./gradlew preflight --write-locks --no-parallel")
    }
}

tasks.register("locksCliHelp") {
    group = "dependencies"
    description = "Prints examples for using the dependency locks CLI."
    notCompatibleWithConfigurationCache("Guidance task; no need to store configuration cache state.")
    doLast {
        logger.lifecycle("./gradlew :tools:runLocksCli --args=\"write-all --json\"")
        logger.lifecycle("./gradlew :tools:runLocksCli --args=\"write-module --module :core --json\"")
        logger.lifecycle("./gradlew :tools:runLocksCli --args=\"write-configuration --module :core --configuration testRuntimeClasspath --json\"")
        logger.lifecycle("./gradlew :tools:runLocksCli --args=\"diff --json\"")
    }
}

tasks.register("locksDiff") {
    group = "dependencies"
    description = "Prints a git diff command for lockfiles."
    notCompatibleWithConfigurationCache("Guidance task; no need to store configuration cache state.")
    doLast {
        logger.lifecycle("git diff -- **/gradle.lockfile settings-gradle.lockfile")
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
val verifyAll: TaskProvider<Task> = tasks.register("verifyAll") {
    group = "verification"
    description = "Runs tests, static analysis, and API compatibility checks in one go."
}

val verifyTaskNames = setOf("test", "detekt", "apiCheck")

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
        syncVersionProperties,
        syncBuildLogicVersionProperties
    )
}
