/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.solver

import cl.ravenhill.knob.problem.GradientAwareProblem

public interface AnalyticalSolver<T> : Solver<T, GradientAwareProblem<T>>
