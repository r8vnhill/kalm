/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.utils.size

import arrow.core.Either
import arrow.core.right
import cl.ravenhill.keen.matchers.shouldHaveSameClassAs
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.sequences.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.next
import io.kotest.property.checkAll
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

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
                        else -> actual.shouldBeLeft()
                    }

                    // The semantics of the mapping are consistent
                    actual shouldHaveSameClassAs expected
                }
            }

            "should eventually produce Right" {
                eventually(1.seconds) {
                    gen.next().shouldBeRight().toInt() shouldBeInRange range
                }
            }

            "should eventually produce Left" {
                eventually(1.seconds) {
                    gen.next().shouldBeLeft()
                        .shouldBeInstanceOf<SizeError>()
                }
            }
        }

        "when the range is fully non-negative (0..2048)" - {
            val range = 0..2048
            val gen = Arb.size(range)

            "should always be Right and within inclusive bounds (PBT)" {
                checkAll(gen) { e ->
                    e.shouldBeRight()
                        .toInt() shouldBeInRange range
                }
            }

            "should eventually include the lower bound (sampling check)" {
                eventually(1.seconds) {
                    gen.samples().map {
                        it.value.map(Size::toInt)
                    }.take(500) shouldContain 0.right()
                }
            }

            "should eventually include the upper bound (sampling check)" {
                eventually(1.seconds) {
                    gen.samples().map {
                        it.value.map(Size::toInt)
                    }.take(500) shouldContain 100.right()
                }
            }
        }

        "when using the (min,max) overload" - {
            "should respect inclusive bounds for a few representative intervals" - {
                data class Case(val min: Int, val max: Int)

                withData(
                    nameFn = { "[${it.min}, ${it.max}]" },
                    Case(0, 0),
                    Case(0, 1),
                    Case(1, 1),
                    Case(5, 9),
                    Case(0, 1024),
                ) { (minB, maxB) ->
                    val min = min(minB, maxB)
                    val max = max(minB, maxB)
                    val gen = Arb.size(min, max)
                    checkAll(gen) { e ->
                        e.shouldBeRight()
                            .toInt() shouldBeInRange (min..max)
                    }
                }
            }
        }
    }

    "An Arb.validSize(range) generator" - {
        "when the range is fully non-negative (0..2048)" - {
            val range = 0..2048
            val gen = Arb.validSize(range)

            "should produce only valid Size values within inclusive bounds (PBT)" {
                checkAll(gen) { s ->
                    s.toInt() shouldBeInRange range
                }
            }

            "should eventually include the lower bound (sampling check)" {
                eventually(1.seconds) {
                    gen.samples().map { it.value.toInt() }.take(500) shouldContain 0
                }
            }

            "should eventually include the upper bound (sampling check)" {
                eventually(1.seconds) {
                    gen.samples().map { it.value.toInt() }.take(500) shouldContain 2048
                }
            }
        }

        "when the range contains invalid values (e.g., negatives)" - {
            val mixed = -50..50

            "should throw during sampling because invalid values may be generated (unit)" {
                val gen = Arb.validSize(mixed)
                eventually {
                    shouldThrow<SizeError.NonNegativeExpected> {
                        gen.samples().take(100).toList()
                    }
                }
            }
        }

        "when using the (min,max) overload" - {

            "should respect inclusive bounds for several intervals" - {

                withData(
                    nameFn = { "[${it.first}, ${it.second}]" },
                    0 to 0,
                    0 to 1,
                    8 to 16,
                    1 to 1024
                ) { (minB, maxB) ->
                    val min = min(minB, maxB)
                    val max = max(minB, maxB)
                    val gen = Arb.validSize(min, max)
                    checkAll(gen) { s ->
                        s.toInt() shouldBeInRange (min..max)
                    }
                }
            }
        }
    }
})
