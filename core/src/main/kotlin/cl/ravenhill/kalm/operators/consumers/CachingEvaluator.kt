/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.operators.consumers

import cl.ravenhill.kalm.eval.SolutionEvaluator
import cl.ravenhill.knob.repr.Solution

/**
 * A contravariant evaluator that caches objective function results.
 *
 * Demonstrates a practical use of [SolutionEvaluator] (which extends
 * [cl.ravenhill.kalm.operators.ContravariantOperator]) for memoization.
 *
 * ## Why Contravariant for Evaluators:
 * Evaluators only *consume* solutions to produce fitness values—they never
 * produce solutions themselves. This makes contravariance perfect:
 *
 * ```kotlin
 * val generalEvaluator: SolutionEvaluator<Solution<Number>> = CachingEvaluator(...)
 * val doubleEvaluator: SolutionEvaluator<Solution<Double>> = generalEvaluator  // ✓
 * ```
 *
 * @param T The type of solution this evaluator can consume.
 * @param objectiveFunction The function to evaluate solutions.
 */
class CachingEvaluator<in T : Solution<*>>(
    private val objectiveFunction: (T) -> Double
) : SolutionEvaluator<T> {
    
    private val cache = mutableMapOf<List<*>, Double>()
    
    var cacheHits: Int = 0
        private set
    
    var cacheMisses: Int = 0
        private set
    
    override fun consume(solution: T) {
        evaluate(solution)
    }
    
    /**
     * Evaluates the solution, using cached value if available.
     */
    fun evaluate(solution: T): Double {
        val key = solution.toList()
        return cache.getOrPut(key) {
            cacheMisses++
            objectiveFunction(solution)
        }.also {
            if (it == cache[key]) cacheHits++
        }
    }
    
    /**
     * Clears the evaluation cache.
     */
    fun clearCache() {
        cache.clear()
        cacheHits = 0
        cacheMisses = 0
    }
}
