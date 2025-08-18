/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.utils

import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.single
import io.kotest.property.checkAll

class OrderedPairArbTest : FreeSpec({

    "an orderedPair arb (two sources)" - {
        "when strict=false" - {
            "should always produce a <= b" {
                checkAll(Arb.orderedPair(Arb.int(), Arb.int())) { (lo, hi) ->
                    lo shouldBeLessThanOrEqual hi
                }
            }

            "should allow equality" {
                checkAll(Arb.int()) { x ->
                    val (a, b) = Arb.orderedPair(
                        Arb.constant(x),
                        Arb.constant(x),
                        strict = false
                    ).single()
                    a shouldBe b
                }
            }
        }

        "when strict=true" - {
            "should always produce a < b" {
                // Use non-degenerate generators to avoid exhaustion by equals
                checkAll(Arb.int(min = Int.MIN_VALUE, max = Int.MAX_VALUE - 1)) { x ->
                    // Construct a pair that has a good chance to be distinct
                    val arb1 = Arb.constant(x)
                    val arb2 = Arb.constant(x + 1)
                    val (a, b) = Arb.orderedPair(arb1, arb2, strict = true).single()
                    a shouldBeLessThan b
                }
            }
        }
    }

    "an orderedPair arb (single source)" - {
        "when strict=false" - {
            "should always produce a <= b" {
                checkAll(Arb.orderedPair(Arb.int(), strict = false)) { (a, b) ->
                    a shouldBeLessThanOrEqual b
                }
            }

            "should allow equality (DDT)" {
                checkAll(Arb.int()) { x ->
                   val (a, b) = Arb.orderedPair(Arb.constant(x), strict = false).single()
                   a shouldBe b
                }
            }
        }

        "when strict=true" - {
            "should always produce a < b (PBT)" {
                checkAll(iterations = 500, Arb.orderedPair(Arb.int(0..1_000), strict = true)) { (a, b) ->
                    a shouldBeLessThan b
                }
            }
        }
    }

    "a descendingPair arb (two sources)" - {
        "when strict=false" - {
            "should always produce first >= second (PBT)" {
                checkAll(Arb.descendingPair(Arb.int(), Arb.int(), strict = false)) { (hi, lo) ->
                    hi shouldBeGreaterThanOrEqual lo
                }
            }

            "should be the reverse of orderedPair" - {
                withData(Pair(1, 9), Pair(9, 1), Pair(5, 5)) { (x, y) ->
                    val ord = Arb.orderedPair(Arb.constant(x), Arb.constant(y), strict = false).single()
                    val desc = Arb.descendingPair(Arb.constant(x), Arb.constant(y), strict = false).single()
                    desc shouldBe (ord.second to ord.first)
                }
            }
        }

        "when strict=true" - {
            "should always produce first > second (PBT)" {
                checkAll(Arb.descendingPair(Arb.int(), Arb.int(), strict = true)) { (hi, lo) ->
                    hi shouldBeGreaterThan lo
                }
            }
        }
    }

    "a descendingPair arb (single source)" - {
        "when strict=false" - {
            "should always produce first >= second" {
                checkAll(Arb.descendingPair(Arb.int(), strict = false)) { (hi, lo) ->
                    hi shouldBeGreaterThanOrEqual lo
                }
            }
        }

        "when strict=true" - {
            "should always produce first > second" {
                checkAll(Arb.descendingPair(Arb.int(), strict = true)) { (hi, lo) ->
                    hi shouldBeGreaterThan lo
                }
            }
        }
    }
})
