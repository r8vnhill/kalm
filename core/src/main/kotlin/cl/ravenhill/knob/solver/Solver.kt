/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.solver

import cl.ravenhill.knob.problem.Problem
import cl.ravenhill.knob.repr.Solution

/**
 * A strategy that maps a [Problem] to a feasible [Solution].
 *
 * This is the minimal, functional contract for optimization/search in KNOB. It is a Kotlin `fun interface`, so
 * implementations can be provided via lambdas, method references, or concrete classes. The type is also a function
 * type, i.e. `(P) -> Solution<T>`.
 *
 * ## Contract (guidelines):
 * - Input: a problem [P] describing objectives (and optional constraints).
 * - Output: a candidate [Solution] for the problem's domain type [T]. When exact optimality is not guaranteed, return
 *   the best-known solution under the solver's stopping criteria.
 * - Determinism: solvers may be stochastic. Prefer seeded RNGs to enable reproducible runs in tests, benchmarks, and
 *   scientific comparisons.
 * - Side effects: avoid mutating inputs; keep the solver state internal. Favor pure APIs where feasible to improve
 *   testability and composability.
 *
 * ## Basic usage:
 * ```kotlin
 * val solver = Solver<Double, Problem<Double>> { problem ->
 *     // implement the search/optimization here
 *     // return a Solution<Double>
 * }
 * val best: Solution<Double> = solver(problem)
 * ```
 *
 * Decorators and composition:
 * - Because [Solver] is a function type, it can be wrapped to add logging, timing, or telemetry without changing the
 *   core algorithm.
 * - Domain-specific solvers may live under separate modules (e.g., `:ec:production` for production-ready algorithms and
 *   `:ec:academic` for illustrative ones) and still conform to this interface.
 *
 * @param T The representation type carried by [Solution] (genotype/phenotype/domain value).
 * @param P A subtype of [Problem] whose solutions are of type [T].
 */
public fun interface Solver<T, P : Problem<T>> : (P) -> Solution<T>
