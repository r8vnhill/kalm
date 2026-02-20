/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.tools.locks

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoSuchOption
import com.github.ajalt.clikt.core.NoSuchSubcommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import cl.ravenhill.kalm.tools.cli.detectMissingOptionValue
import cl.ravenhill.kalm.tools.cli.extractNoSuchOptionName
import kotlin.system.exitProcess

object DependencyLocksCli {

    private const val SUPPORTED_COMMANDS = "write-all, write-module, write-configuration, diff"
    private const val USAGE = """
        Usage:
          locks-cli [--json] write-all
          locks-cli [--json] write-module --module :core
          locks-cli [--json] write-configuration --module :core --configuration testRuntimeClasspath
          locks-cli [--json] diff
          locks-cli help | --help

        Legacy examples:
          locks-cli write-all
          locks-cli write-module --module :core
          locks-cli write-configuration --module :core --configuration testRuntimeClasspath
          locks-cli diff
    """

    private sealed interface Command {
        data object WriteAll : Command
        data class WriteModule(val module: String) : Command
        data class WriteConfiguration(val module: String, val configuration: String) : Command
        data object Diff : Command
    }

    sealed interface CliResult {
        data class Success(val command: String) : CliResult
        data class Failure(val message: String) : CliResult
        data object Help : CliResult
    }

    private fun jsonEscape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun asJson(result: CliResult): String = when (result) {
        is CliResult.Success ->
            """{"exitCode":0,"command":"${jsonEscape(result.command)}","message":null}"""
        is CliResult.Failure ->
            """{"exitCode":1,"command":null,"message":"${jsonEscape(result.message)}"}"""
        CliResult.Help ->
            """{"exitCode":0,"command":null,"message":"${jsonEscape(USAGE.trimIndent())}"}"""
    }

    private data class ExecutionRequest(
        val json: Boolean,
        val commandArgs: Array<String>
    )

    private sealed interface ExecutionRequestResult {
        data class Ok(val request: ExecutionRequest) : ExecutionRequestResult
        data class Error(val message: String) : ExecutionRequestResult
    }

    private sealed interface ParseResult {
        data class Ok(val command: Command) : ParseResult
        data class Error(val message: String) : ParseResult
    }

    private data class ParsedCommandState(var command: Command? = null)

    private class RootParser(private val state: ParsedCommandState) : CliktCommand(name = "locks-cli") {
        override val invokeWithoutSubcommand: Boolean = true
        override val printHelpOnEmptyArgs: Boolean = false

        init {
            subcommands(
                WriteAllParser(state),
                WriteModuleParser(state),
                WriteConfigurationParser(state),
                DiffParser(state)
            )
        }

        override fun run() {
            if (currentContext.invokedSubcommand == null) {
                throw UsageError("Missing command. Supported commands: $SUPPORTED_COMMANDS")
            }
        }
    }

    private class WriteAllParser(private val state: ParsedCommandState) : CliktCommand(name = "write-all") {
        override fun run() {
            state.command = Command.WriteAll
        }
    }

    private class WriteModuleParser(private val state: ParsedCommandState) : CliktCommand(name = "write-module") {
        private val modules by option("--module").multiple()

        override fun run() {
            if (modules.isEmpty()) {
                throw UsageError("Missing required option --module for write-module")
            }
            if (modules.size > 1) {
                throw UsageError("Duplicate option --module")
            }
            val module = modules.single()
            if (module.isBlank()) {
                throw UsageError("Missing value for option --module")
            }
            state.command = Command.WriteModule(module)
        }
    }

    private class WriteConfigurationParser(private val state: ParsedCommandState) :
        CliktCommand(name = "write-configuration") {
        private val modules by option("--module").multiple()
        private val configurations by option("--configuration").multiple()

        override fun run() {
            if (modules.isEmpty() || configurations.isEmpty()) {
                throw UsageError("write-configuration requires --module and --configuration")
            }
            if (modules.size > 1) {
                throw UsageError("Duplicate option --module")
            }
            if (configurations.size > 1) {
                throw UsageError("Duplicate option --configuration")
            }
            val module = modules.single()
            val configuration = configurations.single()
            if (module.isBlank()) {
                throw UsageError("Missing value for option --module")
            }
            if (configuration.isBlank()) {
                throw UsageError("Missing value for option --configuration")
            }
            state.command = Command.WriteConfiguration(module, configuration)
        }
    }

