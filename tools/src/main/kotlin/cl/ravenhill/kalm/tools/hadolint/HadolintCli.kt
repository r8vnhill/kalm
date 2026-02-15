package cl.ravenhill.kalm.tools.hadolint

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * Command-line entry point for running Hadolint against one or more Dockerfiles.
 *
 * This object provides a small orchestration layer around Hadolint execution:
 *
 * - Parses CLI arguments into a validated [CliOptions] instance.
 * - Resolves Dockerfile paths into a [ResolveResult] (existing vs. missing).
 * - Selects an execution strategy ([HadolintRunner]) based on availability:
 *   - [BinaryHadolintRunner] if `hadolint` is available locally.
 *   - [DockerHadolintRunner] as a fallback when Docker is available.
 * - Runs Hadolint for each resolved Dockerfile and aggregates failures.
 *
 * ## Testability
 *
 * The orchestration is designed to be deterministic and straightforward to test:
 *
 * - Tool availability checks can be injected (`hadolintAvailable`, `dockerAvailable`).
 * - File existence checks can be injected (`exists`).
 * - Output streams can be injected (`out`, `err`).
 * - Runner selection can be injected (`runnerSelector`) to use test doubles.
 *
 * ### This avoids requiring:
 *
 * - A real Docker daemon
 * - A real `hadolint` binary
 * - A real filesystem layout
 *
 * ## Exit Code Contract
 *
 * - Returns `0` when all lint targets succeed.
 * - Returns `1` when any target fails, or when validation fails (e.g., strict missing files).
 */
object HadolintCli {
    private class HelpRequested : RuntimeException()

    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    /**
     * Prints the CLI usage message.
     *
     * The output stream is injectable to keep this method test-friendly.
     *
     * @param out Target stream for usage text.
     */
    fun printUsage(out: PrintStream = System.out) {
        out.println(
            """
            |Usage:
            |  cl.ravenhill.kalm.tools.hadolint.HadolintCli [options]
            |
            |Options:
            |  --dockerfile, -f <path>          Dockerfile path to lint (repeatable)
            |  --failure-threshold, -t <level>  error|warning|info|style|ignore (default: warning)
            |  --strict-files                   Fail if any Dockerfile is missing
            |  --help                           Show this help
            """.trimMargin()
        )
    }

    /**
     * Parses CLI arguments into an immutable, validated [CliOptions].
     *
     * Parsing rules:
     *
     * - `--dockerfile` / `-f` may be repeated to lint multiple files.
     * - If no dockerfiles are specified, defaults to `["Dockerfile"]`.
     * - `--failure-threshold` / `-t` is validated and normalized via [ValidThreshold.fromString].
     * - `--strict-files` enables strict missing-file handling (see [CliOptions.strictFiles]).
     * - `--help` / `-h` prints usage and terminates the process with exit code `0`.
     *
     * @param args Raw command-line arguments.
     * @return Parsed and validated [CliOptions].
     * @throws IllegalArgumentException If an unknown flag is found or a required value is missing.
     */
    fun parseArgs(args: Array<String>): CliOptions {
        val dockerfiles = mutableListOf<String>()
        var failureThreshold = ValidThreshold.default
        var strictFiles = false

        var i = 0
        while (i < args.size) {
            when (val arg = args[i]) {
                "--dockerfile", "-f" -> i = parseDockerfileArgument(i, args, dockerfiles)

                "--failure-threshold", "-t" -> {
                    val pair = parseFailureThreshold(i, args)
                    failureThreshold = pair.first
                    i = pair.second
                }

                "--strict-files" -> {
                    strictFiles = true
                    i += 1
                }

                "--help", "-h" -> {
                    throw HelpRequested()
                }

                else -> throw IllegalArgumentException("Unknown argument: $arg")
            }
        }

        return CliOptions(
            dockerfiles = if (dockerfiles.isEmpty()) listOf("Dockerfile") else dockerfiles,
            failureThreshold = failureThreshold,
            strictFiles = strictFiles
        )
    }

