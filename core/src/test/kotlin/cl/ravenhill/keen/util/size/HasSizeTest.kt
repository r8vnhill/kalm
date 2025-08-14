/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.util.size

import cl.ravenhill.keen.util.orderedPair
import cl.ravenhill.keen.util.size.gen.hasSize
import cl.ravenhill.keen.util.size.gen.validSize
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.pair
import io.kotest.property.checkAll

class HasSizeSpec : FreeSpec({

    "Given two HasSize instances" - {

        "when checking if they have the same size" - {

            "then it returns true if sizes are equal" {
                checkAll(Arb.sameSizePair(Arb.validSize())) { (a, b) ->
                    withClue("expected equal sizes, a=${a.size.toInt()}, b=${b.size.toInt()}") {
                        (a sameSizeAs b).shouldBeTrue()
                    }
                }
            }

            "then it returns false if sizes differ" {
                checkAll(Arb.diffSizePair(Arb.validSize())) { (a, b) ->
                    withClue("expected different sizes, a=${a.size.toInt()}, b=${b.size.toInt()}") {
                        (a sameSizeAs b).shouldBeFalse()
                    }
                }
            }
        }

        "sameSizeAs is reflexive" {
            checkAll(Arb.hasSize()) { a ->
                (a sameSizeAs a).shouldBeTrue()
            }
        }

        "sameSizeAs is symmetric" {
            checkAll(Arb.hasSize(), Arb.hasSize()) { a, b ->
                (a sameSizeAs b) shouldBe (b sameSizeAs a)
            }
        }
    }
}) {
    companion object {
        private fun Arb.Companion.sameSizePair(sizeGen: Arb<Size>): Arb<Pair<HasSize, HasSize>> =
            sizeGen.flatMap { s ->
                Arb.pair(
                    Arb.hasSize(Arb.constant(s)),
                    Arb.hasSize(Arb.constant(s))
                )
            }

        private fun Arb.Companion.diffSizePair(sizeGen: Arb<Size>): Arb<Pair<HasSize, HasSize>> =
            Arb.orderedPair(sizeGen, strict = true).flatMap { (a, b) ->
                Arb.bind(
                    Arb.boolean(),
                    Arb.hasSize(Arb.constant(a)),
                    Arb.hasSize(Arb.constant(b))
                ) { swap, ha, hb ->
                    if (swap) ha to hb else hb to ha
                }
            }
    }
}
//
//        "returns false when sizes differ" {
//            checkAll(
//                Arb.int(min = 0, max = 1024),
//                Arb.int(min = 0, max = 1024)
//            ) { x, y ->
//                if (x != y) {
//                    val a = Box(Size.ofOrThrow(x))
//                    val b = Box(Size.ofOrThrow(y))
//                    (a sameSizeAs b).shouldBeFalse()
//                }
//            }
//        }
//    }
//
//    "requireSameSize" - {
//
//        "returns Right(self) when sizes are equal" {
//            checkAll(Arb.int(min = 0, max = 1024)) { n ->
//                val s = Size.ofOrThrow(n)
//                val a = Box(s)
//                val b = Box(s)
//                a.requireSameSize(b) shouldBe Either.Right(a)
//            }
//        }
//
//        "returns Left(MatchingSizesExpected) when sizes differ" {
//            checkAll(
//                Arb.int(min = 0, max = 1024),
//                Arb.int(min = 0, max = 1024)
//            ) { x, y ->
//                if (x != y) {
//                    val ax = Size.ofOrThrow(x)
//                    val ay = Size.ofOrThrow(y)
//                    val a = Box(ax)
//                    val b = Box(ay)
//                    a.requireSameSize(b) shouldBe Either.Left(
//                        SizeError.MatchingSizesExpected(expected = ay, actual = ax).copy(
//                            // Ensure we match the exact expected/actual from the implementation:
//                            expected = b.size,
//                            actual = a.size
//                        )
//                    )
//                }
//            }
//        }
//    }
//
//    "checkSameSizeOrThrow" - {
//
//        "does not throw when sizes are equal" {
//            checkAll(Arb.int(min = 0, max = 1024)) { n ->
//                val s = Size.ofOrThrow(n)
//                val a = Box(s)
//                val b = Box(s)
//                shouldNotThrowAny { a.checkSameSizeOrThrow(b) }
//            }
//        }
//
//        "throws MatchingSizesExpected when sizes differ" {
//            checkAll(
//                Arb.int(min = 0, max = 1024),
//                Arb.int(min = 0, max = 1024)
//            ) { x, y ->
//                if (x != y) {
//                    val a = Box(Size.ofOrThrow(x))
//                    val b = Box(Size.ofOrThrow(y))
//                    val ex = shouldThrow<SizeError.MatchingSizesExpected> { a.checkSameSizeOrThrow(b) }
//                    ex.expected shouldBe a.size
//                    ex.actual shouldBe b.size
//                }
//            }
//        }
//    }
//
//    "mockk-based checks (verifying interaction with size property)" - {
//
//        "sameSizeAs reads sizes from both receivers" {
//            val s = Size.ofOrThrow(10)
//            val a = mockk<HasSize>()
//            val b = mockk<HasSize>()
//            every { a.size } returns s
//            every { b.size } returns s
//
//            (a sameSizeAs b).shouldBeTrue()
//        }
//
//        "requireSameSize returns Right(self) for equal sizes (mocked)" {
//            val s = Size.ofOrThrow(3)
//            val a = mockk<HasSize>()
//            val b = mockk<HasSize>()
//            every { a.size } returns s
//            every { b.size } returns s
//
//            a.requireSameSize(b) shouldBe Either.Right(a)
//        }
//
//        "requireSameSize returns Left for mismatched sizes (mocked)" {
//            val sa = Size.ofOrThrow(2)
//            val sb = Size.ofOrThrow(5)
//            val a = mockk<HasSize>()
//            val b = mockk<HasSize>()
//            every { a.size } returns sa
//            every { b.size } returns sb
//
//            a.requireSameSize(b) shouldBe Either.Left(
//                SizeError.MatchingSizesExpected(expected = b.size, actual = a.size)
//            )
//        }
//
//        "checkSameSizeOrThrow throws for mismatched sizes (mocked)" {
//            val sa = Size.ofOrThrow(1)
//            val sb = Size.ofOrThrow(2)
//            val a = mockk<HasSize>()
//            val b = mockk<HasSize>()
//            every { a.size } returns sa
//            every { b.size } returns sb
//
//            val ex = shouldThrow<SizeError.MatchingSizesExpected> { a.checkSameSizeOrThrow(b) }
//            ex.expected shouldBe sa
//            ex.actual shouldBe sb
//        }
//    }
//})
