/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.jvm

import cl.ravenhill.knob.generators.finiteDouble
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.doubles.shouldBeNaN
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.checkAll

// Regression tests, ensuring changes to MathCompat.fma do not break expected behavior.
class MathCompatTest : FreeSpec({

    "fma delegates to java.lang.Math.fma (PBT over finite doubles)" {
        val finite = Arb.Companion.finiteDouble()
        checkAll(finite, finite, finite) { a: Double, b: Double, c: Double ->
            val expected = Math.fma(a, b, c)
            val actual = MathCompat.fma(a, b, c)
            withClue("a=$a, b=$b, c=$c") { actual shouldBe expected }
        }
    }

    "matches Math.fma on notable non-NaN edge cases" - {
        withData(
            nameFn = { "${it.a} * ${it.b} + ${it.c} = ${it.expected}" },
            listOf(
                nonNaN(0.0, 0.0, 0.0),
                nonNaN(-0.0, 0.0, 1.0),
                nonNaN(Double.MAX_VALUE, Double.MIN_VALUE, 1.0),
                nonNaN(1e308, 1e-308, 1.0),
                nonNaN(-1e308, 1e-308, -1.0),
            )
        ) { (a, b, c, expected) ->
            val actual = MathCompat.fma(a, b, c)
            actual shouldBe expected
        }
    }

    "produces NaN in IEEE-754 scenarios" - {
        withData(
            nameFn = { "${it.a} * ${it.b} + ${it.c} -> NaN" },
            listOf(
                NaNCase(Double.NaN, 2.0, 3.0),
                NaNCase(2.0, Double.NaN, 3.0),
                NaNCase(2.0, 3.0, Double.NaN),
                NaNCase(Double.POSITIVE_INFINITY, 0.0, 1.0),
                NaNCase(Double.NEGATIVE_INFINITY, 0.0, -1.0),
                NaNCase(0.0, Double.POSITIVE_INFINITY, 1.0),
                NaNCase(0.0, Double.NEGATIVE_INFINITY, -1.0),
                NaNCase(Double.POSITIVE_INFINITY, 2.0, Double.NEGATIVE_INFINITY),
                NaNCase(Double.NEGATIVE_INFINITY, 2.0, Double.POSITIVE_INFINITY)
            )
        ) { (a, b, c) ->
            MathCompat.fma(a, b, c).shouldBeNaN()
            // optional cross-check
            Math.fma(a, b, c).shouldBeNaN()
        }
    }
}) {
    private data class NonNaNCase(val a: Double, val b: Double, val c: Double, val expected: Double)
    private data class NaNCase(val a: Double, val b: Double, val c: Double)

    private companion object {
        /** Helper to build a NonNaNCase with expected computed via Math.fma at definition site. */
        private fun nonNaN(a: Double, b: Double, c: Double) =
            NonNaNCase(a, b, c, Math.fma(a, b, c))
    }
}
