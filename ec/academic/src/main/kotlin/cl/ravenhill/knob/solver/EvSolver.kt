/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

/*
 * Academic / illustrative EV solver façade
 */

package cl.ravenhill.knob.solver

import cl.ravenhill.knob.problem.Problem
import cl.ravenhill.knob.repr.Solution

/**
 * Didactic EV-style solver interface used for experiments, comparisons, and tutorials.
 * Not intended for production deployments.
 */
public fun interface EvSolver<T, P : Problem<T>> : Solver<T, P>
