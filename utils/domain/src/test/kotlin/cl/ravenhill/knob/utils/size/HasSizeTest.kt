/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.utils.size

import cl.ravenhill.knob.utils.orderedPair
import cl.ravenhill.knob.utils.size.gen.hasSize
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
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

        "when requiring the same size" - {

            "then it returns Right(self) when sizes are equal" {
                checkAll(Arb.sameSizePair(Arb.validSize())) { (a, b) ->
                    withClue("expected equal sizes, a=${a.size.toInt()}, b=${b.size.toInt()}") {
                        (a requireSameSize b).shouldBeRight(a)
                    }
                }
            }

            "then it returns Left(MatchingSizesExpected) when sizes differ" {
                checkAll(Arb.diffSizePair(Arb.validSize())) { (a, b) ->
                    withClue("expected mismatch, a=${a.size.toInt()}, b=${b.size.toInt()}") {
                        a.requireSameSize(b)
                            .shouldBeLeft(SizeError.MatchingSizesExpected(b.size, a.size))
                    }
                }
            }
        }

        "when enforcing the same size (throwing API)" - {

            "then it does not throw when sizes are equal" {
                checkAll(Arb.sameSizePair(Arb.validSize())) { (a, b) ->
                    withClue("expected equal sizes, a=${a.size.toInt()}, b=${b.size.toInt()}") {
                        shouldNotThrowAny { a.checkSameSizeOrThrow(b) }
                    }
                }
            }

            "then it throws MatchingSizesExpected when sizes differ" {
                checkAll(Arb.diffSizePair(Arb.validSize())) { (a, b) ->
                    withClue("expected mismatch, a=${a.size.toInt()}, b=${b.size.toInt()}") {
                        val ex = shouldThrow<SizeError.MatchingSizesExpected> { a.checkSameSizeOrThrow(b) }
                        ex.expected shouldBe a.size
                        ex.actual shouldBe b.size
                    }
                }
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
