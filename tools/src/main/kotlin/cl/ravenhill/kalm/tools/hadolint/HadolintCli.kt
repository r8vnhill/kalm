package cl.ravenhill.kalm.tools.hadolint

import cl.ravenhill.kalm.tools.cli.detectMissingOptionValue
import cl.ravenhill.kalm.tools.cli.extractNoSuchOptionName
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.NoSuchOption
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.validate
import kotlinx.serialization.json.Json
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * JSON-oriented command-line interface for running Hadolint against one or more Dockerfiles.
 *
 * This CLI is intended for automation and build tooling where stdout is treated as a machine channel and stderr as a
 * human channel.
 *
 * ## Contract
 *
 * - **Stdout**: emits **exactly one** JSON object, encoded as [HadolintCliResult].
 * - **Stderr**: prints usage, parse errors, and progress diagnostics.
 * - **Exit code**: equals the emitted [HadolintCliResult.exitCode].
 *
 * This split allows callers to safely parse stdout even when stderr contains help text or validation errors.
 *
 * ## Workflow
 *
 * 1. Parse arguments via Clikt ([HadolintCommand]) using [parseHadolintOptions].
 * 2. Resolve Dockerfile inputs into existing vs. missing paths via [resolveDockerfiles] and `resolveDockerfilePaths`.
 * 3. Select a [HadolintRunner] via [selectRunner] based on [hasCommand] and [canRun].
 * 4. Execute linting (see `executeLinting`) and aggregate failures into [LintExecution].
 * 5. Emit JSON via [emitResult] and terminate via [main].
 *
 * ## Help and errors
 *
 * Parsing and usage rendering are delegated to Clikt:
 *
 * - `{sh} --help` / `{sh} -h` triggers [PrintHelpMessage] and results in exit code `0`.
 * - Invalid flags/values trigger [UsageError] and result in exit code `1`.
 *
 * The CLI still produces a JSON payload for these early-termination paths to keep automation stable.
 */
object HadolintCli {

    /**
     * Outcome of parsing the CLI arguments.
     *
     * Parsing either yields a validated [CliOptions] ([Ok]) or an early [HadolintCliResult] that should be emitted
     * without running linting ([EarlyResult]).
     */
    private sealed interface ParseOutcome {

        /**
         * Parsing succeeded and produced validated options.
         *
         * @property options Parsed [CliOptions] materialized by [HadolintCommand].
         */
        data class Ok(val options: CliOptions) : ParseOutcome

        /**
         * Parsing resulted in a terminal response (help or validation failure).
         *
         * @property result JSON result to emit without executing linting.
         */
        data class EarlyResult(val result: HadolintCliResult) : ParseOutcome
    }

    /**
     * Clikt-backed command that defines the CLI interface and materializes [CliOptions].
     *
     * This command is responsible for:
     *
     * - describing the CLI surface (options, defaults, help),
     * - validating option values (via `validate { ... }`),
     * - and converting raw strings into a final [CliOptions] instance in [run].
     *
     * This command intentionally does not run Hadolint. Execution is handled by [runCliJson] so that:
     *
     * - stdout can remain JSON-only,
     * - stderr can be used for progress/diagnostics,
     * - and tests can inject I/O and probes without going through Clikt internals.
     */
    private class HadolintCommand : CliktCommand(name = "hadolint-cli") {

        /**
         * Dockerfile paths to lint.
         *
         * This option may be repeated:
         *
         * - `{sh} --dockerfile Dockerfile`
         * - `{sh} -f Dockerfile.api -f Dockerfile.cli`
         *
         * If not specified, [run] defaults to `Dockerfile`.
         */
        private val dockerfiles by option("--dockerfile", "-f")
            .multiple()
            .help(
                "Path to a Dockerfile to lint. Can be specified multiple times. " +
                        "Defaults to 'Dockerfile' if not provided."
            )
            .validate { value ->
                require(value.none(String::isBlank)) { "Empty path not allowed." }
            }

