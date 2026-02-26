/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.operators.producers

import cl.ravenhill.kalm.operators.CovariantOperator
import cl.ravenhill.knob.repr.Solution
import kotlin.random.Random

/**
 * A covariant operator that produces random solutions within specified bounds.
 *
 * Demonstrates the producer pattern where the operator creates new solution instances
 * without consuming any input. The covariance (`out T`) allows this operator to be
 * used polymorphically wherever a less specific solution type is expected.
 *
 * ## Variance Benefit Example:
 * ```kotlin
 * val doubleProducer: CovariantOperator<Solution<Double>> = RandomSolutionProducer(...)
 * val anyProducer: CovariantOperator<Solution<*>> = doubleProducer  // ✓ Safe due to covariance
 * ```
 *
 * @param T The type of elements in the produced solution.
 * @param dimension The number of elements in each solution.
 * @param lowerBound The minimum value for each element.
 * @param upperBound The maximum value for each element.
 * @param random The random number generator to use.
 */
class RandomSolutionProducer<T>(
    private val dimension: Int,
    private val lowerBound: T,
    private val upperBound: T,
    private val random: Random = Random.Default
) : CovariantOperator<Solution<T>> where T : Number, T : Comparable<T> {
    
    override fun produce(): Solution<T> {
        val values = when (lowerBound) {
            is Double -> List(dimension) {
                @Suppress("UNCHECKED_CAST")
                (lowerBound.toDouble() + random.nextDouble() * 
                    (upperBound.toDouble() - lowerBound.toDouble())) as T
            }
            is Float -> List(dimension) {
                @Suppress("UNCHECKED_CAST")
                (lowerBound.toFloat() + random.nextFloat() * 
                    (upperBound.toFloat() - lowerBound.toFloat())) as T
            }
            is Int -> List(dimension) {
                @Suppress("UNCHECKED_CAST")
                (lowerBound.toInt() + random.nextInt(
                    upperBound.toInt() - lowerBound.toInt() + 1)) as T
            }
            is Long -> List(dimension) {
                @Suppress("UNCHECKED_CAST")
                (lowerBound.toLong() + random.nextLong(
                    upperBound.toLong() - lowerBound.toLong() + 1)) as T
            }
            else -> error("Unsupported number type: ${lowerBound::class}")
        }
        
        return Solution.of(values.first(), *values.drop(1).toTypedArray())
    }
}
