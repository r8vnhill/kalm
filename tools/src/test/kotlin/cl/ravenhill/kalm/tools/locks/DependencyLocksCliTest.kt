/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.tools.locks

import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe

class DependencyLocksCliTest : FreeSpec({

    "Given write-all command" - {
        "When building a shell command" - {
            "Then the default write-locks workflow is returned" {
                DependencyLocksCli.buildShellCommand(arrayOf("write-all")) shouldBe
                    "./gradlew preflight --write-locks --no-parallel"
            }
        }
    }

    "Given write-module command" - {
        "When module is provided" - {
            "Then module-specific write-locks command is returned" {
                DependencyLocksCli.buildShellCommand(
                    arrayOf("write-module", "--module", ":core")
                ) shouldBe "./gradlew :core:dependencies --write-locks --no-parallel"
            }
        }

        "When module is missing" - {
            "Then a clear error is reported" {
                shouldThrowWithMessage<IllegalArgumentException>(
                    "Missing required option --module for write-module"
                ) {
                    DependencyLocksCli.buildShellCommand(arrayOf("write-module"))
                }
            }
        }
    }

    "Given write-configuration command" - {
        "When required options are provided" - {
            "Then configuration-specific command is returned" {
                DependencyLocksCli.buildShellCommand(
                    arrayOf(
                        "write-configuration",
                        "--module",
                        ":core",
                        "--configuration",
                        "testRuntimeClasspath"
                    )
                ) shouldBe "./gradlew :core:dependencies --configuration testRuntimeClasspath --write-locks --no-parallel"
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
                    shouldThrowWithMessage<IllegalArgumentException>(
                        "write-configuration requires --module and --configuration"
                    ) {
                        DependencyLocksCli.buildShellCommand(args)
                    }
                }
            }
        }
    }

    "Given diff command" - {
        "When building a shell command" - {
            "Then git diff for lockfiles is returned" {
                DependencyLocksCli.buildShellCommand(arrayOf("diff")) shouldBe
                    "git diff -- **/gradle.lockfile settings-gradle.lockfile"
            }
        }
    }

    "Given unknown command" - {
        "When building a shell command" - {
            "Then usage error is reported" {
                shouldThrowWithMessage<IllegalArgumentException>(
                    "Unknown command 'wat'. Supported commands: write-all, write-module, write-configuration, diff"
                ) {
                    DependencyLocksCli.buildShellCommand(arrayOf("wat"))
                }
            }
        }
    }
})

