/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.tools.locks

import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class DependencyLocksCliTest : FreeSpec({

    "Given write-all command" - {
        "When running the CLI parser" - {
            "Then the default write-locks workflow is returned" {
                val result = DependencyLocksCli.run(arrayOf("write-all"))
                result shouldBe DependencyLocksCli.CliResult.Success(
                    "./gradlew preflight --write-locks --no-parallel"
                )
            }

            "Then --json keeps command semantics unchanged" {
                val result = DependencyLocksCli.run(arrayOf("--json", "write-all"))
                result shouldBe DependencyLocksCli.CliResult.Success(
                    "./gradlew preflight --write-locks --no-parallel"
                )
            }
        }
    }

    "Given write-module command" - {
        "When module is provided" - {
            "Then module-specific write-locks command is returned" {
                DependencyLocksCli.run(arrayOf("write-module", "--module", ":core")) shouldBe
                    DependencyLocksCli.CliResult.Success(
                        "./gradlew \":core:dependencies\" --write-locks --no-parallel"
                    )
            }

            "Then --module=value form is accepted" {
                DependencyLocksCli.run(arrayOf("write-module", "--module=:core")) shouldBe
                    DependencyLocksCli.CliResult.Success(
                        "./gradlew \":core:dependencies\" --write-locks --no-parallel"
                    )
            }
        }

        "When module is missing" - {
            "Then a clear error is reported" {
                DependencyLocksCli.run(arrayOf("write-module")) shouldBe
                    DependencyLocksCli.CliResult.Failure("Missing required option --module for write-module")
            }

            "Then missing module value is reported" {
                DependencyLocksCli.run(arrayOf("write-module", "--module")) shouldBe
                    DependencyLocksCli.CliResult.Failure("Missing value for option --module")
            }

            "Then duplicate module flag is rejected" {
                DependencyLocksCli.run(arrayOf("write-module", "--module", ":core", "--module", ":tools")) shouldBe
                    DependencyLocksCli.CliResult.Failure("Duplicate option --module")
            }

            "Then unknown flags are rejected" {
                DependencyLocksCli.run(arrayOf("write-module", "--module", ":core", "--wat")) shouldBe
                    DependencyLocksCli.CliResult.Failure("Unknown option --wat")
            }
        }
    }

    "Given write-configuration command" - {
        "When required options are provided" - {
            "Then configuration-specific command is returned" {
                DependencyLocksCli.run(
                    arrayOf(
                        "write-configuration",
                        "--module",
                        ":core",
                        "--configuration",
                        "testRuntimeClasspath"
                    )
                ) shouldBe DependencyLocksCli.CliResult.Success(
                    "./gradlew \":core:dependencies\" --configuration \"testRuntimeClasspath\" --write-locks --no-parallel"
                )
            }

            "Then equals-form options are accepted" {
                DependencyLocksCli.run(
                    arrayOf(
                        "write-configuration",
                        "--module=:core",
                        "--configuration=testRuntimeClasspath"
                    )
                ) shouldBe DependencyLocksCli.CliResult.Success(
                    "./gradlew \":core:dependencies\" --configuration \"testRuntimeClasspath\" --write-locks --no-parallel"
                )
            }
        }

        "When required options are missing" - {
            "Then clear validation errors are reported" - {
                withData(
                    nameFn = { it.first },
                    listOf(
                        "missing-module" to arrayOf("write-configuration", "--configuration", "testRuntimeClasspath"),
                        "missing-configuration" to arrayOf("write-configuration", "--module", ":core")
                    )
                ) { (_, args) ->
                    DependencyLocksCli.run(args) shouldBe
                        DependencyLocksCli.CliResult.Failure(
                            "write-configuration requires --module and --configuration"
                        )
                }
            }
        }
    }

    "Given diff command" - {
        "When running the CLI parser" - {
            "Then git diff for lockfiles is returned" {
                DependencyLocksCli.run(arrayOf("diff")) shouldBe
                    DependencyLocksCli.CliResult.Success(
                        "git diff -- **/gradle.lockfile settings-gradle.lockfile"
                    )
            }
        }
    }

    "Given help requests" - {
        "Then help result is returned for explicit help commands" {
            DependencyLocksCli.run(arrayOf("help")) shouldBe DependencyLocksCli.CliResult.Help
            DependencyLocksCli.run(arrayOf("--help")) shouldBe DependencyLocksCli.CliResult.Help
            DependencyLocksCli.run(arrayOf("--json", "help")) shouldBe DependencyLocksCli.CliResult.Help
        }

        "Then empty args return usage failure" {
            DependencyLocksCli.run(emptyArray()) shouldBe
                DependencyLocksCli.CliResult.Failure(
                    "Missing command. Supported commands: write-all, write-module, write-configuration, diff"
                )
        }
    }

    "Given unknown command" - {
        "Then usage error is reported" {
            DependencyLocksCli.run(arrayOf("wat")) shouldBe
                DependencyLocksCli.CliResult.Failure(
                    "Unknown command 'wat'. Supported commands: write-all, write-module, write-configuration, diff"
                )
        }
    }

    "Given duplicate --json option" - {
        "Then it returns a parse failure" {
            DependencyLocksCli.run(arrayOf("--json", "--json", "write-all")) shouldBe
                DependencyLocksCli.CliResult.Failure("Duplicate option --json")
        }
    }
})
