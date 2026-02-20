/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.tools.locks

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

    data class ExecutionRequest(
        val json: Boolean,
        val args: Array<String>
    )

    private sealed interface ExecutionRequestResult {
        data class Ok(val request: ExecutionRequest) : ExecutionRequestResult
        data class Error(val message: String) : ExecutionRequestResult
    }

    private fun parseExecutionRequest(args: Array<String>): ExecutionRequestResult {
        val jsonFlags = args.count { it == "--json" }
        if (jsonFlags > 1) {
            return ExecutionRequestResult.Error("Duplicate option --json")
        }
        val normalizedArgs = args.filterNot { it == "--json" }.toTypedArray()
        return ExecutionRequestResult.Ok(
            ExecutionRequest(
                json = jsonFlags == 1,
                args = normalizedArgs
            )
        )
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

    fun run(args: Array<String>): CliResult {
        val requestResult = parseExecutionRequest(args)
        if (requestResult is ExecutionRequestResult.Error) {
            return CliResult.Failure(requestResult.message)
        }
        requestResult as ExecutionRequestResult.Ok
        val command = parseCommand(requestResult.request.args) ?: return CliResult.Help
        return when (command) {
            is ParseResult.Error -> CliResult.Failure(command.message)
            is ParseResult.Ok -> CliResult.Success(render(command.command))
        }
    }

    private sealed interface ParseResult {
        data class Ok(val command: Command) : ParseResult
        data class Error(val message: String) : ParseResult
    }

    private fun parseCommand(args: Array<String>): ParseResult? {
        if (args.isEmpty()) {
            return ParseResult.Error("Missing command. Supported commands: $SUPPORTED_COMMANDS")
        }
        val commandName = args.first()
        if (commandName == "help" || commandName == "--help") {
            return null
        }
        val options = args.drop(1)
        return when (commandName) {
            "write-all" -> parseNoOptions(commandName, options, Command.WriteAll)
            "write-module" -> parseWriteModule(options)
            "write-configuration" -> parseWriteConfiguration(options)
            "diff" -> parseNoOptions(commandName, options, Command.Diff)
            else -> ParseResult.Error("Unknown command '$commandName'. Supported commands: $SUPPORTED_COMMANDS")
        }
    }

    private fun parseNoOptions(
        commandName: String,
        options: List<String>,
        command: Command
    ): ParseResult {
        if (options.isNotEmpty()) {
            return ParseResult.Error("Unknown option ${options.first()}")
        }
        return ParseResult.Ok(command)
    }

    private fun parseWriteModule(options: List<String>): ParseResult {
        val parsed = parseOptions(options, setOf("--module"))
        if (parsed is ParseResult.Error) return parsed
        parsed as ParsedOptions
        val module = parsed.values["--module"]
            ?: return ParseResult.Error("Missing required option --module for write-module")
        return ParseResult.Ok(Command.WriteModule(module))
    }

    private fun parseWriteConfiguration(options: List<String>): ParseResult {
        val parsed = parseOptions(options, setOf("--module", "--configuration"))
        if (parsed is ParseResult.Error) return parsed
        parsed as ParsedOptions
        val module = parsed.values["--module"]
        val configuration = parsed.values["--configuration"]
        if (module == null || configuration == null) {
            return ParseResult.Error("write-configuration requires --module and --configuration")
        }
        return ParseResult.Ok(Command.WriteConfiguration(module, configuration))
    }

    private data class ParsedOptions(val values: Map<String, String>)

    private fun parseOptions(
        tokens: List<String>,
        allowed: Set<String>
    ): Any {
        val values = mutableMapOf<String, String>()
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            if (!token.startsWith("--")) {
                return ParseResult.Error("Unknown option $token")
            }
            val (name, value, nextIndexOrNull) = if (token.contains('=')) {
                val split = token.split("=", limit = 2)
                Triple(split[0], split[1], null)
            } else {
                val next = tokens.getOrNull(index + 1)
                if (next == null || next.startsWith("--")) {
                    if (token !in allowed) {
                        return ParseResult.Error("Unknown option $token")
                    }
                    return ParseResult.Error("Missing value for option $token")
                }
                Triple(token, next, index + 2)
            }

            if (name !in allowed) {
                return ParseResult.Error("Unknown option $name")
            }
            if (name in values) {
                return ParseResult.Error("Duplicate option $name")
            }
            if (value.isBlank()) {
                return ParseResult.Error("Missing value for option $name")
            }

            values[name] = value
            index = nextIndexOrNull ?: index + 1
        }
        return ParsedOptions(values)
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
