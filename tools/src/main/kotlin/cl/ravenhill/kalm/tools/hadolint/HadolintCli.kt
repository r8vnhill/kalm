package cl.ravenhill.kalm.tools.hadolint

import kotlinx.serialization.json.Json
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.jvm.Throws
import kotlin.system.exitProcess

/**
 * JSON-oriented command-line interface for running Hadolint against one or more Dockerfiles.
 *
 * ## High-level flow
 *
 * 1. Parse arguments into [CliOptions] ([parseArgs]).
 * 2. Resolve inputs into existing vs. missing paths ([resolveDockerfilePaths] via [resolveDockerfiles]).
 * 3. Select the execution strategy ([HadolintRunner]) through [selectRunner]:
 *    - [BinaryHadolintRunner] when `{sh} hadolint` is available.
 *    - [DockerHadolintRunner] when Docker is available.
 * 4. Execute linting ([executeLinting]) and aggregate failures into [LintExecution].
 * 5. Emit a JSON [HadolintCliResult] and exit with its exit code ([main]).
 *
 * ## Logging contract
 *
 * The CLI uses two output streams:
 *
 * - [System.out]: **JSON only** (single object, suitable for parsing).
 * - [System.err]: human-readable diagnostics (warnings, progress, failure reasons).
 *
 * This separation is critical when the caller expects clean JSON on stdout.
 *
 * ## Exit code policy
 *
 * The process exit code is derived from [HadolintCliResult.exitCode]:
 *
 * - `0`: successful execution and no lint failures.
 * - `1`: lint failures or expected CLI/validation failures.
 */
object HadolintCli {

    /**
     * Internal control-flow throwable used to model `--help` / `-h`.
     *
     * This is intentionally *not* exposed as an error to callers: help is a normal termination path. The throwable is
     * caught in [parseAndExecute] and translated into a successful JSON result.
     */
    private class HelpRequested : Throwable()

    /**
     * JSON encoder for serializing [HadolintCliResult].
     */
    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    /**
     * Prints a human-readable usage message.
     *
     * This prints to an injected stream (defaults to [System.out]) so tests can capture output. In JSON mode, callers
     * typically invoke `--help` and read the usage from [System.err], while [out] remains valid JSON.
     *
     * @param out Destination stream for usage output.
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
     * ## Rules:
     *
     * - `--dockerfile` / `-f` may be repeated to lint multiple files.
     * - If no Dockerfiles are specified, defaults to `["Dockerfile"]`.
     * - `--failure-threshold` / `-t` is validated and normalized via [ValidThreshold.fromString].
     * - `--strict-files` enforces missing-file failures (see [CliOptions.strictFiles]).
     * - `--help` / `-h` triggers [HelpRequested] (handled upstream as a successful termination path).
     *
     * @param args Raw command-line arguments.
     * @return Parsed [CliOptions].
     * @throws IllegalArgumentException If a flag is unknown, a value is missing, or a value is invalid.
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
     * Parses a `--failure-threshold` / `-t` flag and its value.
     *
     * @param i Index of the flag argument in [args].
     * @param args Full CLI argument array.
     * @return A pair `(threshold, nextIndex)` where `nextIndex` points after the consumed pair.
     * @throws IllegalArgumentException If the value is missing or not a valid [ValidThreshold].
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
     * Parses a `--dockerfile` / `-f` flag and its value.
     *
     * @param i Index of the flag argument in [args].
     * @param args Full CLI argument array.
     * @param dockerfiles Accumulator list for dockerfile path strings.
     * @return Updated parsing index after consuming the pair.
     * @throws IllegalArgumentException If the value is missing.
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
     * ## Resolution policy:
     *
     * - Each string is converted to a normalized absolute [Path].
     * - Results are partitioned into:
     *   - [ResolveResult.existing] (paths where [exists] returns true)
     *   - [ResolveResult.missing] (paths where [exists] returns false)
     * - Input order is preserved within each partition.
     *
     * @param options Parsed CLI options.
     * @param exists Predicate used to test filesystem existence (injectable for tests).
     * @return [ResolveResult] containing normalized existing and missing paths.
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
     * Checks whether a command appears runnable (available on PATH).
     *
     * Implementation detail: attempts to run `{sh} <command> --version` and returns true iff it exits with code `0`.
     *
     * @param command Program name expected to be resolvable on PATH.
     * @return True if the command appears runnable.
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
     * This is primarily used to probe Docker availability, e.g. `{sh} docker version`.
     *
     * @param command Command and arguments.
     * @return True if the process exits with code `0`.
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
     * Selects a [HadolintRunner] based on environment availability.
     *
     * ## Policy:
     *
     * - Prefer local `hadolint` when available.
     * - Otherwise, use Docker-based execution if Docker is available.
     * - If neither is available, fail with a clear message.
     *
     * @throws IllegalStateException If no supported execution strategy is available.
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
     * Internal representation of parse outcomes.
     *
     * This avoids early returns in [runCliJson] by modeling:
     *
     * - [Ok] -> parsing succeeded; continue execution.
     * - [Done] -> parsing triggered an immediate result (help or parse error).
     */
    private sealed interface Parsed {

        /**
         * Parsing succeeded.
         *
         * @property options Parsed [CliOptions].
         */
        data class Ok(val options: CliOptions) : Parsed

        /**
         * Parsing resulted in a terminal response.
         *
         * Examples:
         * - Help requested
         * - Invalid flag/value detected
         *
         * @property result Final [HadolintCliResult] to emit.
         */
        data class Done(val result: HadolintCliResult) : Parsed
    }

