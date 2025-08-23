/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.utils

import arrow.core.Either
import cl.ravenhill.knob.exceptions.InvalidThresholdException
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.Gen
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.filter
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection

class EqualityThresholdTest : FreeSpec({

    "An 'Equality Threshold' when" - {
        val threshold: (Double) -> Either<InvalidThresholdException, EqualityThreshold> = {
            EqualityThreshold(it)
        }

        "created with" - {
            "a positive value should" - {
                "return a Right with the threshold" {
                    checkAll(Arb.double(min = 0.0, includeNonFiniteEdgeCases = false)) { x ->
                        threshold(x)
                            .shouldBeRight()
                            .value shouldBe x
                    }
                }
            }

            listOf(
                InvalidCreationTest("NaN", Arb.constant(Double.NaN)) { "Threshold should not be NaN, but was NaN" },
                InvalidCreationTest(
                    "an infinite",
                    Exhaustive.collection(listOf(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY))
                ) { "Threshold should be finite, but was $it" },
                InvalidCreationTest(
                    "a negative value",
                    Arb.double(includeNonFiniteEdgeCases = false).filter { it < 0.0 }
                ) { "Threshold should be non-negative, but was $it" }
            ).forEach { (testName, gen, expectedMessage) ->
                "$testName value should" - {
                    "return a Left with an InvalidThresholdException" {
                        checkAll(gen) { x ->
                            threshold(x)
                                .shouldBeLeft()
                                .shouldHaveMessage(expectedMessage(x))
                        }
                    }
                }
            }
        }

        "predefined instances should" - {
            "be valid" {
                mapOf(
                    EqualityThreshold.strict to EqualityThreshold.STRICT,
                    EqualityThreshold.relaxed to EqualityThreshold.RELAXED,
                    EqualityThreshold.exact to EqualityThreshold.EXACT,
                    EqualityThreshold.default to EqualityThreshold.DEFAULT
                ).forEach { (instance, expectedValue) ->
                    instance
                        .shouldBeRight()
                        .value shouldBe expectedValue
                }
            }
        }
    }
})

private data class InvalidCreationTest(
    val testName: String,
    val gen: Gen<Double>,
    val expectedMessage: (Double) -> String
)
