/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.problem

import arrow.core.nonEmptyListOf
import cl.ravenhill.keen.problem.Problem.Companion.invoke
import cl.ravenhill.keen.problem.constrained.Constraint
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.withEdgecases
import io.kotest.property.arrow.core.nel
import io.kotest.property.checkAll
import io.mockk.mockk
import kotlin.math.tanh

class ProblemFreeSpecTest : FreeSpec({

    "Problem companion factories" - {
        val arbObjective = Arb.mixedObjective<Int>()
        val nelObjectives = Arb.nel(arbObjective)
        val arbConstraints = Arb.list(Arb.mockConstraint<Int>(), 0..3)

        "when a Problem is created from the NonEmptyList" - {
            "then objectives preserve order and constraints default to empty" {
                checkAll(nelObjectives) { objectives ->
                    val problem = Problem(objectives)

                    problem.objectives.shouldContainExactly(objectives)
                    problem.constraints.shouldBeEmpty()
                }
            }
        }

        "when a Problem is created from varargs" - {
            "then objectives preserve order and constraints default to empty" {
                checkAll(arbObjective, Arb.list(arbObjective, 0..4)) { f, others ->
                    val problem = Problem(f, *others.toTypedArray())

                    problem.objectives.shouldContainExactly(listOf(f) + others)
                    problem.constraints.shouldBeEmpty()
                }
            }
        }

        "given provided constraints" - {
            "when a Problem is created with constraints" - {
                "then objectives and constraints are preserved" {
                    checkAll(arbObjective, arbConstraints) { f, constraints ->
                        val p = Problem(f, constraints = constraints)

                        p.objectives.shouldContainExactly(f)
                        p.constraints.shouldContainExactly(constraints)
                    }
                }
            }
        }
    }

    "type parameter T" - {
        "given a custom representation type" - {
            data class Gene(val value: Int)

            "when a Problem is created" - {
                "then it keeps one objective and no constraints" {
                    context(Arb.objective<Gene>()) {
                        checkAll(Arb.problem()) { p ->
                            p.objectives
                                .shouldHaveSize(1)  // throws on size != 1
                                .shouldContainExactly(TODO())
                            p.constraints.shouldHaveSize(0)
                        }
                    }
                }
            }
        }
    }

    "edge behaviors" - {

        "given a single-objective NonEmptyList" - {
            val f = Objective<Double> { it.firstOrNull() ?: 0.0 }

            "when creating a Problem from that NonEmptyList" - {
                val p = Problem(nonEmptyListOf(f))

                "then the only objective is present" {
                    p.objectives.shouldContainExactly(f)
                }
            }
        }

        "given an explicit empty constraints list" - {
            val f = Objective<Int> { it.size.toDouble() }

            "when creating a Problem with empty constraints" - {
                val p = Problem(f, constraints = emptyList())

                "then constraints are empty" {
                    p.constraints.shouldHaveSize(0)
                }
            }
        }
    }
})

/**
 * Arbitrary Objective<T> that maps Solution<T> -> Double deterministically using a parametric, hash-based shape
 * `(bias + scale * nonlinearity(hash))`.
 *
 * Pure & total: no randomness during evaluation; the randomness only occurs when the objective is *created*.
 */
// Keep objective() deterministic & with good edge cases
private fun <T> Arb.Companion.objective(): Arb<Objective<T>> = Arb.bind(
    Arb.int(),                      // seed
    Arb.double(-10.0..10.0),        // bias
    Arb.double(-5.0..5.0),          // scale
    Arb.double(0.1..4.0)            // slope
) { seed, bias, scale, slope ->
    Objective<T> { sol ->
        val h = sol.hashCode() xor seed
        val x = (h % 100_000) / 10_000.0
        bias + scale * tanh(slope * x)
    }
}.withEdgecases(
    listOf(
        Objective { 0.0 },
        Objective { 1.0 },
        Objective { -1.0 }
    )
)

/**
 * A small mixture that randomly chooses among:
 *  - constant objective,
 *  - linear hash-based,
 *  - tanh hash-based.
 */
private fun <T> Arb.Companion.mixedObjective(): Arb<Objective<T>> {
    val constant = arbitrary {
        val c = Arb.double(-2.0..2.0).bind()
        Objective<T> { _ -> c }  // constant function; no randomness at call time
    }

    val linear = arbitrary {
        val seed = it.random.nextInt()
        val a = Arb.double(-2.0..2.0).bind()
        val b = Arb.double(-2.0..2.0).bind()
        Objective<T> { s ->
            // normalize hash so values are bounded-ish
            val x = ((s.hashCode() xor seed) % 100_000) / 10_000.0
            a * x + b
        }
    }

    val smooth = Arb.objective<T>() // already bounded with tanh

    return Arb.choice(constant, linear, smooth)
}

/**
 * Generates relaxed MockK instances of `Constraint<T>`.
 *
 * - Pure from the PBT perspective: each mock is created once and reused during evaluation.
 * - Deterministic names make failing traces easier to read.
 * - A [stub] hook lets tests customize behavior per generated mock.
 */
private inline fun <reified T> Arb.Companion.mockConstraint(
    crossinline stub: Constraint<T>.() -> Unit = {}
): Arb<Constraint<T>> = arbitrary { rs ->
    // Deterministic(ish) name per generated mock – helpful in logs
    val suffix = rs.random.nextInt().toString(16)
    mockk<Constraint<T>>(
        relaxed = true, name = "Constraint<${
            T::class.simpleName ?: "T"
        }>#${suffix}"
    ).apply { stub(this) }
}

context(objectiveCtx: Arb<Objective<T>>)
private fun <T> Arb.Companion.problem(): Arb<Problem<T>> =
    objectiveCtx.map { objective -> Problem(objective) }
