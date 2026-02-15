package cl.ravenhill.kalm.tools.hadolint

import java.io.PrintStream
import java.nio.file.Path

/**
 * Marker exception used to represent *expected*, user-facing CLI failures.
 *
 * This exception type separates:
 *
 * - **User / domain errors**
 *   - Missing Dockerfiles with `--strict-files`
 *   - No lint targets found
 * - **Environment errors**
 *   - Neither `hadolint` nor Docker available
 *
 * from truly unexpected programming failures.
 *
 * By distinguishing expected failures from programmer errors, the CLI can:
 *
 * - Produce deterministic [LintExecution] results
 * - Emit structured JSON
 * - Avoid crashing the process unexpectedly
 *
 * These exceptions are caught and translated into controlled exit codes.
 *
 * @param message Human-readable diagnostic message.
 */
internal class ExpectedCliFailure(message: String) : RuntimeException(message)

/**
 * Identifier for the concrete lint execution strategy used.
 *
 * This replaces string literals such as `"binary"` or `"docker"` with a type-safe enumeration to prevent drift
 * and magic values.
 */
internal enum class RunnerId {
    /** Local `hadolint` binary was used. */
    BINARY,

    /** Docker-based `hadolint/hadolint` image was used. */
    DOCKER,

    /** Runner could not be determined (error path). */
    UNKNOWN
}

/**
 * Strongly typed representation of a process exit code.
 *
 * This value class prevents accidental mixing of arbitrary integers with semantic exit codes.
 *
 * ## Convention:
 *
 * - `0` -> success
 * -  ~  -> failure
 */
@JvmInline
internal value class ExitCode(val value: Int)

/**
 * Minimal logging abstraction for lint execution.
 *
 * This decouples execution logic from the output medium.
 *
 * ## Enables:
 *
 * - CLI printing
 * - Silent JSON-only mode
 * - Structured logging in the future
 * - Deterministic testing
 */
internal interface LintLogger {

    /**
     * Emits informational output.
     */
    fun info(message: String)

    /**
     * Emits warning or failure-related output.
     */
    fun warn(message: String)
}

/**
 * Simple [LintLogger] backed by a [PrintStream].
 *
 * Suitable for CLI execution environments.
 *
 * @property stream Destination output stream.
 */
internal class PrintStreamLintLogger(
    private val stream: PrintStream
) : LintLogger {

    override fun info(message: String) =
        stream.println(message)

    override fun warn(message: String) =
        stream.println(message)
}

/**
 * Domain-level result of a lint execution phase.
 *
 * This represents the *semantic outcome* of running Hadolint across one or more Dockerfiles, independent of the
 * presentation layer.
 *
 * @property exitCode Structured exit code (`0` success, `1` failure).
 * @property failed Dockerfiles that produced non-zero exit codes.
 * @property runnerId Identifier of the runner strategy used.
 */
internal data class LintExecution(
    val exitCode: ExitCode,
    val failed: List<Path>,
    val runnerId: RunnerId
)

/**
 * Resolves Dockerfile paths and emits resolution diagnostics.
 *
 * ## Responsibilities:
 *
 * - Normalize Dockerfile paths.
 * - Partition them into existing and missing sets.
 * - Emit resolution warnings.
 *
 * Resolution remains pure in [HadolintCli.resolveDockerfiles]. This function adds *logging concerns only*.
 *
 * @param options CLI options containing raw dockerfile paths.
 * @param exists Filesystem existence predicate (injectable for testing).
 * @param logger Logging abstraction.
 * @return [ResolveResult] containing normalized existing and missing paths.
 */
internal fun resolveDockerfilePaths(
    options: CliOptions,
    exists: (Path) -> Boolean,
    logger: LintLogger
): ResolveResult {
    val resolved = HadolintCli.resolveDockerfiles(options, exists)

    resolved.missing.forEach {
        logger.warn("WARNING: Dockerfile not found and will be skipped: $it")
    }

    logger.info(
        "Found ${resolved.existing.size} Dockerfile(s); ${resolved.missing.size} missing."
    )

    return resolved
}

/**
 * Executes linting and converts failures into safe [LintExecution] results.
 *
 * ## This function guarantees:
 *
 * - No exception escapes.
 * - All failures produce deterministic exit code `1`.
 *
 * ## Failure handling policy:
 *
 * - [ExpectedCliFailure] -> user-facing error, exit code `1`
 * - Any other [RuntimeException] -> unexpected failure, exit code `1`
 *
 * @return Structured [LintExecution].
 */
