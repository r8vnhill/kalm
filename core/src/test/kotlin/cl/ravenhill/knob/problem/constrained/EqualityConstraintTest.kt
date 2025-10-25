/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.problem.constrained

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import arrow.core.tail
import cl.ravenhill.knob.repr.Solution
import cl.ravenhill.knob.exceptions.InvalidThresholdException
import cl.ravenhill.knob.matchers.shouldBeCloseTo
import cl.ravenhill.knob.repr.map
import cl.ravenhill.knob.utils.EqualityThreshold
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FreeSpec
import io.kotest.datatest.withData
import io.kotest.engine.names.WithDataTestName
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.property.Arb
import io.kotest.property.Gen
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

class EqualityConstraintTest : FreeSpec({
    "An 'Equality Constraint' when" - {
        "creating an instance" - {
            "in the context of" - {
                "a valid equality threshold should" - {
                    with(Validity.AllValid) {
                        "bind the threshold to the constraint" {
                            checkAll(
                                arbEqualityThreshold(), // Valid thresholds
                                arbFunction(),  // Functions to apply to the solution
                                arbFunction()   // Functions to apply to the solution
                            ) { threshold, left, right ->
                                with(threshold.right()) {
                                    EqualityConstraint(left, right)
                                        .shouldBeRight()
                                        .threshold
                                        .value
                                        .shouldBeCloseTo(threshold.value)
                                }
                            }
                        }
                    }
                }
                "an invalid equality threshold should" - {
                    with(Validity.AllInvalid) {
                        "return a Left with an InvalidThresholdException" {
                            checkAll(
                                arbEqualityThreshold(),
                                arbFunction(),
                                arbFunction()
                            ) { invalidThreshold, left, right ->
                                with(invalidThreshold.left()) {
                                    EqualityConstraint(left, right)
                                        .shouldBeLeft()
                                        .shouldHaveMessage(
                                            invalidThreshold
                                                .message
                                                .shouldNotBeNull {
                                                    "Invalid threshold message should not be null, this is probably a " +
                                                            "bug on the test setup"
                                                }
                                        )
                                }
                            }
                        }
                    }
                }
            }
            "with default threshold" - {
                "provided by the framework should" - {
                    "create a valid constraint" {
                        checkAll(
                            arbFunction(),  // Functions to apply to the solution
                            arbFunction()   // Functions to apply to the solution
                        ) { left, right ->
                            EqualityConstraint.withDefaultThreshold(left, right)
                                .shouldBeRight()
                                .threshold
                                .value
                                .shouldBeCloseTo(EqualityThreshold.DEFAULT)
                        }
                    }
                }
                "provided by the user should" - {
                    "create a valid constraint if the threshold is valid" {
                        checkAll(
                            Arb.double(min = 0.0, includeNonFiniteEdgeCases = false),   // Valid thresholds
                            arbFunction(),  // Functions to apply to the solution
                            arbFunction()   // Functions to apply to the solution
                        ) { threshold, left, right ->
                            EqualityConstraint.withDefaultThreshold(left, right, threshold)
                                .shouldBeRight()
                                .threshold
                                .value
                                .shouldBeCloseTo(threshold)
                        }
                    }
                    "return a Left with an InvalidThresholdException if the threshold is invalid" {
                        with(Validity.AllInvalid) {
                            checkAll(
                                Arb.double(max = 0.0, includeNonFiniteEdgeCases = false)
                                    .filter { it < 0.0 }, // Invalid thresholds
                                arbFunction(),  // Functions to apply to the solution
                                arbFunction()   // Functions to apply to the solution
                            ) { invalidThreshold, left, right ->
                                EqualityConstraint.withDefaultThreshold(left, right, invalidThreshold)
                                    .shouldBeLeft()
                                    .shouldHaveMessage(
                                        EqualityThreshold(invalidThreshold)
                                            .leftOrElse { error("Expected an invalid threshold, but got: $it") }
                                            .message
                                            .shouldNotBeNull {
                                                "Invalid threshold message should not be null, this is probably a " +
                                                        "bug on the test setup"
                                            }
                                    )
                            }
                        }
                    }
                }
            }
        }

        "invoking the constraint" - {
            data class Case(
                val threshold: Double,
                val leftFn: (Solution<Int>) -> Double,
                val rightFn: (Solution<Int>) -> Double,
                val solution: Solution<Int>
            ) : WithDataTestName {
                override fun dataTestName() = "threshold=$threshold | solution=${solution.toList()}"
            }

            "with a solution that meets the equality condition should" - {
                "return true" - {
                    withData(
                        /* 1️⃣ Exact equality, zero threshold */
                        Case(
                            threshold = 0.0,
                            leftFn   = { it.first().toDouble() },
                            rightFn  = { it.first().toDouble() },
                            solution = Solution(nonEmptyListOf(7))
                        ),
                        /* 2️⃣ Off-by-epsilon, inside a tiny tolerance */
                        Case(
                            threshold = 1e-6,
                            leftFn   = { it.first().toDouble() },
                            rightFn  = { it.first().toDouble() + 9e-7 },   // diff = 0.0000009 < 1e-6
                            solution = Solution(nonEmptyListOf(42))
                        ),
                        /* 3️⃣ Aggregate (sum) with modest tolerance */
                        Case(
                            threshold = 0.5,
                            leftFn   = { it.sumOf(Int::toDouble) },
                            rightFn  = { it.sumOf(Int::toDouble) + 0.3 },  // diff = 0.3 < 0.5
                            solution = Solution(nonEmptyListOf(1, 2, 3, 4))
                        ),
                        /* 4️⃣ Negative numbers – average comparison */
                        Case(
                            threshold = 0.25,
                            leftFn   = { it.map(Int::toDouble).average() },
                            rightFn  = { it.map(Int::toDouble).average() + 0.2 },
                            solution = Solution(nonEmptyListOf(-10, -20, -30))
                        ),
                        /* 5️⃣ Large magnitude values with larger tolerance */
                        Case(
                            threshold = 2.0,
                            leftFn   = { it[0].toDouble() },
                            rightFn  = { it[0].toDouble() + 1.9 },         // diff = 1.9 < 2.0
                            solution = Solution(nonEmptyListOf(10_000, 20_000))
                        )
                    ) { (threshold, leftFn, rightFn, solution) ->
                        with(EqualityThreshold(threshold)) {
                            EqualityConstraint(leftFn, rightFn)
                                .shouldBeRight()          // Constraint must be created successfully
                                .invoke(solution)
                                .shouldBeTrue()           // …and must evaluate to true
                        }
                    }
                }
            }
            "with a solution that does not meet the equality condition should" - {
                "return false" - {
                    withData(
                        /* 1️⃣ Zero threshold but different values (easy miss) */
                        Case(
                            threshold = 0.0,
                            leftFn    = { it.first().toDouble() },
                            rightFn   = { it.first().toDouble() + 1.0 },
                            solution  = Solution(nonEmptyListOf(10))
                        ),
                        /* 2️⃣ Difference *just* above a tiny tolerance */
                        Case(
                            threshold = 1e-6,
                            leftFn    = { it.first().toDouble() },
                            rightFn   = { it.first().toDouble() + 1.1e-6 }, // diff = 1.1 µ > tol
                            solution  = Solution(nonEmptyListOf(3))
                        ),
                        /* 3️⃣ Sum deviates beyond tolerance */
                        Case(
                            threshold = 0.5,
                            leftFn    = { it.sumOf(Int::toDouble) },
                            rightFn   = { it.sumOf(Int::toDouble) + 0.6 },
                            solution  = Solution(nonEmptyListOf(2, 4, 6))
                        ),
                        /* 4️⃣ Negative numbers, average outside threshold */
                        Case(
                            threshold = 0.25,
                            leftFn    = { it.map(Int::toDouble).average() },
                            rightFn   = { it.map(Int::toDouble).average() - 0.3 }, // |diff| = 0.3 > 0.25
                            solution  = Solution(nonEmptyListOf(-5, -10, -15))
                        ),
                        /* 5️⃣ Large magnitude: diff bigger than tolerance */
                        Case(
                            threshold = 2.0,
                            leftFn    = { it[0].toDouble() },
                            rightFn   = { it[0].toDouble() + 2.1 }, // diff = 2.1 > 2.0
                            solution  = Solution(nonEmptyListOf(50_000, 1))
                        )
                    ) { (threshold, leftFn, rightFn, solution) ->

                        with(EqualityThreshold(threshold)) {
                            EqualityConstraint(leftFn, rightFn)
                                .shouldBeRight()          // creation succeeds
                                .invoke(solution)         // evaluation
                                .shouldBeFalse()          // ❌ must fail equality
                        }
                    }
                }
            }
        }
    }
})

