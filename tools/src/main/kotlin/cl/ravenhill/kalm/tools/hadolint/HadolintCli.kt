package cl.ravenhill.kalm.tools.hadolint

import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 *
 */
object HadolintCli {
    /**
     * @param out
     */
    fun printUsage(out: PrintStream = System.out) {
        out.println(
            """
            |Usage:
            |  kotlin scripts/quality/Invoke-Hadolint.kts [options]
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
     * @param args
     * @return
     */
    fun parseArgs(args: Array<String>): CliOptions {
        val dockerfiles = mutableListOf<String>()
        var failureThreshold = ValidThreshold.default
        var strictFiles = false

        var i = 0
        while (i < args.size) {
            // Processes arguments; updates options; throws on unknown arguments
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
                    printUsage()
                    exitProcess(0)
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
     * @param i
     * @param args
     * @return
     */
    private fun parseFailureThreshold(
        i: Int,
        args: Array<String>
    ): Pair<ValidThreshold, Int> {
        var currentIndex = i
        if (currentIndex + 1 >= args.size) {
            throw IllegalArgumentException("Missing value for --failure-threshold")
        }
        val failureThreshold1: ValidThreshold = ValidThreshold.fromString(args[currentIndex + 1])
        currentIndex += 2
        return Pair(failureThreshold1, currentIndex)
    }

    /**
     * @param i
     * @param args
     * @param dockerfiles
     * @return
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
     * @param options
     * @param exists
     * @return
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
     * @param command
     * @return
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
     * @param command
     * @return
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
     * @param hadolintAvailable
     * @param dockerAvailable
     * @return
     */
    fun selectRunner(hadolintAvailable: Boolean, dockerAvailable: Boolean): HadolintRunner {
        return if (hadolintAvailable) {
            BinaryHadolintRunner()
        } else if (dockerAvailable) {
            DockerHadolintRunner()
        } else {
            error("Could not find `hadolint` and Docker is not available. Install one of them and try again.")
        }
    }

    /**
     * @param args
     * @param runnerSelector
     * @param hadolintAvailable
     * @param dockerAvailable
     * @param exists
     * @param out
     * @param err
     * @return
     */
    fun runCli(
        args: Array<String>,
        runnerSelector: (Boolean, Boolean) -> HadolintRunner = ::selectRunner,
        hadolintAvailable: () -> Boolean = { hasCommand("hadolint") },
        dockerAvailable: () -> Boolean = { canRun("docker", "version") },
        exists: (Path) -> Boolean = Files::exists,
        out: PrintStream = System.out,
        err: PrintStream = System.err
    ): Int {
        val options = parseArgs(args)
        val runner = runnerSelector(hadolintAvailable(), dockerAvailable())
        val resolved = resolveDockerfilePaths(options, exists, out)
        validateDockerfiles(resolved, options)

        out.println("Linting Dockerfiles with threshold '${options.failureThreshold}'...")
        out.println("Targets: ${resolved.existing.joinToString(", ")}")

        val failed = runHadolintOnResolvedFiles(resolved, out, runner, options, err)

        if (failed.isNotEmpty()) {
            err.println("Hadolint reported issues in: ${failed.joinToString(", ")}")
            return 1
        }

        out.println("Hadolint completed successfully.")
        return 0
    }

    /**
     * @param resolved
     * @param out
     * @param runner
     * @param options
     * @param err
     * @return
     */
    private fun runHadolintOnResolvedFiles(
        resolved: ResolveResult,
        out: PrintStream,
        runner: HadolintRunner,
        options: CliOptions,
        err: PrintStream
    ): MutableList<Path> {
        val failed = mutableListOf<Path>()
        // Runs linter; collects paths where linter fails
        for (file in resolved.existing) {
            out.println("Running Hadolint on: $file")
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
     * @param resolved
     * @param options
     * @throws IllegalStateException
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
     * @param options
     * @param exists
     * @param out
     * @return
     */
    private fun resolveDockerfilePaths(
        options: CliOptions,
        exists: (Path) -> Boolean,
        out: PrintStream
    ): ResolveResult {
        val resolved = resolveDockerfiles(options, exists)
        resolved.missing.forEach { out.println("WARNING: Dockerfile not found and will be skipped: $it") }
        out.println("Found ${resolved.existing.size} Dockerfile(s); ${resolved.missing.size} missing.")
        return resolved
    }

    /**
     * @param args
     */
    @JvmStatic
    fun main(args: Array<String>) {
        exitProcess(runCli(args))
    }
}
