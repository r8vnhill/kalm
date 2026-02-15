package cl.ravenhill.kalm.tools.hadolint

import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private const val DEFAULT_THRESHOLD = "warning"
private val VALID_THRESHOLDS = setOf("error", "warning", "info", "style", "ignore")

data class CliOptions(
    val dockerfiles: List<String> = listOf("Dockerfile"),
    val failureThreshold: String = DEFAULT_THRESHOLD,
    val strictFiles: Boolean = false
)

data class ResolveResult(
    val existing: List<Path>,
    val missing: List<Path>
)

interface HadolintRunner {
    fun commandDescription(file: Path, threshold: String): String
    fun run(file: Path, threshold: String): Int
}

class BinaryHadolintRunner : HadolintRunner {
    override fun commandDescription(file: Path, threshold: String): String =
        "hadolint --failure-threshold $threshold ${file}"

    override fun run(file: Path, threshold: String): Int {
        val process = ProcessBuilder(
            "hadolint",
            "--failure-threshold",
            threshold,
            file.toString()
        )
            .inheritIO()
            .start()
        return process.waitFor()
    }
}

class DockerHadolintRunner : HadolintRunner {
    override fun commandDescription(file: Path, threshold: String): String =
        "docker run --rm -i hadolint/hadolint --failure-threshold $threshold -"

    override fun run(file: Path, threshold: String): Int {
        val process = ProcessBuilder(
            "docker",
            "run",
            "--rm",
            "-i",
            "hadolint/hadolint",
            "--failure-threshold",
            threshold,
            "-"
        )
            .redirectInput(file.toFile())
            .inheritIO()
            .start()
        return process.waitFor()
    }
}

object HadolintCli {
    fun printUsage(out: PrintStream = System.out) {
        out.println(
            """
            Usage:
              kotlin scripts/quality/Invoke-Hadolint.kts [options]

            Options:
              --dockerfile, -f <path>       Dockerfile path to lint (repeatable)
              --failure-threshold, -t <level>
                                           error|warning|info|style|ignore (default: warning)
              --strict-files                Fail if any Dockerfile is missing
              --help                        Show this help
            """.trimIndent()
        )
    }

    fun parseArgs(args: Array<String>): CliOptions {
        val dockerfiles = mutableListOf<String>()
        var failureThreshold = DEFAULT_THRESHOLD
        var strictFiles = false

        var i = 0
        while (i < args.size) {
            when (val arg = args[i]) {
                "--dockerfile", "-f" -> {
                    if (i + 1 >= args.size) {
                        throw IllegalArgumentException("Missing value for --dockerfile")
                    }
                    dockerfiles += args[i + 1]
                    i += 2
                }

                "--failure-threshold", "-t" -> {
                    if (i + 1 >= args.size) {
                        throw IllegalArgumentException("Missing value for --failure-threshold")
                    }
                    failureThreshold = args[i + 1].lowercase()
                    i += 2
                }

                "--strict-files" -> {
                    strictFiles = true
                    i += 1
                }

                "--help", "-h" -> {
                    printUsage()
                    kotlin.system.exitProcess(0)
                }

                else -> throw IllegalArgumentException("Unknown argument: $arg")
            }
        }

        if (failureThreshold !in VALID_THRESHOLDS) {
            throw IllegalArgumentException(
                "Invalid --failure-threshold '$failureThreshold'. Valid values: ${VALID_THRESHOLDS.joinToString(", ")}"
            )
        }

        return CliOptions(
            dockerfiles = if (dockerfiles.isEmpty()) listOf("Dockerfile") else dockerfiles,
            failureThreshold = failureThreshold,
            strictFiles = strictFiles
        )
    }

    fun resolveDockerfiles(
        options: CliOptions,
        exists: (Path) -> Boolean = Files::exists
    ): ResolveResult {
        val dockerfiles = options.dockerfiles.map { Paths.get(it).toAbsolutePath().normalize() }
        val existing = dockerfiles.filter { exists(it) }
        val missing = dockerfiles.filterNot { exists(it) }
        return ResolveResult(existing = existing, missing = missing)
    }

    fun hasCommand(command: String): Boolean = try {
        val process = ProcessBuilder(command, "--version")
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
        process.waitFor() == 0
    } catch (_: Exception) {
        false
    }

    fun canRun(vararg command: String): Boolean = try {
        val process = ProcessBuilder(*command)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
        process.waitFor() == 0
    } catch (_: Exception) {
        false
    }

    fun selectRunner(hadolintAvailable: Boolean, dockerAvailable: Boolean): HadolintRunner {
        return if (hadolintAvailable) {
            BinaryHadolintRunner()
        } else if (dockerAvailable) {
            DockerHadolintRunner()
        } else {
            error("Could not find `hadolint` and Docker is not available. Install one of them and try again.")
        }
    }

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

        val resolved = resolveDockerfiles(options, exists)
        resolved.missing.forEach { out.println("WARNING: Dockerfile not found and will be skipped: $it") }
        out.println("Found ${resolved.existing.size} Dockerfile(s); ${resolved.missing.size} missing.")

        if (resolved.missing.isNotEmpty() && options.strictFiles) {
            error("Missing Dockerfiles while --strict-files is enabled.")
        }

        if (resolved.existing.isEmpty()) {
            error("No valid Dockerfiles were found to lint.")
        }

        out.println("Linting Dockerfiles with threshold '${options.failureThreshold}'...")
        out.println("Targets: ${resolved.existing.joinToString(", ")}")

        val failed = mutableListOf<Path>()
        for (file in resolved.existing) {
            out.println("Running Hadolint on: $file")
            val exitCode = runner.run(file, options.failureThreshold)
            if (exitCode != 0) {
                err.println("Hadolint failed. Command: ${runner.commandDescription(file, options.failureThreshold)}")
                failed.add(file)
            }
        }

        if (failed.isNotEmpty()) {
            err.println("Hadolint reported issues in: ${failed.joinToString(", ")}")
            return 1
        }

        out.println("Hadolint completed successfully.")
        return 0
    }

    @JvmStatic
    fun main(args: Array<String>) {
        kotlin.system.exitProcess(runCli(args))
    }
}
