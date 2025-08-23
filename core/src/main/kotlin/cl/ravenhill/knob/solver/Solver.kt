/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.solver

import cl.ravenhill.knob.problem.Problem
import cl.ravenhill.knob.repr.Solution

/**
 * A strategy that produces a [Solution] for a given [Problem].
 *
 * A [Solver] encapsulates the search or optimization logic needed to transform a problem definition into a candidate
 * solution.
 * It is a fun interface, so solver instances can be provided as lambdas or method references:
 *
 * ```kotlin
 * val solver = Solver { problem ->
 *     // ...solve `problem` and return a Solution<T>
 * }
 * ```
 *
 * @param T The type of the representation (genotype/phenotype) used by the problem.
 */
public fun interface Solver<T> : (Problem<T>) -> Solution<T>