        /**
         * Hadolint failure threshold.
         *
         * This is interpreted by [ValidThreshold.fromString]. Invalid values are rejected during parsing.
         */
        private val threshold by option("--failure-threshold", "-t")
            .help("Lint failures at or above this threshold will be treated as errors.")
            .validate { value -> ValidThreshold.fromString(value) }

        /**
         * When enabled, missing Dockerfiles are treated as a failure rather than a warning.
         */
        private val strictFiles by option("--strict-files")
            .flag(default = false)
            .help("Fail if any specified Dockerfiles do not exist.")

        /**
         * Materialized options after parsing.
         *
         * Clikt executes [run] once parsing succeeds. This property is set there so that callers (e.g. [runCliJson])
         * can access the validated configuration.
         */
        lateinit var options: CliOptions
            private set

        override fun help(context: Context): String =
            "Run Hadolint against one or more Dockerfiles and emit a JSON result."

        override fun run() {
            options = CliOptions(
                dockerfiles = dockerfiles.ifEmpty { listOf("Dockerfile") },
                failureThreshold = threshold?.let(ValidThreshold::fromString) ?: ValidThreshold.default,
                strictFiles = strictFiles
            )
        }
    }

    /**
     * JSON encoder for serialising [HadolintCliResult].
     *
     * `encodeDefaults = true` keeps the JSON schema stable across early-termination paths (help/usage errors).
     */
    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    /**
     * Parses CLI arguments into an immutable, validated [CliOptions].
     *
     * This function exists primarily for unit testing and for code paths that need parsing without executing the full
     * JSON workflow.
     *
     * ## Notes
     *
     * - Clikt already provides robust parsing and usage errors; however, this wrapper normalizes errors into
     *   [IllegalArgumentException] for a simple call contract.
     * - Missing-value detection is handled before Clikt parsing to provide consistent messages across Clikt versions.
     *
     * @param args Raw command-line arguments.
     * @return Parsed [CliOptions].
     * @throws IllegalArgumentException If parsing fails, an unknown flag is provided, or a value is missing/invalid.
     */
    fun parseArgs(args: Array<String>): CliOptions {
        detectMissingOptionValue(
            args = args.toList(),
            optionNames = setOf("--dockerfile", "-f", "--failure-threshold", "-t")
        )?.let { message ->
            if (message.contains("failure-threshold") || message.contains("-t")) {
                throw IllegalArgumentException("Missing value for --failure-threshold")
            }
            throw IllegalArgumentException("Missing value for --dockerfile")
        }

        val command = HadolintCommand()
        return try {
            command.parse(args.toList())
            command.options
        } catch (_: PrintHelpMessage) {
            // parseArgs is a "parsing-only" API; printing help is the responsibility of [runCliJson].
            throw IllegalArgumentException("Unknown argument: --help")
        } catch (error: UsageError) {
            throw IllegalArgumentException(parseArgsErrorMessage(error))
        }
    }

    /**
     * Normalizes Clikt parsing exceptions into a stable, user-facing error message.
     *
     * @param error Clikt parse error.
     * @return A human-readable message suitable for stderr.
     */
    private fun parseArgsErrorMessage(error: UsageError): String = when (error) {
        is NoSuchOption -> "Unknown argument: ${extractNoSuchOptionName(error.message.orEmpty())}"
        else -> error.message ?: error.toString()
    }

    /**
     * Checks whether a command appears runnable (available on PATH).
     *
     * Implementation detail: attempts to run `{sh} <command> --version` and returns `true` iff it exits with code `0`.
     * Execution is bounded by a 2-second timeout.
     *
     * @param command Program name expected to be resolvable on PATH.
     * @return True if the command appears runnable.
     */
    fun hasCommand(command: String): Boolean = try {
        val process = ProcessBuilder(command, "--version")
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
        val completed = process.waitFor(2, TimeUnit.SECONDS)
        if (completed) process.exitValue() == 0 else {
            process.destroyForcibly()
            false
        }
    } catch (_: Exception) {
        false
    }

