package cl.ravenhill.kalm.tools.hadolint

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
    /**
     *
     */
    private class HelpRequested : RuntimeException()

    /**
     *
     */
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

    private sealed interface Parsed {
        data class Ok(val options: CliOptions) : Parsed
        data class Done(val result: HadolintCliResult) : Parsed
    }

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

        val result = when (val parsed: Parsed = parseAndExecute(args, err, defaultOptions, started, nowEpochMs)) {
            is Parsed.Done -> parsed.result
            is Parsed.Ok -> {
                lintDockerfiles(
                    parsed,
                    exists,
                    err,
                    runnerSelector,
                    hadolintAvailable,
                    dockerAvailable,
                    nowEpochMs,
                    started
                )
            }
        }

        return emitResult(out, result)
    }

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
            runnerSelector,
            hadolintAvailable,
            dockerAvailable,
            resolved,
            options,
            logger
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

    private fun emitResult(
        out: PrintStream,
        result: HadolintCliResult
    ): HadolintCliResult {
        out.println(json.encodeToString(result))
        return result
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
