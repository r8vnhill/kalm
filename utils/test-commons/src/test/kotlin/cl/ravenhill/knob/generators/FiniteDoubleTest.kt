/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.generators

import cl.ravenhill.knob.matchers.shouldBeFinite
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.doubles.shouldNotBeNaN
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.checkAll

class FiniteDoubleTest : FreeSpec({

    "a finiteDouble generator" - {

        "when used in property checks" - {

            "should only emit finite values" {
                checkAll(iterations = 10_000, Arb.finiteDouble()) { d ->
                    d.shouldBeFinite()
                    d.shouldNotBeNaN()
                }
            }

            "should respect the inclusive range [-1e6, 1e6]" {
                checkAll(iterations = 10_000, Arb.finiteDouble()) { d ->
                    d shouldBeGreaterThanOrEqual -1e6
                    d shouldBeLessThanOrEqual 1e6
                }
            }
        }

        "when sampling deterministically" - {
            "should produce both negative and positive numbers" {
                val rs = RandomSource.seeded(123456789L)
                val values = Arb.finiteDouble()
                    .samples(rs)
                    .take(2_000)
                    .map { it.value }
                    .toList()

                (values.any { it < 0 }).shouldBeTrue()
                (values.any { it > 0 }).shouldBeTrue()
            }
        }
    }
})
