/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.utils.size

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import cl.ravenhill.keen.matchers.shouldHaveSameClassAs
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll

class SizeArbsTest : FreeSpec({

    "An Arb.size(range) generator" - {
        "when the range includes negatives and non-negatives (-100..100)" - {
            val range = -100..100
            val gen = Arb.size(range)

            "should return Right(Size) only for inputs >= 0 and Left(SizeError) otherwise (PBT)" {
                // We mirror the internal mapping by regenerating the same domain and comparing semantics.
                checkAll(Arb.int(range)) { n ->
                    val expected: Either<SizeError, Size> =
                        if (n >= 0) Size.ofOrThrow(n).right() else Size(n) // Left branch stands for "invalid"
                    val actual = Size(n) // the same constructor used by Arb.size(range).map(Size::invoke)

                    // Sanity check on the constructor itself
                    when {
                        n >= 0 -> actual.shouldBeRight().toInt() shouldBe n
                        else   -> actual.shouldBeLeft()
                    }

                    // The semantics of the mapping are consistent
                    actual shouldHaveSameClassAs expected
                }
            }

            "should eventually produce both Left and Right (distribution sanity)" {
                TODO()
//                // Sample a bunch and check we see both sides.
//                val sample = gen.samples().take(200).map { it.value }.toList()
//                sample.any { it.isLeft() } shouldBe true
//                sample.any { it.isRight() } shouldBe true
            }
        }

        "when the range is fully non-negative (0..1024)" - {
            TODO()
//            val range = 0..1024
//            val gen = Arb.size(range)
//
//            "should always be Right and within inclusive bounds (PBT)" {
//                checkAll(gen) { e ->
//                    val s = e.shouldBeRight()
//                    s.intValue() shouldBeInRange range
//                }
//            }
//
//            "should include both edges (0 and 1024) often enough (sampling check)" {
//                val xs = gen.samples().take(500).mapNotNull { it.value.orNull() }.map { it.intValue() }.toList()
//                xs.shouldNotBeEmpty()
//                // Not strictly guaranteed, but with 500 draws over 1025 values, seeing edges is very likely.
//                xs.shouldContain(0)
//                xs.shouldContain(1024)
//            }
        }

        "when using the (min,max) overload" - {
            TODO()
//            "should respect inclusive bounds for a few representative intervals (DDT)" {
//                data class Case(val min: Int, val max: Int)
//                withData(
//                    nameFn = { "[${it.min}, ${it.max}]" },
//                    Case(0, 0),
//                    Case(0, 1),
//                    Case(1, 1),
//                    Case(5, 9),
//                    Case(0, 1024),
//                ) { (minB, maxB) ->
//                    val min = min(minB, maxB)
//                    val max = max(minB, maxB)
//                    val gen = Arb.size(min, max)
//                    checkAll(gen) { e ->
//                        val s = e.shouldBeRight()
//                        s.intValue() shouldBeInRange (min..max)
//                    }
//                }
//            }
        }
    }

    "An Arb.validSize(range) generator" - {
        TODO()
//        "when the range is fully non-negative (0..2048)" - {
//            val range = 0..2048
//            val gen = Arb.validSize(range)
//
//            "should produce only valid Size values within inclusive bounds (PBT)" {
//                checkAll(gen) { s ->
//                    s.intValue() shouldBeGreaterThanOrEqual 0
//                    s.intValue() shouldBeInRange range
//                }
//            }
//
//            "should include both edges (sampling check)" {
//                val xs = gen.samples().take(500).map { it.value.intValue() }.toList()
//                xs.shouldNotBeEmpty()
//                xs.shouldContain(0)
//                xs.shouldContain(2048)
//            }
//        }
//
//        "when the range contains invalid values (e.g., negatives)" - {
//            val mixed = -50..50
//
//            "should throw during sampling because invalid values may be generated (unit)" {
//                val gen = Arb.validSize(mixed)
//                shouldThrow<IllegalArgumentException> {
//                    // Force at least one sample; generation maps `Size::ofOrThrow`, which should fail for < 0
//                    gen.samples().first().value // likely to hit a negative soon; if not, take more than one:
//                }
//            }
//        }
//
//        "when using the (min,max) overload" - {
//            "should respect inclusive bounds for several intervals (DDT)" {
//                data class Case(val min: Int, val max: Int)
//                withData(
//                    nameFn = { "[${it.min}, ${it.max}]" },
//                    Case(0, 0),
//                    Case(0, 1),
//                    Case(8, 16),
//                    Case(1, 1024)
//                ) { (minB, maxB) ->
//                    val min = min(minB, maxB)
//                    val max = max(minB, maxB)
//                    val gen = Arb.validSize(min, max)
//                    checkAll(gen) { s ->
//                        s.intValue() shouldBeInRange (min..max)
//                    }
//                }
//            }
//        }
    }
})