    /**
     * Checks whether a command line can be executed successfully.
     *
     * This is primarily used to probe Docker availability, e.g. `{sh} docker version`. Execution is bounded by a
     * 2-second timeout.
     *
     * @param command Command and arguments.
     * @return True if the process exits with code `0` within the timeout.
     */
    fun canRun(vararg command: String): Boolean = try {
        val process = ProcessBuilder(*command)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
        val completed = process.waitFor(2, TimeUnit.SECONDS)
        if (completed) process.exitValue() == 0 else {
            process.destroyForcibly()
            false
        }
    } catch (_: Exception) {
        false
    }

    /**
     * Resolves dockerfile path strings from [options] into a [ResolveResult].
     *
     * ## Resolution policy
     *
     * - Each input string is converted into a normalized absolute [Path].
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
     * Selects a [HadolintRunner] based on environment availability.
     *
     * - Prefer local `{sh} hadolint` when available.
     * - Otherwise fall back to Docker execution when Docker is available.
     *
     * @param hadolintAvailable Whether a local `{sh} hadolint` binary is available.
     * @param dockerAvailable Whether Docker is available for fallback execution.
     * @return A [HadolintRunner] compatible with the current environment.
     * @throws IllegalStateException If neither strategy is available.
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
     * Builds a default [HadolintCliResult] for early-termination paths.
     *
     * Used when parsing fails or help is requested. This keeps the JSON schema stable even when no linting occurs.
     *
     * @param exitCode Process exit code for the early result.
     * @param started Start timestamp (milliseconds since epoch).
     * @param finished End timestamp (milliseconds since epoch).
     * @param defaults Defaults used for fields that are normally derived from [CliOptions].
     * @return A complete [HadolintCliResult] with empty targets/missing/failed lists and an "unknown" runner.
     */
    private fun buildDefaultResult(
        exitCode: Int,
        started: Long,
        finished: Long,
        defaults: CliOptions
    ): HadolintCliResult = HadolintCliResult(
        exitCode = exitCode,
        threshold = defaults.failureThreshold.value,
        strictFiles = defaults.strictFiles,
        targets = emptyList(),
        missing = emptyList(),
        failed = emptyList(),
        runner = "unknown",
        startedAtEpochMs = started,
        finishedAtEpochMs = finished
    )

    /**
     * Serializes [result] to JSON and writes it to [out].
     *
     * This is the only place where JSON is emitted, ensuring stdout remains parseable and schema-stable.
     *
     * @param out Destination stream for JSON output.
     * @param result Result to encode.
     * @return The same [HadolintCliResult] for convenient chaining/testing.
     */
    private fun emitResult(out: PrintStream, result: HadolintCliResult): HadolintCliResult {
        out.println(json.encodeToString(result))
        return result
    }

    /**
     * Runs the CLI workflow and returns a JSON-friendly result.
     *
     * ## Behavior
     *
     * - Always emits exactly one JSON result via [emitResult].
     * - Writes usage and error diagnostics to [err].
     * - Returns a [HadolintCliResult] whose `exitCode` is suitable for [main].
     *
     * ## Parsing
     *
     * Parsing is performed by [parseHadolintOptions] and delegated to Clikt:
     *
     * - Help requested -> prints formatted help to [err] and emits a success JSON result (exit code `0`).
     * - Parse/validation error -> prints a normalized error message to [err] and emits a failure JSON result (exit code
     *   `1`).
     *
     * ## Testability
     *
     * The main side effects are injectable:
     *
     * - Environment probes: [hadolintAvailable], [dockerAvailable]
     * - Filesystem: [exists]
     * - Runner selection: [runnerSelector]
     * - Time: [nowEpochMs]
     * - Streams: [out], [err]
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
        val defaultOptions = defaultCliOptions()

        val parseOutcome = parseHadolintOptions(args, err, started, nowEpochMs, defaultOptions)
        if (parseOutcome is ParseOutcome.EarlyResult) {
            return emitResult(out, parseOutcome.result)
        }
        parseOutcome as ParseOutcome.Ok
        val options = parseOutcome.options

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

        val result = buildExecutionResult(
            options = options,
            resolved = resolved,
            execution = execution,
            startedAtEpochMs = started,
            finishedAtEpochMs = nowEpochMs()
        )
        return emitResult(out, result)
    }

    /**
     * Returns the default CLI options used for schema-stable early results.
     *
     * These defaults mirror the behavior of [HadolintCommand.run] when the user provides no arguments.
     */
    private fun defaultCliOptions(): CliOptions = CliOptions(
        dockerfiles = listOf("Dockerfile"),
        failureThreshold = ValidThreshold.default,
        strictFiles = false
    )

