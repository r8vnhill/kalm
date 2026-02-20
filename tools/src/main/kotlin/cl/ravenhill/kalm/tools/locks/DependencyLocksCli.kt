/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.tools.locks

import cl.ravenhill.kalm.tools.locks.DependencyLocksCli.main
import cl.ravenhill.kalm.tools.locks.DependencyLocksCli.run
import kotlin.system.exitProcess

/**
 * CLI entrypoint for dependency lockfile workflows.
 *
 * This CLI does **not** modify lockfiles directly. Instead, it resolves the user’s intent into a deterministic shell
 * command that can be copied into a terminal (or consumed by tooling in JSON mode).
 *
 * ## Responsibilities
 *
 * - Parse the execution request (e.g., detect `--json` and normalize arguments).
 * - Recognize help invocations (`help`, `--help`, `-h`).
 * - Parse the requested subcommand into a domain [LocksCommand].
 * - Render a corresponding command string via [renderCommand].
 * - Provide a stable programmatic result via [CliResult], suitable for:
 *   - interactive use (plain text)
 *   - automation (JSON output)
 *
 * ## Separation of Concerns
 *
 * This object intentionally separates:
 *
 * - *parsing* ([parseExecutionRequest], [parseCommand])
 * - *domain representation* ([LocksCommand])
 * - *rendering* ([renderCommand], [CliResult.renderCliResult])
 * - *process concerns* ([main], [exitProcess])
 *
 * Keeping [run] side-effect-free improves testability and enables reuse by wrappers.
 */
object DependencyLocksCli {

    /**
     * Result of executing the CLI in "library mode" (i.e., via [run]).
     *
     * This type is intentionally minimal and stable because it is:
     *
     * - returned to callers (tests, wrappers),
     * - rendered to either plain text or JSON,
     * - mapped to an exit code via [toExitCode].
     *
     * Implementations:
     *
     * - [Success] indicates a valid request was parsed and rendered into a command.
     * - [Failure] indicates invalid arguments or an unsupported request.
     * - [Help] indicates a help invocation (e.g., `help`, `--help`, `-h`).
     */
    sealed interface CliResult {

        /**
         * Successful parsing and rendering.
         *
         * @property command The rendered shell command to execute. This string is intended to be copy-pasted into a
         *   terminal or executed by a wrapper.
         */
        data class Success(val command: String) : CliResult

        /**
         * Unsuccessful execution.
         *
         * @property message Human-readable error message describing what failed. The message is expected to be stable
         *   enough for users but should not be relied on as a strict API.
         */
        data class Failure(val message: String) : CliResult

        /**
         * Help request detected.
         *
         * This is treated as a successful outcome (exit code `0`) and renders usage text.
         */
        data object Help : CliResult
    }

    /**
     * Executes the CLI in a side-effect-free manner.
     *
     * This function performs argument parsing and returns a [CliResult] without writing to stdout/stderr or terminating
     * the process. It is suitable for:
     *
     * - unit tests
     * - wrapper scripts
     * - embedding in other tooling
     *
     * Process concerns (printing and exit codes) are handled by [main].
     *
     * @param args Raw command-line arguments passed to the program.
     * @return A [CliResult] representing the outcome of parsing and rendering.
     */
    fun run(args: List<String>): CliResult = when (val request = parseExecutionRequest(args)) {
        is ExecutionRequestResult.Ok -> run(request.request)
        is ExecutionRequestResult.Error -> CliResult.Failure(request.message)
    }

    /**
     * Executes a previously validated [ExecutionRequest].
     *
     * The request is assumed to have already:
     *
     * - validated global flags (e.g., duplicate `--json`),
     * - normalized out-of-band flags into [ExecutionRequest.json],
     * - produced a command argument list in [ExecutionRequest.commandArgs].
     *
     * @param request Normalised execution request.
     * @return A [CliResult] describing the parsed command or failure.
     */
    private fun run(request: ExecutionRequest): CliResult {
        val commandArgs = request.commandArgs

        // Help is handled as a first-class outcome because it should not be treated as an error.
        if (commandArgs.firstOrNull() == "help" || commandArgs.any { it == "--help" || it == "-h" }) {
            return CliResult.Help
        }

        return when (val parsed = parseCommand(commandArgs)) {
            is ParseResult.Ok -> CliResult.Success(renderCommand(parsed.command))
            is ParseResult.Error -> CliResult.Failure(parsed.message)
        }
    }

    /**
     * Program entrypoint.
     *
     * This method is responsible for process-level behavior:
     *
     * - Determine JSON mode from the request.
     * - Execute [run] using a normalised [ExecutionRequest] when available.
     * - Render the [CliResult] via [CliResult.renderCliResult].
     * - Print to stdout/stderr depending on the render contract.
     * - Exit with a stable code via [CliResult.toExitCode].
     *
     * Keeping these responsibilities in [main] (instead of [run]) makes the CLI easier to test.
     *
     * @param args Raw command-line arguments from the JVM.
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val requestResult = parseExecutionRequest(args.toList())

        val (jsonMode, result) = when (requestResult) {
            is ExecutionRequestResult.Ok ->
                requestResult.request.json to run(requestResult.request)

            // If parsing the execution request fails, default to plain rendering so users see the message.
            is ExecutionRequestResult.Error ->
                false to CliResult.Failure(requestResult.message)
        }

        val rendered = result.renderCliResult(jsonMode)
        log(rendered)
        exitProcess(result.toExitCode())
    }

    /**
     * Logs the rendered CLI result to either the standard output or standard error,
     * depending on the `toStdErr` flag in the provided `RenderedCliResult`.
     *
     * @param rendered The rendered CLI result containing the text to log and a flag indicating whether the output
     *   should go to the standard error.
     */
    private fun log(rendered: RenderedCliResult) = if (rendered.toStdErr) {
        System.err.println(rendered.text)
    } else {
        println(rendered.text)
    }
}
