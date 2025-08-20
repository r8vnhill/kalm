/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.generators

import cl.ravenhill.keen.matchers.shouldBeCloseToUlps
import cl.ravenhill.keen.matchers.shouldHaveSize
import cl.ravenhill.keen.utils.size.Size
import cl.ravenhill.keen.utils.size.validSize
import io.kotest.assertions.nondeterministic.continually
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.next
import io.kotest.property.checkAll
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class DoubleArrayExactArbTest : FreeSpec({

    val finiteDoubleArb = Arb.finiteDouble(lo = -1e3, hi = 1e3)

    "a doubleArrayExact arb" - {

        "when size varies in 0..32 and content is in [-1e3, 1e3]" - {
            val sizeArb = Arb.validSize(0..32)

            "should always produce arrays of exact size with all elements in range (PBT)" {
                checkAll(sizeArb) { size ->
                    val generatedArray = Arb.doubleArrayExact(size, finiteDoubleArb).next()
                    generatedArray
                        .shouldHaveSize(size)
                        .forEachIndexed { index, element ->
                            withClue("Element $element at index $index") {
                                element
                                    .shouldBeGreaterThanOrEqual(-1e3)
                                    .shouldBeLessThanOrEqual(1e3)
                            }
                        }
                }
            }

            "should eventually include the upper bound (sampling check)" {
                val hi = 10.0

                `eventually hits bound within ULPs`(
                    hi,
                    arb = { hi -> Arb.finiteDouble(lo = -10.0, hi) },
                    select = DoubleArray::maxOrNull
                )
            }

            "should eventually include the lower bound (sampling check)" {
                val lo = -10.0
                `eventually hits bound within ULPs`(
                    lo,
                    arb = { lo -> Arb.finiteDouble(lo, hi = 10.0) },
                    select = DoubleArray::minOrNull
                )
            }
        }
    }

    "when size is zero" - {
        "should produce empty arrays" {
            val doubleArrayGen = Arb.doubleArrayExact(Size.ofOrThrow(0), Arb.double())
            continually(1.seconds) {
                doubleArrayGen.next()
                    .shouldBeEmpty()
            }
        }
    }
})

private suspend fun `eventually hits bound within ULPs`(
    expected: Double,
    arb: (Double) -> Arb<Double>,
    select: DoubleArray.() -> Double?,
    size: Size = Size.ofOrThrow(500),
    timeout: Duration = 1.seconds,
    maxUlps: Long = 10
) {
    val arrays = Arb.doubleArrayExact(size, arb(expected))
    eventually(timeout) {
        arrays.next()
            .select()
            .shouldNotBeNull()
            // Use ULPs for robust floating-point comparison near numeric boundaries.
            .shouldBeCloseToUlps(expected, maxUlps)
    }
}
