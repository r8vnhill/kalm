package cl.ravenhill.kalm.tools.cli

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class CliktSupportTest : FreeSpec({
    "detectMissingOptionValue" - {
        "detects missing inline and separated values" {
            val cases = listOf(
                listOf("--module=") to "Missing value for option --module",
                listOf("--module", "--configuration", "test") to "Missing value for option --module",
                listOf("--module") to "Missing value for option --module"
            )
            cases.forEach { (args, expected) ->
                detectMissingOptionValue(args, setOf("--module", "--configuration")) shouldBe expected
            }
        }

        "returns null when values are present" {
            detectMissingOptionValue(
                listOf("--module", ":core", "--configuration=testRuntimeClasspath"),
                setOf("--module", "--configuration")
            ) shouldBe null
        }

        "stops parsing options after -- sentinel" {
            detectMissingOptionValue(
                listOf("--", "--module"),
                setOf("--module")
            ) shouldBe null
        }
    }

    "extractNoSuchOptionName" - {
        "extracts option names from clikt-like messages" {
            extractNoSuchOptionName("no such option '--wat'") shouldBe "--wat"
            extractNoSuchOptionName("Unknown option \"--wat\"") shouldBe "--wat"
        }

        "falls back to trimmed message when pattern does not match" {
            extractNoSuchOptionName("  unexpected message  ") shouldBe "unexpected message"
        }
    }
})
