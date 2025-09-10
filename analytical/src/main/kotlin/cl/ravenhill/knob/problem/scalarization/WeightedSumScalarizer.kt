/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.problem.scalarization

import arrow.core.NonEmptyList
import arrow.core.raise.either
import arrow.core.raise.ensure
import cl.ravenhill.knob.problem.objective.DifferentiableObjective
import cl.ravenhill.knob.repr.Gradient
import cl.ravenhill.knob.repr.Solution

public class WeightedSumScalarizer<T>(
    private val weights: List<Double>? = null,
    private val scale: (Double, Gradient.Result) -> Gradient.Result
) : Scalarizer<T> {

    override fun combine(
        objectives: NonEmptyList<DifferentiableObjective<T>>
    ): Scalarizer.Result<T> = either {
        val w = weights ?: List(objectives.size) { 1.0 }

        ensure(w.size == objectives.size) {
            ScalarizationError.IncompatibleObjectives(
                "Number of weights (${w.size}) must match number of objectives (${objectives.size})"
            )
        }

        // Fast path: single objective (optionally scaled).
        if (objectives.size == 1) {
            val f = objectives.head
            val wf = w.first()
            object : DifferentiableObjective<T> {
                override fun invoke(solution: Solution<T>): Double =
                    wf * f(solution)

                override fun gradient(at: Solution<T>): Gradient.Result =
                    scale(wf, f.gradient(at))
            }
        } else {
            object : DifferentiableObjective<T> {
                override fun invoke(solution: Solution<T>): Double =
                    objectives.zip(w).sumOf { (fi, wi) -> wi * fi(solution) }

                override fun gradient(at: Solution<T>): Gradient.Result {
                    TODO()
                }
            }
        }
    }
}
