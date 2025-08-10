/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.solver

import cl.ravenhill.keen.problem.Problem
import cl.ravenhill.keen.repr.Solution

public fun interface Solver<T> : (Problem<T>) -> Solution<T>