    private class DiffParser(private val state: ParsedCommandState) : CliktCommand(name = "diff") {
        override fun run() {
            state.command = Command.Diff
        }
    }

    private fun parseExecutionRequest(args: Array<String>): ExecutionRequestResult {
        val jsonFlags = args.count { it == "--json" }
        if (jsonFlags > 1) {
            return ExecutionRequestResult.Error("Duplicate option --json")
        }
        return ExecutionRequestResult.Ok(
            ExecutionRequest(
                json = jsonFlags == 1,
                commandArgs = args.filterNot { it == "--json" }.toTypedArray()
            )
        )
    }

    fun run(args: Array<String>): CliResult {
        val request = parseExecutionRequest(args)
        if (request is ExecutionRequestResult.Error) {
            return CliResult.Failure(request.message)
        }
        request as ExecutionRequestResult.Ok
        val commandArgs = request.request.commandArgs
        if (commandArgs.firstOrNull() == "help" || commandArgs.any { it == "--help" || it == "-h" }) {
            return CliResult.Help
        }
        return when (val parsed = parseCommand(commandArgs)) {
            is ParseResult.Ok -> CliResult.Success(render(parsed.command))
            is ParseResult.Error -> CliResult.Failure(parsed.message)
        }
    }

    private fun parseCommand(args: Array<String>): ParseResult {
        val missingValueMessage = detectMissingOptionValue(
            args = args.toList(),
            optionNames = setOf("--module", "--configuration")
        )
        if (missingValueMessage != null) {
            return ParseResult.Error(missingValueMessage)
        }
        val state = ParsedCommandState()
        return try {
            RootParser(state).parse(args.toList())
            ParseResult.Ok(requireNotNull(state.command))
        } catch (error: UsageError) {
            ParseResult.Error(normalizeError(error, args))
        }
    }

    private fun normalizeError(
        error: UsageError,
        args: Array<String>
    ): String = when (error) {
        is NoSuchSubcommand -> "Unknown command '${args.firstOrNull().orEmpty()}'. Supported commands: $SUPPORTED_COMMANDS"
        is NoSuchOption -> "Unknown option ${findUnknownOption(args) ?: extractNoSuchOptionName(error.message.orEmpty())}"
        else -> error.message ?: "Invalid arguments"
    }

    private fun findUnknownOption(args: Array<String>): String? {
        val allowedOptions = when (args.firstOrNull()) {
            "write-module" -> setOf("--module")
            "write-configuration" -> setOf("--module", "--configuration")
            else -> emptySet()
        }
        val tokens = args.drop(1)
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            if (!token.startsWith("--")) {
                index += 1
                continue
            }
            val optionName = token.substringBefore('=')
            if (optionName !in allowedOptions) {
                return optionName
            }
            if (!token.contains('=')) {
                index += 2
            } else {
                index += 1
            }
        }
        return null
    }

    private fun render(command: Command): String = when (command) {
        Command.WriteAll -> "./gradlew preflight --write-locks --no-parallel"
        is Command.WriteModule -> "./gradlew \"${command.module}:dependencies\" --write-locks --no-parallel"
        is Command.WriteConfiguration ->
            "./gradlew \"${command.module}:dependencies\" --configuration \"${command.configuration}\" --write-locks --no-parallel"
        Command.Diff -> "git diff -- **/gradle.lockfile settings-gradle.lockfile"
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val requestResult = parseExecutionRequest(args)
        val jsonMode = when (requestResult) {
            is ExecutionRequestResult.Ok -> requestResult.request.json
            else -> false
        }
        if (requestResult is ExecutionRequestResult.Error) {
            if (jsonMode) {
                System.out.println(asJson(CliResult.Failure(requestResult.message)))
            } else {
                System.err.println(requestResult.message)
                System.err.println(USAGE.trimIndent())
            }
            exitProcess(1)
        }

        when (val result = run(args)) {
            is CliResult.Success -> {
                if (jsonMode) {
                    System.out.println(asJson(result))
                } else {
                    println(result.command)
                }
                exitProcess(0)
            }
            is CliResult.Failure -> {
                if (jsonMode) {
                    System.out.println(asJson(result))
                } else {
                    System.err.println(result.message)
                    System.err.println(USAGE.trimIndent())
                }
                exitProcess(1)
            }
            CliResult.Help -> {
                if (jsonMode) {
                    System.out.println(asJson(result))
                } else {
                    System.err.println(USAGE.trimIndent())
                }
                exitProcess(0)
            }
        }
    }
}