    /**
     * Parses a `--failure-threshold` / `-t` argument pair.
     *
     * This method enforces presence of the value argument and delegates validation and normalization to
     * [ValidThreshold.fromString].
     *
     * @param i Index of the flag argument in [args].
     * @param args Full CLI argument array.
     * @return A pair `(threshold, nextIndex)` where `nextIndex` is the updated parsing position.
     * @throws IllegalArgumentException If the threshold value is missing or invalid.
     */
    private fun parseFailureThreshold(
        i: Int,
        args: Array<String>
    ): Pair<ValidThreshold, Int> {
        var currentIndex = i
        if (currentIndex + 1 >= args.size) {
            throw IllegalArgumentException("Missing value for --failure-threshold")
        }
        val threshold = ValidThreshold.fromString(args[currentIndex + 1])
        currentIndex += 2
        return threshold to currentIndex
    }

    /**
     * Parses a `--dockerfile` / `-f` argument pair and appends its value to [dockerfiles].
     *
     * @param i Index of the flag argument in [args].
     * @param args Full CLI argument array.
     * @param dockerfiles Accumulator for dockerfile path strings.
     * @return Updated parsing position (index after the consumed pair).
     * @throws IllegalArgumentException If the dockerfile value is missing.
     */
    private fun parseDockerfileArgument(
        i: Int,
        args: Array<String>,
        dockerfiles: MutableList<String>
    ): Int {
        var currentIndex = i
        if (currentIndex + 1 >= args.size) {
            throw IllegalArgumentException("Missing value for --dockerfile")
        }
        dockerfiles += args[currentIndex + 1]
        currentIndex += 2
        return currentIndex
    }

    /**
     * Resolves dockerfile path strings from [options] into a [ResolveResult].
     *
     * ## Resolution behavior:
     *
     * - Converts each input string to a normalized absolute [Path].
     * - Splits results into existing and missing lists.
     * - Preserves input order.
     *
     * The filesystem check is injectable to support deterministic unit tests.
     *
     * @param options Parsed CLI options.
     * @param exists Predicate that determines whether a path exists on disk.
     * @return [ResolveResult] containing existing and missing paths.
     */
    fun resolveDockerfiles(
        options: CliOptions,
        exists: (Path) -> Boolean = Files::exists
    ): ResolveResult {
        val dockerfiles = options.dockerfiles.map { Paths.get(it).toAbsolutePath().normalize() }
        val existing = dockerfiles.filter { exists(it) }
        val missing = dockerfiles.filterNot { exists(it) }
        return ResolveResult(existing = existing, missing = missing)
    }

    /**
     * Checks whether a command appears to be available on the current system PATH.
     *
     * This method attempts to run `<command> --version` and returns:
     *
     * - `true` if the process starts and exits with code `0`
     * - `false` if execution fails or returns a non-zero exit code
     *
     * @param command Program name (expected to be resolvable on PATH).
     * @return `true` if the command appears runnable, otherwise `false`.
     */
    fun hasCommand(command: String): Boolean = try {
        val process = ProcessBuilder(command, "--version")
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
        process.waitFor() == 0
    } catch (_: Exception) {
        false
    }

    /**
     * Checks whether a command line can be executed successfully.
     *
     * This is primarily used for probing Docker availability, e.g. `docker version`.
     *
     * @param command Command and arguments.
     * @return `true` if the process exits with code `0`, otherwise `false`.
     */
    fun canRun(vararg command: String): Boolean = try {
        val process = ProcessBuilder(*command)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
        process.waitFor() == 0
    } catch (_: Exception) {
        false
    }

