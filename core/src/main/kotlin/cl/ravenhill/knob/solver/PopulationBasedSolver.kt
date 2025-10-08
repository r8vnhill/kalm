/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.solver

import cl.ravenhill.knob.problem.Problem
import cl.ravenhill.knob.repr.Population

public interface PopulationBasedSolver<T, P : Problem<T>> : Solver<T, P> {
    public val population: Population<T>
}