context(validity: Validity.AllValid)
private fun arbEqualityThreshold(): Gen<EqualityThreshold> =
    Arb.double(min = 0.0, includeNonFiniteEdgeCases = false).map { value ->
        EqualityThreshold(value)
            .getOrElse { error("Expected a valid threshold, but got: $it") }
    }

context(validity: Validity.AllInvalid)
private fun arbEqualityThreshold(): Gen<InvalidThresholdException> =
    Arb.double(max = 0.0)
        .filter { it < 0.0 || it.isNaN() || it.isInfinite() }
        .map { value ->
            EqualityThreshold(value)
                .leftOrElse { error("Expected an invalid threshold, but got: $it") }
        }

private fun <A, B> Either<A, B>.leftOrElse(function: (B) -> A): A = when (this) {
    is Either.Left -> value
    is Either.Right -> function(value)
}

private val functions = listOf(
    { solution: Solution<Int> -> solution.first().toDouble() },
    { solution: Solution<Int> -> solution.tail().first().toDouble() },
    { solution: Solution<Int> -> solution.sumOf { it.toDouble() } },
    { solution: Solution<Int> -> solution.map { it.toDouble() }.average() },
)

private fun arbFunction(): Arb<(Solution<Int>) -> Double> = Arb.element(functions)

private sealed interface Validity {
    data object AllValid : Validity
    data object AllInvalid : Validity
}
