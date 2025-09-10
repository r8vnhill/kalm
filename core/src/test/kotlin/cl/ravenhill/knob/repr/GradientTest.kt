/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.repr

import arrow.core.Either
import cl.ravenhill.knob.generators.finiteDouble
import cl.ravenhill.knob.repr.gen.pairedWithAliasedGradient
import cl.ravenhill.knob.repr.gen.pairedWithGradient
import cl.ravenhill.knob.repr.gen.sizedDoubleArrayEither
import cl.ravenhill.knob.repr.gen.withAliasedGradient
import cl.ravenhill.knob.repr.gen.withGradient
import cl.ravenhill.knob.repr.gen.withIndex
import cl.ravenhill.knob.repr.gen.withOutOfBoundsIndex
import cl.ravenhill.knob.repr.gen.withValidIndex
import cl.ravenhill.knob.utils.size.Size
import cl.ravenhill.knob.utils.size.SizeError
import cl.ravenhill.knob.utils.size.UnsafeSizeCreation
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arrow.core.nonEmptyList
import io.kotest.property.checkAll

@OptIn(UnsafeSizeCreation::class)
class GradientFreeSpec : FreeSpec({

    context(Arb.finiteDouble()) {
        val nonEmptyList = Arb.nonEmptyList(contextOf<Arb<Double>>())
        val positiveSizeRight = Arb.size(min = 1)
        val positiveValidSize = Arb.validSize(min = 1)

        "Given Gradient factories" - {
            val strictlyPositiveExpected = SizeError.StrictlyPositiveExpected(0)

            "when creating from a non-empty list" - {
                "then it should create a valid Gradient" {
                    checkAll(nonEmptyList) { list ->
                        Gradient(list) shouldContainExactly list
                    }
                }
            }

            "when creating from a vararg of doubles" - {
                "then it should create a valid Gradient" {
                    checkAll(nonEmptyList) { list ->
                        Gradient.of(list.first(), *list.tail.toDoubleArray())
                    }
                }
            }

            "when safely creating from a DoubleArray" - {
                "that is not empty" - {

                    context(positiveSizeRight) {
                        val pairedArrayWithGradient = Arb.sizedDoubleArrayEither().pairedWithGradient()

                        "then it should create a valid Gradient" {
                            checkAll(pairedArrayWithGradient) { e ->
                                val (arr, g) = e.shouldBeRight(failureMessage = { it.message.toString() })
                                g shouldHaveSize arr.size
                                g shouldContainExactly arr.toList()
                            }
                        }

                        "and the resulting Gradient should be immune to external mutations (defensive copy)" {
                            checkAll(
                                pairedArrayWithGradient.withIndex(),
                                contextOf<Arb<Double>>()
                            ) { e, newValue ->
                                val (index, arr, g) = e.shouldBeRight(failureMessage = { it.message.toString() })
                                val before = g[index]
                                arr[index] = newValue

                                g[index] shouldBe before
                            }
                        }
                    }
                }

                "that is empty" - {
                    "then it should return a SizeError" {
                        Gradient.fromArray(doubleArrayOf())
                            .shouldBeLeft(strictlyPositiveExpected)
                    }
                }
            }

            "when unsafely creating from a DoubleArray" - {
                context(_: Arb<Either<SizeError, Size>>)
                fun pairedWithAliasedGradient() = Arb
                    .sizedDoubleArrayEither()
                    .pairedWithAliasedGradient()

                "then it should create a Gradient without validation" {
                    context(Arb.size()) { // Allow empty arrays for this test
                        checkAll(pairedWithAliasedGradient()) { e ->
                            val (array, gradient) = e.shouldBeRight(failureMessage = { it.message.toString() })
                            gradient shouldContainExactly array.toList()
                        }
                    }
                }

                "then modifications to the array should reflect in the Gradient" {
                    context(positiveSizeRight) { // A non-empty size is required for the array
                        checkAll(
                            pairedWithAliasedGradient().withIndex(),
                            contextOf<Arb<Double>>()
                        ) { e, newValue ->
                            val (index, array, gradient) = e.shouldBeRight(failureMessage = { it.message.toString() })
                            array[index] = newValue
                            gradient[index] shouldBe newValue
                        }
                    }
                }
            }

            "when creating a zero-filled Gradient" - {
                "with a valid size" - {
                    "then it should create a valid Gradient" {
                        checkAll(positiveValidSize) { size ->
                            Gradient.zeros(size)
                                .shouldBeRight()
                                .shouldContainExactly(List(size.value) { 0.0 })
                        }
                    }
                }

                "with an invalid size" - {
                    "then it should return a SizeError" {
                        Gradient.zeros(Size.zero)
                            .shouldBeLeft(strictlyPositiveExpected)
                    }
                }
            }

            "when creating a filled Gradient" - {
                "with a valid size" - {
                    "then it should create a valid Gradient" {
                        checkAll(positiveValidSize, contextOf<Arb<Double>>()) { size, value ->
                            Gradient.fill(size, value)
                                .shouldBeRight()
                                .shouldContainExactly(List(size.value) { value })
                        }
                    }
                }

                "with an invalid size" - {
                    "then it should return a SizeError" {
                        checkAll(contextOf<Arb<Double>>()) { value ->
                            Gradient.fill(Size.zero, value)
                                .shouldBeLeft(strictlyPositiveExpected)
                        }
                    }
                }
            }
        }

        "Given a Gradient" - {
            val nelGradient = nonEmptyList.withGradient()
            val aliasedArrayGradient = nonEmptyList.withAliasedGradient()

            "when accessing its size" - {
                "then it should return the size of the contained array" {
                    checkAll(nelGradient) { (components, gradient) ->
                        gradient shouldHaveSize components.size
                    }
                }
            }

            "when accessing its components" - {
                val validGradientWithIndex = nelGradient.withValidIndex()
                val invalidGradientWithIndex = nelGradient.withOutOfBoundsIndex()

                "with get" - {

                    "should return the value at a valid index" {
                        checkAll(validGradientWithIndex) { (components, gradient, i) ->
                            gradient[i] shouldBe components[i]
                        }
                    }

                    "then it throws IndexOutOfBoundsException for an invalid index" {
                        checkAll(invalidGradientWithIndex) { (_, gradient, i) ->
                            withClue("i=$i ${gradient.size}") {
                                shouldThrow<IndexOutOfBoundsException> { gradient[i] }
                            }
                        }
                    }
                }

                "with getOrNull" - {
                    "should return the value at a valid index" {
                        checkAll(validGradientWithIndex) { (components, gradient, i) ->
                            gradient.getOrNull(i)
                                .shouldNotBeNull()
                                .shouldBe(components[i])
                        }
                    }

                    "should return null for an invalid index" {
                        checkAll(invalidGradientWithIndex) { (_, gradient, i) ->
                            withClue("i=$i ${gradient.size}") {
                                gradient.getOrNull(i).shouldBeNull()
                            }
                        }
                    }
                }

                "with getOrElse" - {
                    "should return the value at a valid index" {
                        checkAll(validGradientWithIndex) { (components, gradient, i) ->
                            val fallback = { _: Int -> Double.NaN }
                            withClue("i=$i size=${components.size}") {
                                gradient.getOrElse(i, fallback) shouldBe components[i]
                            }
                        }
                    }

                    "should return the default for an invalid index (and passes the index)" {
                        checkAll(invalidGradientWithIndex) { (_, gradient, i) ->
                            val fallback = { j: Int -> j.toDouble() + 1.0 }
                            withClue("i=$i size=${gradient.size}") {
                                gradient.getOrElse(i, fallback) shouldBe fallback(i)
                            }
                        }
                    }
                }
            }

            "when converting" - {

                "to a DoubleArray" - {

                    "then it returns a defensive copy with identical contents" {
                        checkAll(nonEmptyList.withGradient().withValidIndex()) { (components, gradient, i) ->
                            val arr = gradient.toDoubleArray()
                            arr.shouldHaveSize(components.size)
                                .toList()
                                .shouldContainExactly(components)

                            // Mutating the returned array must NOT affect the gradient
                            val before = gradient[i]
                            arr[i] = arr[i] + 1.0
                            gradient[i] shouldBe before
                        }
                    }
                }

                "to a List<Double>" - {
                    "then it returns a list with the same contents" {
                        checkAll(nonEmptyList.withGradient()) { (components, gradient) ->
                            gradient.toList() shouldContainExactly components
                        }
                    }

                    "then, for an aliased gradient, the list view reflects backing-array changes" {
                        // Build gradients that ALIAS their backing arrays (unsafe constructor through gen)
                        checkAll(
                            aliasedArrayGradient.withValidIndex(),
                            contextOf<Arb<Double>>()
                        ) { gradientData, newValue ->
                            val (backing, g, i) = gradientData

                            val list = g.toList()

                            // Mutate array; the list should reflect change (same storage)
                            backing[i] = newValue

                            // Ensure the view shows the updated value
                            list[i] shouldBe newValue
                            // And the gradient reflects the same (aliased)
                            g[i] shouldBe newValue

                            // Sanity: if you recompute a fresh list, it must also match
                            g.toList()[i] shouldBe newValue
                        }
                    }
                }
            }

            "metrics" - {
                TODO()
            }

            "algebraic ops" - {
                TODO()
            }

            "higher-order ops" - {
                TODO()
            }

            "std overrides" - {
                TODO()
            }
        }

        "Double.times(g: Gradient)" - {
            TODO()
        }
    }
})