    /**
     * Runs the CLI workflow and returns a structured JSON-friendly result.
     *
     * ## Contract:
     *
     * - Always returns a [HadolintCliResult].
     * - Always prints exactly one JSON object to [out] via [emitResult].
     * - Diagnostic/progress output is written to [err].
     *
     * @param args Raw CLI arguments.
     * @param runnerSelector Strategy for selecting the runner (injectable for tests).
     * @param hadolintAvailable Probe for local `hadolint` availability.
     * @param dockerAvailable Probe for Docker availability.
     * @param exists Filesystem existence predicate (injectable for tests).
     * @param out JSON output stream.
     * @param err Diagnostic output stream.
     * @param nowEpochMs Time source (injectable for deterministic tests).
     * @return The emitted [HadolintCliResult].
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

        // Defaults used when parsing fails or help is requested. This keeps the schema stable even for early
        // termination paths.
        val defaultOptions = CliOptions(
            dockerfiles = listOf("Dockerfile"),
            failureThreshold = ValidThreshold.default,
            strictFiles = false
        )

        val result = when (val parsed: Parsed = parseAndExecute(args, err, defaultOptions, started, nowEpochMs)) {
            is Parsed.Done -> parsed.result
            is Parsed.Ok -> lintDockerfiles(
                parsed = parsed,
                exists = exists,
                err = err,
                runnerSelector = runnerSelector,
                hadolintAvailable = hadolintAvailable,
                dockerAvailable = dockerAvailable,
                nowEpochMs = nowEpochMs,
                started = started
            )
        }

        return emitResult(out, result)
    }

    /**
     * Runs resolution and linting for parsed options and builds the final [HadolintCliResult].
     *
     * This function is intentionally side-effect-light:
     *
     * - All user-facing logs are sent through [PrintStreamLintLogger] to [err].
     * - It returns a fully populated result object, including timestamps and runner identification.
     *
     * @param parsed Successful parse result containing [CliOptions].
     * @param exists Filesystem existence predicate.
     * @param err Diagnostic stream (human output).
     * @param runnerSelector Runner selection strategy.
     * @param hadolintAvailable Availability probe.
     * @param dockerAvailable Availability probe.
     * @param nowEpochMs Time source.
     * @param started Start timestamp captured by [runCliJson].
     * @return Fully populated [HadolintCliResult].
     */
    private fun lintDockerfiles(
        parsed: Parsed.Ok,
        exists: (Path) -> Boolean,
        err: PrintStream,
        runnerSelector: (Boolean, Boolean) -> HadolintRunner,
        hadolintAvailable: () -> Boolean,
        dockerAvailable: () -> Boolean,
        nowEpochMs: () -> Long,
        started: Long
    ): HadolintCliResult {
        val options = parsed.options
        val logger = PrintStreamLintLogger(err)

        val resolved = resolveDockerfilePaths(options, exists, logger)

        val execution = executeLinting(
            runnerSelector = runnerSelector,
            hadolintAvailable = hadolintAvailable,
            dockerAvailable = dockerAvailable,
            resolved = resolved,
            options = options,
            logger = logger
        )

        val finished = nowEpochMs()

        return HadolintCliResult(
            exitCode = execution.exitCode.value,
            threshold = options.failureThreshold.value,
            strictFiles = options.strictFiles,
            targets = resolved.existing.map(Path::toString),
            missing = resolved.missing.map(Path::toString),
            failed = execution.failed.map(Path::toString),
            runner = execution.runnerId.name.lowercase(),
            startedAtEpochMs = started,
            finishedAtEpochMs = finished
        )
    }

    /**
     * Parses arguments and materializes early-termination results.
     *
     * This function centralizes the “parse stage” policy:
     *
     * - Help requested -> print usage to [err], return exit code `0`.
     * - Parse/validation error -> print a message to [err], return exit code `1`.
     * - Success -> return [Parsed.Ok].
     *
     * Returning [Parsed.Done] avoids early returns in [runCliJson] and keeps the control flow easy to follow and test.
     *
     * @param args Raw CLI arguments.
     * @param err Diagnostic stream for help/errors.
     * @param defaultOptions Defaults used when parse does not yield [CliOptions].
     * @param started Start timestamp captured by the caller.
     * @param nowEpochMs Time source for the termination timestamp.
     * @return [Parsed.Ok] on success, otherwise [Parsed.Done].
     */
    private fun parseAndExecute(
        args: Array<String>,
        err: PrintStream,
        defaultOptions: CliOptions,
        started: Long,
        nowEpochMs: () -> Long
    ): Parsed = try {
        Parsed.Ok(parseArgs(args))
    } catch (_: HelpRequested) {
        printUsage(err)
        Parsed.Done(
            HadolintCliResult(
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
    } catch (e: IllegalArgumentException) {
        err.println(e.message ?: e.toString())
        Parsed.Done(
            HadolintCliResult(
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

    /**
     * Serializes [result] to JSON and writes it to [out].
     *
     * This is the *only* place where JSON is emitted. Keeping emission centralized:
     *
     * - ensures stdout stays parseable,
     * - avoids partial JSON,
     * - and makes it easy to evolve the schema.
     *
     * @return The same [HadolintCliResult] for convenient call chaining/testing.
     */
    private fun emitResult(
        out: PrintStream,
        result: HadolintCliResult
    ): HadolintCliResult {
        out.println(json.encodeToString(result))
        return result
    }

    /**
     * Process entry point.
     *
     * - Executes [runCliJson], which prints JSON to stdout.
     * - Exits with [HadolintCliResult.exitCode] so the CLI remains shell-friendly.
     *
     * @param args Raw CLI arguments.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val result = runCliJson(args)
        exitProcess(result.exitCode)
    }
}
