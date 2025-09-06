/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.generators

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.arrow.core.either
import io.kotest.property.checkAll
import java.util.concurrent.atomic.AtomicInteger


class TraverseEitherSpec : FreeSpec({

    "An Arb<Either<E, A>>.traverseEither" - {
        "when the source yields Left values" - {
            val lefts: Arb<Either<String, Int>> = Arb.string().map(String::left)

            "should leave the Left value unchanged (PBT)" {
                val f: (Int) -> Arb<Int> = { Arb.constant(it + 1) } // should not be used

                val paired: Arb<Pair<Either<String, Int>, Either<String, Int>>> =
                    lefts.flatMap { e -> Arb.constant(e).traverseEither(f).map { out -> e to out } }

                checkAll(paired) { (input, output) ->
                    output shouldBeLeft input.leftOrNull()
                }
            }

            "should not evaluate the mapping function (PBT)" {
                val calls = AtomicInteger(0)
                val f: (Int) -> Arb<Int> = {
                    calls.incrementAndGet()
                    Arb.constant(it + 42)
                }

                val out: Arb<Either<String, Int>> = lefts.traverseEither(f)
                checkAll(out) { e -> e.shouldBeLeft() }
                calls.get() shouldBe 0
            }
        }

        "when the source yields Right values and f is deterministic (Arb.constant)" - {
            "should equal a pure map over Right (PBT)" {
                val rights: Arb<Either<String, Int>> = Arb.int().map(Int::right)
                val g: (Int) -> String = { "v=$it" }
                val f: (Int) -> Arb<String> = { a -> Arb.constant(g(a)) }

                val paired: Arb<Pair<Either<String, Int>, Either<String, String>>> =
                    rights.flatMap { e -> Arb.constant(e).traverseEither(f).map { out -> e to out } }

                checkAll(paired) { (input, output) ->
                    output shouldBe input.map(g)
                }
            }
        }

        "when the source yields mixed Left/Right values and f is effectful (non-constant)" - {
            "should preserve Left as-is and map Right to the effect's support (PBT)" {
                val mixed: Arb<Either<String, Int>> = Arb.either(Arb.string(), Arb.int(0..100))

                // f returns values in [a, a+2]
                val f: (Int) -> Arb<Int> = { a ->
                    Arb.choice(
                        Arb.constant(a),
                        Arb.constant(a + 1),
                        Arb.constant(a + 2),
                    )
                }

                val paired: Arb<Pair<Either<String, Int>, Either<String, Int>>> =
                    mixed.flatMap { e -> Arb.constant(e).traverseEither(f).map { out -> e to out } }

                checkAll(100, paired) { (input, output) ->
                    when (input) {
                        is Either.Left -> output.shouldBeLeft(input.value)
                        is Either.Right -> {
                            val v = output.shouldBeRight()
                            (v == input.value || v == input.value + 1 || v == input.value + 2).shouldBeTrue()
                        }
                    }
                }
            }
        }

        "when used as a law: traverse with constant Arb == map over Right (DDT)" - {
            data class Case(val offset: Int, val e: Either<String, Int>)

            withData(
                Case(0, "err".left()),
                Case(1, 7.right()),
                Case(-5, 42.right()),
            ) { (offset, e) ->
                val g: (Int) -> Int = { it + offset }
                val f: (Int) -> Arb<Int> = { a -> Arb.constant(g(a)) }

                val actual = Arb.constant(e).traverseEither(f)
                checkAll(actual) { out -> out shouldBe e.map(g) }
            }
        }
    }
})