    /**
     * Selects a [HadolintRunner] based on tool availability.
     *
     * ## Runner selection policy:
     *
     * - Prefer local `hadolint` when available.
     * - Otherwise fall back to Docker if available.
     * - If neither is available, fail with a clear diagnostic message.
     *
     * @param hadolintAvailable Whether the local `hadolint` binary is available.
     * @param dockerAvailable Whether Docker is available.
     * @return A concrete [HadolintRunner] implementation.
     * @throws IllegalStateException If neither execution strategy is available.
     */
    fun selectRunner(hadolintAvailable: Boolean, dockerAvailable: Boolean): HadolintRunner =
        if (hadolintAvailable) {
            BinaryHadolintRunner()
        } else if (dockerAvailable) {
            DockerHadolintRunner()
        } else {
            error("Could not find `hadolint` and Docker is not available. Install one of them and try again.")
        }

    /**
     * Runs the Hadolint CLI workflow and returns a process-like exit code.
     *
     * This function is the main unit-testable entry point: all external concerns can be injected.
     *
     * ## Workflow:
     *
     * 1. Parse [args] into [CliOptions]
     * 2. Select a [HadolintRunner]
     * 3. Resolve dockerfile paths and print missing-file warnings
     * 4. Validate strict-file policy and presence of at least one target
     * 5. Run Hadolint for each resolved file and aggregate failures
     *
     * @param args Raw CLI arguments.
     * @param runnerSelector Strategy to select a [HadolintRunner] given availability flags.
     * @param hadolintAvailable Probe for local `hadolint` availability.
     * @param dockerAvailable Probe for Docker availability.
     * @param exists Filesystem existence predicate for testability.
     * @param out Output stream for normal diagnostics.
     * @param err Output stream for error diagnostics.
     * @return `0` if lint succeeds for all targets, otherwise `1`.
     */
    fun runCliJson(
        args: Array<String>,
        runnerSelector: (Boolean, Boolean) -> HadolintRunner = ::selectRunner,
        hadolintAvailable: () -> Boolean = { hasCommand("hadolint") },
        dockerAvailable: () -> Boolean = { canRun("docker", "version") },
        exists: (Path) -> Boolean = Files::exists,
        out: PrintStream = System.out,
        err: PrintStream = System.err,
        nowEpochMs: () -> Long = { System.currentTimeMillis() }
    ): HadolintCliResult {
        val started = nowEpochMs()
        val defaultOptions = CliOptions(
            dockerfiles = listOf("Dockerfile"),
            failureThreshold = ValidThreshold.default,
            strictFiles = false
        )
        val options = try {
            parseArgs(args)
        } catch (_: HelpRequested) {
            printUsage(err)
            return emitResult(
                out = out,
                result = HadolintCliResult(
                    exitCode = 0,
                    threshold = defaultOptions.failureThreshold.value,
                    strictFiles = defaultOptions.strictFiles,
                    targets = emptyList(),
                    missing = emptyList(),
                    failed = emptyList(),
                    runner = "unknown",
                    startedAtEpochMs = started,
                    finishedAtEpochMs = nowEpochMs()
                )
            )
        } catch (e: Exception) {
            err.println(e.message ?: e.toString())
            return emitResult(
                out = out,
                result = HadolintCliResult(
                    exitCode = 1,
                    threshold = defaultOptions.failureThreshold.value,
                    strictFiles = defaultOptions.strictFiles,
                    targets = emptyList(),
                    missing = emptyList(),
                    failed = emptyList(),
                    runner = "unknown",
                    startedAtEpochMs = started,
                    finishedAtEpochMs = nowEpochMs()
                )
            )
        }
        val resolved = resolveDockerfilePaths(options, exists, err)

        var runnerId = "unknown"
        val (exitCode, failed) = try {
            val runner = runnerSelector(hadolintAvailable(), dockerAvailable())
            runnerId = when (runner) {
                is BinaryHadolintRunner -> "binary"
                is DockerHadolintRunner -> "docker"
                else -> runner::class.simpleName ?: "unknown"
            }

            validateDockerfiles(resolved, options)
            err.println("Linting Dockerfiles with threshold '${options.failureThreshold.value}'...")
            err.println("Targets: ${resolved.existing.joinToString(", ")}")

            val failedPaths = runHadolintOnResolvedFiles(resolved, err, runner, options, err)
            if (failedPaths.isNotEmpty()) {
                err.println("Hadolint reported issues in: ${failedPaths.joinToString(", ")}")
                1 to failedPaths
            } else {
                0 to emptyList()
            }
        } catch (e: Exception) {
            err.println(e.message ?: e.toString())
            1 to emptyList<Path>()
        }

        val result = HadolintCliResult(
            exitCode = exitCode,
            threshold = options.failureThreshold.value,
            strictFiles = options.strictFiles,
            targets = resolved.existing.map(Path::toString),
            missing = resolved.missing.map(Path::toString),
            failed = failed.map(Path::toString),
            runner = runnerId,
            startedAtEpochMs = started,
            finishedAtEpochMs = nowEpochMs()
        )
        return emitResult(out, result)
    }

