/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.tools.locks

import cl.ravenhill.kalm.tools.cli.detectMissingOptionValue
import cl.ravenhill.kalm.tools.cli.extractNoSuchOptionName
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoSuchOption
import com.github.ajalt.clikt.core.NoSuchSubcommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option

internal fun parseExecutionRequest(args: List<String>): ExecutionRequestResult {
    val jsonFlags = args.count { it == "--json" }
    if (jsonFlags > 1) {
        return ExecutionRequestResult.Error("Duplicate option --json")
    }
    return ExecutionRequestResult.Ok(
        ExecutionRequest(
            json = jsonFlags == 1,
            commandArgs = args.filterNot { it == "--json" }
        )
    )
}

internal fun parseCommand(args: List<String>): ParseResult {
    val cliArgs = args.takeWhile { it != "--" }
    val missingValueMessage = detectMissingOptionValue(
        args = cliArgs,
        optionNames = setOf("--module", "--configuration")
    )
    if (missingValueMessage != null) {
        return ParseResult.Error(missingValueMessage)
    }
    val state = ParsedCommandState()
    return try {
        RootParser(state).parse(cliArgs)
        ParseResult.Ok(requireNotNull(state.command))
    } catch (error: UsageError) {
        ParseResult.Error(normalizeError(error, cliArgs))
    }
}

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
            throw UsageError("Missing command. Supported commands: $supportedCommandsText")
        }
    }
}

private class WriteAllParser(private val state: ParsedCommandState) : CliktCommand(name = "write-all") {
    override fun run() {
        state.command = LocksCommand.WriteAll
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
        state.command = LocksCommand.WriteModule(module)
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
        state.command = LocksCommand.WriteConfiguration(module, configuration)
    }
}

private class DiffParser(private val state: ParsedCommandState) : CliktCommand(name = "diff") {
    override fun run() {
        state.command = LocksCommand.Diff
    }
}

private fun normalizeError(error: UsageError, args: List<String>): String = when (error) {
    is NoSuchSubcommand -> "Unknown command '${args.firstOrNull().orEmpty()}'. Supported commands: $supportedCommandsText"
    is NoSuchOption -> "Unknown option ${findUnknownOption(args) ?: extractNoSuchOptionName(error.message.orEmpty())}"
    else -> error.message ?: "Invalid arguments"
}

private fun findUnknownOption(args: List<String>): String? {
    val allowedOptions = when (args.firstOrNull()) {
        "write-module" -> setOf("--module")
        "write-configuration" -> setOf("--module", "--configuration")
        else -> emptySet()
    }
    val tokens = args.drop(1)
    var index = 0
    while (index < tokens.size) {
        val token = tokens[index]
        if (token == "--") {
            break
        }
        if (!token.startsWith("--")) {
            index += 1
            continue
        }
        val optionName = token.substringBefore('=')
        if (optionName !in allowedOptions) {
            return optionName
        }
        index += if (!token.contains('=')) 2 else 1
    }
    return null
}