    /**
     * Parses arguments into [CliOptions] or materializes an early [HadolintCliResult].
     *
     * This function is the single place where Clikt parsing exceptions are translated into:
     *
     * - formatted help text (stderr) and exit code `0`, or
     * - a readable error message (stderr) and exit code `1`.
     *
     * @param args Raw CLI arguments.
     * @param err Destination for usage and diagnostics.
     * @param started Start timestamp captured by [runCliJson].
     * @param nowEpochMs Time source for the early result end timestamp.
     * @param defaultOptions Defaults used to fill required JSON fields on early termination.
     * @return [ParseOutcome.Ok] on success, otherwise [ParseOutcome.EarlyResult].
     */
    private fun parseHadolintOptions(
        args: Array<String>,
        err: PrintStream,
        started: Long,
        nowEpochMs: () -> Long,
        defaultOptions: CliOptions
    ): ParseOutcome {
        val command = HadolintCommand()
        return try {
            command.parse(args.toList())
            ParseOutcome.Ok(command.options)
        } catch (e: PrintHelpMessage) {
            printHelpMessage(e, err)
            ParseOutcome.EarlyResult(buildDefaultResult(0, started, nowEpochMs(), defaultOptions))
        } catch (e: UsageError) {
            err.println(parseArgsErrorMessage(e))
            ParseOutcome.EarlyResult(buildDefaultResult(1, started, nowEpochMs(), defaultOptions))
        }
    }

    /**
     * Prints formatted help text for a Clikt [PrintHelpMessage] exception.
     *
     * When Clikt provides a formatted help string via the command context, that help is printed verbatim. Otherwise, a
     * minimal fallback usage line is printed.
     *
     * @param error Help exception thrown by Clikt.
     * @param err Destination stream (stderr).
     */
    private fun printHelpMessage(
        error: PrintHelpMessage,
        err: PrintStream
    ) {
        val formattedHelp = error.context?.command?.getFormattedHelp(error)
        if (formattedHelp != null) {
            err.print(formattedHelp)
            return
        }
        err.println(error.message ?: "Usage: hadolint-cli [options]")
    }

    /**
     * Builds a full [HadolintCliResult] from a successful lint execution.
     *
     * @param options Parsed CLI options.
     * @param resolved Partitioned dockerfile paths (existing vs. missing).
     * @param execution Lint execution summary, including failures and runner information.
     * @param startedAtEpochMs Start timestamp captured before parsing/execution.
     * @param finishedAtEpochMs End timestamp captured after execution.
     * @return A complete [HadolintCliResult] suitable for JSON serialization.
     */
    private fun buildExecutionResult(
        options: CliOptions,
        resolved: ResolveResult,
        execution: LintExecution,
        startedAtEpochMs: Long,
        finishedAtEpochMs: Long
    ): HadolintCliResult = HadolintCliResult(
        exitCode = execution.exitCode.value,
        threshold = options.failureThreshold.value,
        strictFiles = options.strictFiles,
        targets = resolved.existing.map(Path::toString),
        missing = resolved.missing.map(Path::toString),
        failed = execution.failed.map(Path::toString),
        runner = execution.runnerId.name.lowercase(),
        startedAtEpochMs = startedAtEpochMs,
        finishedAtEpochMs = finishedAtEpochMs
    )

    /**
     * Process entry point.
     *
     * Executes [runCliJson] and terminates the process with the emitted [HadolintCliResult.exitCode].
     *
     * @param args Raw CLI arguments.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val result = runCliJson(args)
        exitProcess(result.exitCode)
    }
}
