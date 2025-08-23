/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.matchers

import cl.ravenhill.knob.generators.finiteDouble
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.checkAll

class BeFiniteMatcherTest : FreeSpec({

    // Shared fixtures
    val nonFinite = listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)
    val finiteDoubles = Arb.finiteDouble()

    "a finiteness matcher (beFinite)" - {

        "when value is finite" - {
            "should pass for random finite doubles" {
                checkAll(iterations = 300, finiteDoubles) { x ->
                    beFinite().test(x).passed().shouldBeTrue()
                }
            }
        }

        "when value is not finite" - {

            "should fail for NaN and infinities" - {
                withData(nonFinite) { x ->
                    val result = beFinite().test(x)
                    result.passed().shouldBeFalse()
                }
            }

            "should produce a helpful failure message" - {
                withData(nonFinite) { x ->
                    val result = beFinite().test(x)
                    // If your production matcher uses `render()`/`renderKind()`, assert key parts:
                    result.failureMessage() shouldContain "Expected ${x.render()} to be finite"
                }
            }

            "should produce a helpful negated failure message for finite inputs" {
                val neg = beFinite().test(42.0).negatedFailureMessage()
                // Less brittle than exact match; checks intent and key term
                neg shouldContain "not to be finite"
            }
        }
    }

    "a Double extension (shouldBeFinite)" - {

        "when value is finite" - {
            "should not throw" {
                shouldNotThrow<AssertionError> { 0.0.shouldBeFinite() }
                shouldNotThrow<AssertionError> { 1234.567.shouldBeFinite() }
                // randomized
                checkAll(iterations = 300, finiteDoubles) { x ->
                    shouldNotThrow<AssertionError> { x.shouldBeFinite() }
                }
            }
        }

        "when value is not finite" - {
            "should throw for NaN and infinities" - {
                withData(nonFinite) { x ->
                    shouldThrow<AssertionError> { x.shouldBeFinite() }
                }
            }
        }
    }

    "a Double extension (shouldNotBeFinite)" - {

        "when value is not finite" - {
            "should not throw for NaN and infinities" - {
                withData(nonFinite) { x ->
                    shouldNotThrow<AssertionError> { x.shouldNotBeFinite() }
                }
            }
        }

        "when value is finite" - {
            "should throw" {
                shouldThrow<AssertionError> { 0.0.shouldNotBeFinite() }
                shouldThrow<AssertionError> { (-1.23).shouldNotBeFinite() }
                // randomized
                checkAll(iterations = 300, finiteDoubles) { x ->
                    shouldThrow<AssertionError> { x.shouldNotBeFinite() }
                }
            }
        }
    }
})