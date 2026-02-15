package cl.ravenhill.kalm.tools.hadolint

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
class HadolintCliTest : FreeSpec({
    "Given default arguments" - {
        "When parsing" - {
            "Then defaults are applied" {
                val options = HadolintCli.parseArgs(emptyArray())

                options.dockerfiles.shouldContainExactly(listOf("Dockerfile"))
                options.failureThreshold shouldBe "warning"
                options.strictFiles shouldBe false
            }
        }
    }

    "Given short flags and strict files" - {
        "When parsing" - {
            "Then values are mapped correctly" {
                val options = HadolintCli.parseArgs(
                    arrayOf(
                        "-f",
                        "Dockerfile",
                        "-f",
                        "Dockerfile.alt",
                        "-t",
                        "error",
                        "--strict-files"
                    )
                )

                options.dockerfiles.shouldContainExactly(listOf("Dockerfile", "Dockerfile.alt"))
                options.failureThreshold shouldBe "error"
                options.strictFiles shouldBe true
            }
        }
    }

    "Given an invalid threshold" - {
        "When parsing" - {
            "Then an error is raised" {
                shouldThrow<IllegalStateException> {
                    HadolintCli.parseArgs(arrayOf("--failure-threshold", "nope"))
                }
            }
        }
    }

    "Given existing and missing Dockerfiles" - {
        "When resolving" - {
            "Then they are separated correctly" {
                val existingFile = Files.createTempFile("Dockerfile", ".tmp")
                existingFile.toFile().deleteOnExit()
                val options = CliOptions(dockerfiles = listOf(existingFile.toString(), "missing"))

                val resolved = HadolintCli.resolveDockerfiles(options) { path -> path == existingFile }

                resolved.existing.shouldContainExactly(listOf(existingFile))
                resolved.missing.size shouldBe 1
            }
        }
    }

    "Given a runner that reports failure" - {
        "When running the CLI" - {
            "Then it returns a non-zero exit code" {
                val existingFile = Files.createTempFile("Dockerfile", ".tmp")
                existingFile.toFile().deleteOnExit()
                val runner = FakeRunner(exitCode = 1)
                val out = ByteArrayOutputStream()
                val err = ByteArrayOutputStream()

                val exitCode = HadolintCli.runCli(
                    args = arrayOf("--dockerfile", existingFile.toString()),
                    runnerSelector = { _, _ -> runner },
                    hadolintAvailable = { false },
                    dockerAvailable = { false },
                    exists = { path -> path == existingFile },
                    out = PrintStream(out),
                    err = PrintStream(err)
                )

                exitCode shouldBe 1
                runner.runs.shouldContainExactly(listOf(existingFile))
            }
        }
    }

    "Given a runner that reports success" - {
        "When running the CLI" - {
            "Then it returns zero and produces no stderr" {
                val existingFile = Files.createTempFile("Dockerfile", ".tmp")
                existingFile.toFile().deleteOnExit()
                val runner = FakeRunner(exitCode = 0)
                val out = ByteArrayOutputStream()
                val err = ByteArrayOutputStream()

                val exitCode = HadolintCli.runCli(
                    args = arrayOf("--dockerfile", existingFile.toString()),
                    runnerSelector = { _, _ -> runner },
                    hadolintAvailable = { false },
                    dockerAvailable = { false },
                    exists = { path -> path == existingFile },
                    out = PrintStream(out),
                    err = PrintStream(err)
                )

                exitCode shouldBe 0
                runner.runs.shouldContainExactly(listOf(existingFile))
                err.toString() shouldBe ""
            }
        }
    }
})

private class FakeRunner(private val exitCode: Int) : HadolintRunner {
    val runs = mutableListOf<Path>()

    override fun commandDescription(file: Path, threshold: String): String =
        "fake-hadolint"

    override fun run(file: Path, threshold: String): Int {
        runs.add(file)
        return exitCode
    }
}