    private fun emitResult(
        out: PrintStream,
        result: HadolintCliResult
    ): HadolintCliResult {
        out.println(json.encodeToString(result))
        return result
    }

    /**
     * Runs Hadolint for each resolved dockerfile and returns the list of failing paths.
     *
     * ## For each file:
     *
     * - Prints a progress message to [out]
     * - Executes [runner]
     * - Records the file in the failure list when the exit code is non-zero
     * - Prints a diagnostic command description to [err] on failure
     *
     * @param resolved Resolution results containing existing lint targets.
     * @param out Output stream for progress logs.
     * @param runner Runner used to execute Hadolint.
     * @param options CLI options (the threshold is propagated to the runner).
     * @param err Output stream for failure diagnostics.
     * @return Mutable list of dockerfiles that produced non-zero exit codes.
     */
    private fun runHadolintOnResolvedFiles(
        resolved: ResolveResult,
        log: PrintStream,
        runner: HadolintRunner,
        options: CliOptions,
        err: PrintStream
    ): List<Path> {
        val failed = mutableListOf<Path>()
        // Runs linter; tracks files with non‑zero exit codes
        for (file in resolved.existing) {
            log.println("Running Hadolint on: $file")
            val exitCode = runner.run(file, options.failureThreshold)
            if (exitCode != 0) {
                err.println(
                    "Hadolint failed. Command: ${
                        runner.commandDescription(
                            file,
                            options.failureThreshold
                        )
                    }"
                )
                failed.add(file)
            }
        }
        return failed
    }

    /**
     * Validates resolved dockerfile targets against [options].
     *
     * ## Validation rules:
     *
     * - If missing files exist and [CliOptions.strictFiles] is enabled, fail immediately.
     * - If no existing dockerfiles are found, fail immediately.
     *
     * @param resolved Resolved file sets.
     * @param options Parsed CLI options.
     * @throws IllegalStateException If validation fails.
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

    /**
     * Resolves dockerfile paths and prints a summary to [out].
     *
     * @param options Parsed CLI options.
     * @param exists Filesystem existence predicate.
     * @param out Output stream for warnings and summary.
     * @return [ResolveResult] containing existing and missing paths.
     */
    private fun resolveDockerfilePaths(
        options: CliOptions,
        exists: (Path) -> Boolean,
        err: PrintStream
    ): ResolveResult {
        val resolved = resolveDockerfiles(options, exists)
        resolved.missing.forEach { err.println("WARNING: Dockerfile not found and will be skipped: $it") }
        err.println("Found ${resolved.existing.size} Dockerfile(s); ${resolved.missing.size} missing.")
        return resolved
    }

    /**
     * CLI entry point.
     *
     * Delegates to [runCli] and terminates the JVM with the returned exit code.
     *
     * @param args Raw CLI arguments.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val result = runCliJson(args)
        exitProcess(result.exitCode)
    }
}
