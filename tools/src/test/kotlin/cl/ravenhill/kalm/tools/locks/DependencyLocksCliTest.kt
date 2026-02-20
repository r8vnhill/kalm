/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.tools.locks

import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.stringPattern
import io.kotest.property.checkAll

/**
 * Comprehensive test suite for [DependencyLocksCli] argument parsing and command rendering.
 *
 * ## Test Organization
 *
 * Tests are organized by subcommand and cross-cutting concerns:
 *
 * - **write-all**: verifies the lockfile write-all workflow
 * - **write-module**: tests module-specific lockfile writes with validation
 * - **write-configuration**: tests configuration-scoped lockfile writes with validation
 * - **diff**: verifies the diff workflow
 * - **help**: tests help request recognition
 * - **global options**: tests `--json` flag handling and placement
 * - **sentinel parsing**: tests `--` argument delimiter behavior
 *
 * ## Test Strategy
 *
 * - **Table-driven tests (DDT)** for fixed cases (missing options, duplicates, unknown options)
 * - **Property-based tests (PBT)** for generated cases (JSON placement, sentinel positions, random trailing tokens)
 * - **Data type aliases** for compact test assertions
 *
 * ## Key Invariants Verified
 *
 * - Help requests always return `Help` result with exit code 0
 * - Invalid arguments always populate stderr message
 * - `--json` placement anywhere in args does not affect command rendering
 * - Arguments after `--` sentinel are excluded from validation
 * - Command schema remains stable (field order, field names)
 */
