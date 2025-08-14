/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.repr

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import cl.ravenhill.keen.util.size.SizeError
import cl.ravenhill.keen.util.size.Size
import cl.ravenhill.keen.util.size.UnsafeSizeCreation
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.doubleArray
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arrow.core.nonEmptyList
import io.kotest.property.checkAll

@OptIn(UnsafeSizeCreation::class)
class GradientFreeSpec : FreeSpec({
    "Given Gradient factories" - {
        "when creating from a non-empty list" - {
            "then it should create a valid Gradient" {
                checkAll(Arb.nonEmptyList(Arb.finiteDouble())) { list ->
                    Gradient(list) shouldContainExactly list
                }
            }
        }

        "when creating from a vararg of doubles" - {
            "then it should create a valid Gradient" {
                checkAll(Arb.nonEmptyList(Arb.finiteDouble())) { list ->
                    Gradient.of(list.first(), *list.tail.toDoubleArray())
                }
            }
        }

        "when safely creating from a DoubleArray" - {
            "that is not empty" - {

                // Contexts ensure strictly-positive sizes and finite doubles
                context(Arb.size(), Arb.finiteDouble()) {

                    "then it should create a valid Gradient" {
                        checkAll(Arb.sizedDoubleArrayEither()) { e ->
                            val arr = e.shouldBeRight() // unwrap Either
                            val g = Gradient.fromArray(arr) // copies storage
                                .shouldBeRight()
                            g.size.toInt() shouldBe arr.size
                            g shouldContainExactly arr.toList()
                        }
                    }

//                    "and the resulting Gradient should be immune to external mutations (defensive copy)" {
//                        checkAll(Arb.sizedDoubleArrayEither(), Arb.double().filter(Double::isFinite)) { e, newValue ->
//                            val arr = e.shouldBeRight()
//                            val g = Gradient.fromArray(arr)
//
//                            val before = g[0]
//                            arr[0] = newValue
//
//                            g[0] shouldBe before
//                        }
//                    }
                }
            }

//            "that is empty" - {
//                "then it should return a SizeError" {
//                    Gradient.fromArray(doubleArrayOf())
//                        .shouldBeLeft(SizeError.StrictlyPositiveExpected(0))
//                }
//            }
//        }
//
//        "when unsafely creating from a DoubleArray" - {
//            "then it should create a Gradient without validation" {
//                context(Arb.size(), Arb.finiteDouble()) {
//                    checkAll(
//                        Arb.doubleArray()
//                            .withEdgecases(doubleArrayOf())
//                    ) { array ->
//                        Gradient.unsafeFromOwnedArray(array)
//                            .shouldContainExactly(array.toList())
//                    }
//                }
//            }
//
//            "then modifications to the array should reflect in the Gradient" {
//                context(Arb.size(), Arb.finiteDouble()) {
//                    checkAll(Arb.doubleArray(), contextOf<Arb<Double>>()) { array, newValue ->
//                        val gradient = Gradient.unsafeFromOwnedArray(array)
//                        array[0] = newValue
//                        gradient[0] shouldBe newValue
//                    }
//                }
//            }
//        }
//
//        "when creating a zero-filled Gradient" - {
//            "with a valid size" - {
//                "then it should create a valid Gradient" {
//                    checkAll(Arb.size()) { size ->
//                        Gradient.zeros(size)
//                            .shouldBeRight()
//                            .shouldContainExactly(DoubleArray(size.value) { 0.0 }.toList())
//                    }
//                }
//            }

//            "with an invalid size" - {
//                "then it should return a SizeError" {
//                    Gradient.zeros(Size(0)).shouldBeLeft(SizeError.StrictlyPositiveExpected(0))
//                }
//            }
        }
    }
})

private fun Arb.Companion.finiteDouble(): Arb<Double> = Arb
    .double(
        min = -1e6, max = 1e6,
        includeNonFiniteEdgeCases = false
    )

private fun Arb.Companion.size(range: IntRange = 1..10): Arb<Either<SizeError, Size>> = Arb
    .int(range)
    .map { size -> Size(size) }

context(sizeCtx: Arb<Either<SizeError, Size>>, contentCtx: Arb<Double>)
private fun Arb.Companion.doubleArray() = sizeCtx.flatMap { size ->
    size.fold(
        ifLeft = { Arb.constant(it.left()) },
        ifRight = { Arb.doubleArray(Arb.constant(it.value), contentCtx).map { arr -> arr.right() } }
    )
}

/**
 * Builds an [Arb] that yields `Either<SizeError, DoubleArray>` using context-provided generators.
 *
 * @receiver [Arb.Companion]
 * @return An [Arb] of `Either<SizeError, DoubleArray>`.
 */
context(sizeCtx: Arb<Either<SizeError, Size>>, contentCtx: Arb<Double>)
fun Arb.Companion.sizedDoubleArrayEither(): Arb<Either<SizeError, DoubleArray>> =
    sizeCtx.flatMap { eitherSize ->
        eitherSize.traverseArb { sz ->
            Arb.doubleArray(length = Arb.constant(sz.toInt()), content = contentCtx)
        }
    }

/**
 * Transforms the right-hand side (`A`) of an `Either` into another `Either` within the context of an [Arb], using the
 * provided transformation function, while preserving any left-hand side (`E`) value.
 *
 * @param f A function that maps a value of type `A` to an [Arb] of type `B`.
 * @return An [Arb] containing an `Either` where the right-hand side (`A`) has been transformed to `B` using the
 *   supplied function, or the left-hand side (`E`) remains unchanged.
 */
private fun <E, A, B> Either<E, A>.traverseArb(f: (A) -> Arb<B>): Arb<Either<E, B>> = fold(
    ifLeft = { e -> Arb.constant(e.left()) },
    ifRight = { a -> f(a).map { it.right() } }
)
