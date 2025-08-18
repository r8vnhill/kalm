/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.generators

import io.kotest.core.spec.style.FreeSpec

class DoubleArrayExactArbTest : FreeSpec({

    "a doubleArrayExact arb" - {
        "when size varies in 0..32 and content is in [-1e3, 1e3]" - {
            TODO("Depends on size generators")
//            "should always produce arrays of exact size with all elements in range (PBT)" {
//                val content = Arb.double(-1_000.0..1_000.0)
//                checkAll(iterations = 500, Arb.int(0..32)) { n ->
//                    val size = Size.ofOrThrow(n)
//                    val arr = Arb.doubleArrayExact(size, content).singleSample()
//                    arr.size shouldBe n
//                    arr.forEach {
//                        it.shouldBeGreaterThanOrEqual(-1_000.0)
//                        it.shouldBeLessThanOrEqual(1_000.0)
//                    }
//                }
//            }
        }

        "when size is zero" - {
            TODO()
//            "should produce empty arrays (DDT)" {
//                val content = Arb.double()
//                val arr = Arb.doubleArrayExact(Size.ofOrThrow(0), content).singleSample()
//                arr.size shouldBe 0
//            }
        }

        "when content is a constant" - {
            TODO()
//            "should fill the array with that constant for any size 0..64 (PBT)" {
//                val constants = Arb.element(listOf(0.0, -1.5, PI))
//                checkAll(iterations = 200, Arb.int(0..64), constants) { n, c ->
//                    val arr = Arb.doubleArrayExact(Size.ofOrThrow(n), Arb.constant(c)).singleSample()
//                    arr.size shouldBe n
//                    if (n > 0) {
//                        // fast path: all elements are exactly 'c'
//                        arr.toSet() shouldBe setOf(c)
//                    }
//                }
//            }
        }

        "when content may include special IEEE-754 values" - {
            TODO()
//            "should allow NaN and infinities to appear and still respect size (DDT+PBT)" {
//                val specials = listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0)
//                val content = Arb.element(specials)
//
//                // small sample where equality checks are meaningful for non-NaN values
//                val arr = Arb.doubleArrayExact(Size.ofOrThrow(8), content)
//                    .generate(RandomSource.default()).first().value
//
//                arr.size shouldBe 8
//                // every element is one of the specials (treat NaN separately)
//                arr.forEach { v ->
//                    if (v.isNaN()) v.shouldBeNaN()
//                    else specials.filterNot { it.isNaN() }.shouldContainOnly(
//                        Double.NEGATIVE_INFINITY, 0.0, Double.POSITIVE_INFINITY
//                    ).let { /* membership already implied by generator */ }
//                }
//            }
        }
    }
})
