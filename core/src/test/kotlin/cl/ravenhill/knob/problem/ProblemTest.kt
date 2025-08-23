/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.problem

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import cl.ravenhill.knob.problem.constrained.Constraint
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.withEdgecases
import io.kotest.property.arbitrary.zip
import io.kotest.property.arrow.core.nel
import io.kotest.property.checkAll
import io.mockk.mockk
import kotlin.math.tanh

class ProblemFreeSpecTest : FreeSpec({

    "Problem companion factories" - {
        val arbObjective = Arb.mixedObjective<Int>()
        val nelObjectives = Arb.nel(arbObjective)
        val arbConstraints = Arb.mockConstraint<Int>().asList()

        "when a Problem is created from the NonEmptyList" - {
            "then objectives preserve order and constraints default to empty" {
                checkAll(nelObjectives) { objectives ->
                    val problem = Problem(objectives)

                    problem.objectives shouldContainExactly objectives
                    problem.constraints.shouldBeEmpty()
                }
            }
        }

        "when a Problem is created from varargs" - {
            "then objectives preserve order and constraints default to empty" {
                checkAll(arbObjective, Arb.list(arbObjective, 0..4)) { f, others ->
                    val problem = Problem(f, *others.toTypedArray())

                    problem.objectives shouldContainExactly listOf(f) + others
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
                        p.constraints shouldContainExactly constraints
                    }
                }
            }
        }
    }

    "type parameter T" - {
        "given a custom representation type" - {
            data class Gene(val value: Int)

            "when a Problem is created" - {
                "then it preserves objectives and constraints" {
                    context(Arb.objective<Gene>().asNel(), Arb.mockConstraint<Gene>().asList()) {
                        checkAll(Arb.problemFixture<Gene>()) { (objectives, constraints, problem) ->
                            problem.objectives.shouldContainExactly(objectives)
                            problem.constraints.shouldContainExactly(constraints)
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
                    p.constraints.shouldBeEmpty()
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

/**
 * Transforms an [Arb] instance for [Constraint] into an [Arb] instance for lists of [Constraint].
 */
private fun <T> Arb<Constraint<T>>.asList(): Arb<List<Constraint<T>>> =
    Arb.list(this, 0..3)

/**
 * Transforms an [Arb] of [Objective] instances into an [Arb] of [NonEmptyList] containing [Objective] instances.
 */
private fun <T> Arb<Objective<T>>.asNel(): Arb<NonEmptyList<Objective<T>>> =
    Arb.nel(this, 1..5)

/**
 * A small bundle used in tests to carry objectives, constraints and the constructed [Problem].
 */
private data class ProblemFixture<T>(
    val objectives: NonEmptyList<Objective<T>>,
    val constraints: Collection<Constraint<T>>,
    val problem: Problem<T>
)

/**
 * Generates an arbitrary [ProblemFixture] for testing by combining random objectives and constraints.
 */
context(
    objectivesCtx: Arb<NonEmptyList<Objective<T>>>,
    constraintsCtx: Arb<Collection<Constraint<T>>>
)
private fun <T> Arb.Companion.problemFixture(): Arb<ProblemFixture<T>> =
    Arb.zip(objectivesCtx, constraintsCtx) { objectives, constraints ->
        ProblemFixture(objectives, constraints, Problem(objectives, constraints))
    }
