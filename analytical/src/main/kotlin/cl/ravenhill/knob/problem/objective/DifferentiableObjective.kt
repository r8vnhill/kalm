/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.problem.objective

import cl.ravenhill.knob.repr.Gradient
import cl.ravenhill.knob.repr.Solution


public interface DifferentiableObjective<T> : Objective<T> {
    public fun gradient(at: Solution<T>): Gradient.Result

    public companion object {
        public operator fun <T> invoke(
            objective: Objective<T>,
            gradient: (at: Solution<T>) -> Gradient.Result
        ): DifferentiableObjective<T> =
            Impl(objective, gradient)
    }

    private class Impl<T>(
        private val objective: Objective<T>,
        private val computeGradient: (at: Solution<T>) -> Gradient.Result
    ) : DifferentiableObjective<T> {
        override fun gradient(at: Solution<T>): Gradient.Result =
            computeGradient(at)

        override fun invoke(solution: Solution<T>): Double =
            objective(solution)
    }
}

public fun <T> Objective<T>.toDifferentiable(
    gradient: (at: Solution<T>) -> Gradient.Result
): DifferentiableObjective<T> =
    DifferentiableObjective(this, gradient)