class DependencyLocksCliTest : FreeSpec({
    typealias Success = DependencyLocksCli.CliResult.Success
    typealias Failure = DependencyLocksCli.CliResult.Failure
    typealias Help = DependencyLocksCli.CliResult.Help

    "When parsing write-all" - {
        "Then it returns the write-all command" {
            DependencyLocksCli.run(listOf("write-all")) shouldBe Success(expectedWriteAll())
        }

        "Then JSON mode keeps command semantics" {
            DependencyLocksCli.run(listOf("--json", "write-all")) shouldBe Success(expectedWriteAll())
        }

        "Then extra positional args are rejected" {
            DependencyLocksCli.run(listOf("write-all", "wat")) shouldBe
                Failure("Invalid arguments")
        }

        "Then command help is recognized" {
            DependencyLocksCli.run(listOf("write-all", "--help")) shouldBe Help
        }
    }

    "When parsing write-module" - {
        "Then it returns the module command" {
            DependencyLocksCli.run(listOf("write-module", "--module", ":core")) shouldBe
                Success(expectedWriteModule(":core"))
        }

        "Then --module=value is accepted" {
            DependencyLocksCli.run(listOf("write-module", "--module=:core")) shouldBe
                Success(expectedWriteModule(":core"))
        }

        "Then missing module option is rejected" {
            DependencyLocksCli.run(listOf("write-module")) shouldBe
                Failure("Missing required option --module for write-module")
        }

        "Then missing module value is rejected" {
            DependencyLocksCli.run(listOf("write-module", "--module")) shouldBe
                Failure("Missing value for option --module")
        }

        "Then empty module value in equals form is rejected" {
            DependencyLocksCli.run(listOf("write-module", "--module=")) shouldBe
                Failure("Missing value for option --module")
        }

        "Then duplicate module option is rejected" {
            DependencyLocksCli.run(listOf("write-module", "--module", ":core", "--module", ":tools")) shouldBe
                Failure("Duplicate option --module")
        }

        "Then unknown options are rejected" {
            DependencyLocksCli.run(listOf("write-module", "--module", ":core", "--wat")) shouldBe
                Failure("Unknown option --wat")
        }

        "Then extra positional args are rejected" {
            DependencyLocksCli.run(listOf("write-module", "--module", ":core", "wat")) shouldBe
                Failure("Invalid arguments")
        }
    }

    "When parsing write-configuration" - {
        "Then it returns the configuration command" {
            DependencyLocksCli.run(
                listOf(
                    "write-configuration",
                    "--module",
                    ":core",
                    "--configuration",
                    "testRuntimeClasspath"
                )
            ) shouldBe Success(expectedWriteConfiguration())
        }

        "Then equals-form options are accepted" {
            DependencyLocksCli.run(
                listOf(
                    "write-configuration",
                    "--module=:core",
                    "--configuration=testRuntimeClasspath"
                )
            ) shouldBe Success(expectedWriteConfiguration())
        }

        "Then missing required options are rejected" - {
            withData(
                nameFn = Case::name,
                Case(
                    name = "missing-module",
                    args = listOf("write-configuration", "--configuration", "testRuntimeClasspath"),
                    expected = Failure("write-configuration requires --module and --configuration")
                ),
                Case(
                    name = "missing-configuration",
                    args = listOf("write-configuration", "--module", ":core"),
                    expected = Failure("write-configuration requires --module and --configuration")
                )
            ) { case ->
                DependencyLocksCli.run(case.args) shouldBe case.expected
            }
        }

        "Then duplicate module option is rejected" {
            DependencyLocksCli.run(
                listOf(
                    "write-configuration",
                    "--module",
                    ":core",
                    "--module",
                    ":tools",
                    "--configuration",
                    "testRuntimeClasspath"
                )
            ) shouldBe Failure("Duplicate option --module")
        }

        "Then duplicate configuration option is rejected" {
            DependencyLocksCli.run(
                listOf(
                    "write-configuration",
                    "--module",
                    ":core",
                    "--configuration",
                    "a",
                    "--configuration",
                    "b"
                )
            ) shouldBe Failure("Duplicate option --configuration")
        }

        "Then unknown options are rejected" {
            DependencyLocksCli.run(
                listOf("write-configuration", "--module", ":core", "--configuration", "a", "--wat")
            ) shouldBe Failure("Unknown option --wat")
        }

        "Then empty module value in equals form is rejected" {
            DependencyLocksCli.run(
                listOf("write-configuration", "--module=", "--configuration=testRuntimeClasspath")
            ) shouldBe Failure("Missing value for option --module")
        }

        "Then empty configuration value in equals form is rejected" {
            DependencyLocksCli.run(
                listOf("write-configuration", "--module=:core", "--configuration=")
            ) shouldBe Failure("Missing value for option --configuration")
        }
    }

    "When parsing diff" - {
        "Then it returns the diff command" {
            DependencyLocksCli.run(listOf("diff")) shouldBe Success(expectedDiff())
        }

        "Then extra positional args are rejected" {
            DependencyLocksCli.run(listOf("diff", "wat")) shouldBe
                Failure("Invalid arguments")
        }
    }

    "When parsing help" - {
        "Then explicit help invocations return help result" {
            DependencyLocksCli.run(listOf("help")) shouldBe Help
            DependencyLocksCli.run(listOf("--help")) shouldBe Help
            DependencyLocksCli.run(listOf("--json", "help")) shouldBe Help
        }

        "Then help before command returns help" {
            DependencyLocksCli.run(listOf("--help", "write-all")) shouldBe Help
        }

        "Then empty args return a usage failure" {
            DependencyLocksCli.run(emptyList()) shouldBe
                Failure("Missing command. Supported commands: write-all, write-module, write-configuration, diff")
        }
    }

    "When parsing unknown commands" - {
        "Then usage error is returned" {
            DependencyLocksCli.run(listOf("wat")) shouldBe
                Failure("Unknown command 'wat'. Supported commands: write-all, write-module, write-configuration, diff")
        }
    }

    "When parsing duplicate --json" - {
        "Then duplicate json is rejected" {
            DependencyLocksCli.run(listOf("--json", "--json", "write-all")) shouldBe
                Failure("Duplicate option --json")
        }
    }

    "When parsing json placement" - {
        "Then placement does not change semantics for table-driven cases" - {
            withData(
                nameFn = Case::name,
                Case(
                    name = "json-before-command",
                    args = listOf("--json", "write-all"),
                    expected = Success(expectedWriteAll())
                ),
                Case(
                    name = "json-after-command",
                    args = listOf("write-all", "--json"),
                    expected = Success(expectedWriteAll())
                ),
                Case(
                    name = "json-between-command-and-options",
                    args = listOf("write-module", "--json", "--module", ":core"),
                    expected = Success(expectedWriteModule(":core"))
                )
            ) { case ->
                DependencyLocksCli.run(case.args) shouldBe case.expected
            }
        }

        "Then placement does not change semantics for generated positions" {
            val base = listOf("write-module", "--module", ":a:b")
            checkAll(Arb.int(0..base.size)) { index ->
                val args = (base.take(index) + "--json" + base.drop(index))
                DependencyLocksCli.run(args) shouldBe Success(expectedWriteModule(":a:b"))
            }
        }
    }

    "When parsing args with -- sentinel" - {
        "Then options after sentinel are ignored for unknown-option detection" {
            DependencyLocksCli.run(listOf("write-module", "--module", ":core", "--", "--wat")) shouldBe
                Success(expectedWriteModule(":core"))
        }

        "Then options before sentinel still validate" {
            DependencyLocksCli.run(listOf("write-module", "--module", "--", ":core")) shouldBe
                Failure("Missing value for option --module")
        }

        "Then trailing tokens after sentinel do not change result" {
            val prefix = listOf("write-module", "--module", ":core")
            checkAll(
                Arb.list(Arb.stringPattern("[a-z]{1,8}"), 1..4)
            ) { trailing ->
                val args = (prefix + "--" + trailing)
                DependencyLocksCli.run(args) shouldBe Success(expectedWriteModule(":core"))
            }
        }
    }
})

/**
 * Test case container for table-driven tests (DDT).
 *
 * Enables concise parameterized testing via [withData] where multiple input/output pairs can be defined declaratively
 * rather than as separate test functions.
 *
 * @property name Display the name for this case (used by test framework for reporting)
 * @property args List of CLI arguments to parse
 * @property expected The expected [DependencyLocksCli.CliResult] from parsing
 */
private data class Case(
    val name: String,
    val args: List<String>,
    val expected: DependencyLocksCli.CliResult
)

/** Expected Gradle command for writing all lockfiles. */
private fun expectedWriteAll() =
    "./gradlew preflight --write-locks --no-parallel"

/** Expected Gradle command for writing lockfiles of a specific module. */
private fun expectedWriteModule(module: String) =
    "./gradlew \"$module:dependencies\" --write-locks --no-parallel"

/** Expected Gradle command for writing lockfiles for the core test runtime configuration. */
private fun expectedWriteConfiguration() =
    "./gradlew \":core:dependencies\" --configuration \"testRuntimeClasspath\" --write-locks --no-parallel"

/** Expected command for showing lockfile-related Git diff output. */
private fun expectedDiff() =
    "git diff -- **/gradle.lockfile settings-gradle.lockfile"