internal fun executeLinting(
    runnerSelector: (Boolean, Boolean) -> HadolintRunner,
    hadolintAvailable: () -> Boolean,
    dockerAvailable: () -> Boolean,
    resolved: ResolveResult,
    options: CliOptions,
    logger: LintLogger
): LintExecution = try {
    executeLint(
        runnerSelector = runnerSelector,
        hadolintAvailable = hadolintAvailable,
        dockerAvailable = dockerAvailable,
        resolved = resolved,
        options = options,
        logger = logger
    )
} catch (e: ExpectedCliFailure) {
    logger.warn(e.message ?: e.toString())
    LintExecution(
        exitCode = ExitCode(1),
        failed = emptyList(),
        runnerId = RunnerId.UNKNOWN
    )
} catch (e: RuntimeException) {
    logger.warn("Unexpected error: ${e.message ?: e.toString()}")
    LintExecution(
        exitCode = ExitCode(1),
        failed = emptyList(),
        runnerId = RunnerId.UNKNOWN
    )
}

/**
 * Core lint execution algorithm.
 *
 * ## Steps:
 *
 * 1. Select runner
 * 2. Validate resolution results
 * 3. Emit progress logs
 * 4. Execute Hadolint per Dockerfile
 * 5. Aggregate failures
 *
 * This function may throw [ExpectedCliFailure] for controlled, user-facing failures.
 *
 * @throws ExpectedCliFailure If runner selection or validation fails.
 */
private fun executeLint(
    runnerSelector: (Boolean, Boolean) -> HadolintRunner,
    hadolintAvailable: () -> Boolean,
    dockerAvailable: () -> Boolean,
    resolved: ResolveResult,
    options: CliOptions,
    logger: LintLogger
): LintExecution {

    val runner = try {
        runnerSelector(hadolintAvailable(), dockerAvailable())
    } catch (e: IllegalStateException) {
        throw ExpectedCliFailure(e.message ?: e.toString())
    }

    val runnerId = when (runner) {
        is BinaryHadolintRunner -> RunnerId.BINARY
        is DockerHadolintRunner -> RunnerId.DOCKER
        else -> RunnerId.UNKNOWN
    }

    try {
        validateDockerfiles(resolved, options)
    } catch (e: IllegalStateException) {
        throw ExpectedCliFailure(e.message ?: e.toString())
    }

    logger.info("Linting Dockerfiles with threshold '${options.failureThreshold.value}'...")
    logger.info("Targets: ${resolved.existing.joinToString(", ")}")

    val failedPaths = runHadolintOnResolvedFiles(
        resolved = resolved,
        logger = logger,
        runner = runner,
        options = options
    )

    if (failedPaths.isNotEmpty()) {
        logger.warn("Hadolint reported issues in: ${failedPaths.joinToString(", ")}")
    }

    return LintExecution(
        exitCode = ExitCode(if (failedPaths.isNotEmpty()) 1 else 0),
        failed = failedPaths,
        runnerId = runnerId
    )
}

/**
 * Executes Hadolint for each resolved Dockerfile.
 *
 * This function:
 *
 * - Emits progress messages.
 * - Executes the selected runner.
 * - Collects non-zero exit codes.
 * - Emits command diagnostics on failure.
 *
 * It does not throw; it only aggregates execution results.
 *
 * @return List of Dockerfiles that failed linting.
 */
private fun runHadolintOnResolvedFiles(
    resolved: ResolveResult,
    logger: LintLogger,
    runner: HadolintRunner,
    options: CliOptions
): List<Path> {

    val failed = mutableListOf<Path>()

    for (file in resolved.existing) {
        logger.info("Running Hadolint on: $file")

        val exitCode = runner.run(file, options.failureThreshold)

        if (exitCode != 0) {
            logger.warn(
                "Hadolint failed. Command: ${
                    runner.commandDescription(file, options.failureThreshold)
                }"
            )
            failed.add(file)
        }
    }

    return failed
}

/**
 * Validates Dockerfile resolution results against CLI policy.
 *
 * Rules:
 *
 * - If missing files exist AND `--strict-files` is enabled -> fail.
 * - If no existing Dockerfiles are found -> fail.
 *
 * Throws [IllegalStateException] which is wrapped into
 * [ExpectedCliFailure] by the caller.
 */
private fun validateDockerfiles(
    resolved: ResolveResult,
    options: CliOptions
) {
    if (resolved.missing.isNotEmpty() && options.strictFiles) {
        error("Missing Dockerfiles while --strict-files is enabled.")
    }

    if (resolved.existing.isEmpty()) {
        error("No valid Dockerfiles were found to lint.")
    }
}
