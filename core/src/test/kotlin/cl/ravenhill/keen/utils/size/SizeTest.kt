/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.utils.size

import cl.ravenhill.keen.utils.descendingPair
import cl.ravenhill.keen.utils.orderedPair
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.negativeInt
import io.kotest.property.arbitrary.nonNegativeInt
import io.kotest.property.arbitrary.nonPositiveInt
import io.kotest.property.arbitrary.positiveInt
import io.kotest.property.checkAll

class SizeTest : FreeSpec({

    "Size" - {

        "Given a non-negative Int" - {
            "when fromNonNegative is called, then it returns Right(Size(n))" {
                checkAll(Arb.nonNegativeInt()) { n ->
                    Size.fromNonNegative(n)
                        .shouldBeRight()
                        .toInt() shouldBe n
                }
            }

            "when ofOrNull is called, then it returns Size(n)" {
                checkAll(Arb.nonNegativeInt()) { n ->
                    Size.ofOrNull(n)
                        .shouldNotBeNull()
                        .toInt() shouldBe n
                }
            }

            "when ofOrThrow is called, then it returns Size(n)" {
                checkAll(Arb.nonNegativeInt()) { n ->
                    shouldNotThrowAny {
                        Size.ofOrThrow(n)
                    }.toInt() shouldBe n
                }
            }
        }

        "Given a negative Int" - {
            "when fromNonNegative is called, then it returns Left(NonNegativeExpected(n))" {
                checkAll(Arb.negativeInt()) { n ->
                    Size.fromNonNegative(n)
                        .shouldBeLeft()
                        .shouldBeInstanceOf<SizeError.NonNegativeExpected>()
                        .shouldHaveMessage("Expected a non-negative size, but got $n")
                }
            }

            "when ofOrNull is called, then it returns null" {
                checkAll(Arb.negativeInt()) { n ->
                    Size.ofOrNull(n)
                        .shouldBeNull()
                }
            }

            "when ofOrThrow is called, then it throws SizeError.NonNegativeExpected" {
                checkAll(Arb.negativeInt()) { n ->
                    shouldThrowWithMessage<SizeError.NonNegativeExpected>(
                        message = "Expected a non-negative size, but got $n"
                    ) {
                        Size.ofOrThrow(n)
                    }
                }
            }
        }

        "Given a positive Int, when strictlyPositive is called, then it returns Right(Size(n))" {
            checkAll(Arb.positiveInt()) { n ->
                Size.strictlyPositive(n)
                    .shouldBeRight()
                    .toInt() shouldBe n
            }
        }

        "Given a non-positive Int, when strictlyPositive is called, then it returns Left(StrictlyPositiveExpected(n))" {
            checkAll(Arb.nonPositiveInt()) { n ->
                Size.strictlyPositive(n)
                    .shouldBeLeft()
                    .shouldBeInstanceOf<SizeError.StrictlyPositiveExpected>()
                    .shouldHaveMessage("Expected a strictly positive size, but got $n")
            }
        }

        "invoke delegates to fromNonNegative" {
            checkAll(Arb.int()) { n ->
                Size(n) shouldBe Size.fromNonNegative(n)
            }
        }

        "zero is a constant 0 and is additive identity" {
            Size.zero.toInt() shouldBe 0
            checkAll(Arb.int(min = 0, max = Int.MAX_VALUE / 2)) { n ->
                val s = Size.ofOrThrow(n)
                (Size.zero + s).toInt() shouldBe n
                (s + Size.zero).toInt() shouldBe n
            }
        }

        "plus produces a Size with value equal to the sum (no overflow region)" {
            checkAll(
                Arb.nonNegativeInt(Int.MAX_VALUE / 2),
                Arb.nonNegativeInt(Int.MAX_VALUE / 2)
            ) { a, b ->
                val sa = Size.ofOrThrow(a)
                val sb = Size.ofOrThrow(b)
                (sa + sb).toInt() shouldBe (a + b)
            }
        }

        "compareTo orders by underlying value" - {

            "when both are equal, then it returns 0" {
                checkAll(Arb.nonNegativeInt()) { n ->
                    val a = Size.ofOrThrow(n)
                    val b = Size.ofOrThrow(n)
                    a.compareTo(b) shouldBe 0
                }
            }

            "when first is less, then it returns negative" {
                checkAll(
                    Arb.orderedPair(Arb.nonNegativeInt(), Arb.nonNegativeInt(), strict = true)
                ) { (a, b) ->
                    // generator guarantees a < b
                    Size.ofOrThrow(a) shouldBeLessThan Size.ofOrThrow(b)
                }
            }

            "when first is greater, then it returns positive" {
                checkAll(
                    Arb.descendingPair(Arb.nonNegativeInt(), Arb.nonNegativeInt(), strict = true)
                ) { (a, b) ->
                    // generator guarantees a > b
                    Size.ofOrThrow(a) shouldBeGreaterThan Size.ofOrThrow(b)
                }
            }

            "antisymmetry: sign(a.compareTo(b)) == -sign(b.compareTo(a)) for a ≠ b" {
                checkAll(
                    Arb.orderedPair(Arb.nonNegativeInt(), Arb.nonNegativeInt(), strict = true)
                ) { (a, b) ->
                    val sa = Size.ofOrThrow(a)
                    val sb = Size.ofOrThrow(b)
                    val ab = sa.compareTo(sb)
                    val ba = sb.compareTo(sa)
                    (ab > 0 && ba < 0) || (ab < 0 && ba > 0) shouldBe true
                }
            }

            "transitivity: if a < b and b < c then a < c" {
                // build a < b < c by sampling three and sorting
                checkAll(Arb.nonNegativeInt(), Arb.nonNegativeInt(), Arb.nonNegativeInt()) { x, y, z ->
                    val sorted = listOf(x, y, z).sorted()
                    if (sorted[0] < sorted[1] && sorted[1] < sorted[2]) {
                        val a = Size.ofOrThrow(sorted[0])
                        val b = Size.ofOrThrow(sorted[1])
                        val c = Size.ofOrThrow(sorted[2])
                        (a < b && b < c && a < c) shouldBe true
                    }
                }
            }
        }

        "toInt exposes the underlying value" {
            checkAll(Arb.nonNegativeInt()) { n ->
                Size.ofOrThrow(n).toInt() shouldBe n
            }
        }
    }
})
