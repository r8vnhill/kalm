/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.operators.consumers

import cl.ravenhill.kalm.operators.ContravariantOperator
import cl.ravenhill.knob.repr.Solution

/**
 * A contravariant operator that collects statistics from consumed solutions.
 *
 * This operator demonstrates how consumers can aggregate information from multiple
 * solutions. The contravariant `in` modifier enables accepting more specific types:
 *
 * ## Contravariance Pattern:
 * ```kotlin
 * val generalCollector: ContravariantOperator<Solution<Number>> = StatisticsCollector()
 * val intCollector: ContravariantOperator<Solution<Int>> = generalCollector  // ✓ Safe
 * 
 * // We can feed Int solutions to a Number consumer
 * intCollector.consume(Solution.of(1, 2, 3))
 * ```
 *
 * @param T The type of solution this collector can consume.
 */
class StatisticsCollector<in T : Solution<*>> : ContravariantOperator<T> {
    
    private var _consumedCount: Int = 0
    private var _totalSize: Int = 0
    private val _sizeCounts = mutableMapOf<Int, Int>()
    
    /**
     * Number of solutions consumed so far.
     */
    val consumedCount: Int get() = _consumedCount
    
    /**
     * Total number of elements across all consumed solutions.
     */
    val totalSize: Int get() = _totalSize
    
    /**
     * Distribution of solution sizes.
     */
    val sizeCounts: Map<Int, Int> get() = _sizeCounts.toMap()
    
    /**
     * Average size of consumed solutions.
     */
    val averageSize: Double
        get() = if (_consumedCount > 0) _totalSize.toDouble() / _consumedCount else 0.0
    
    override fun consume(solution: T) {
        _consumedCount++
        val size = solution.size
        _totalSize += size
        _sizeCounts[size] = _sizeCounts.getOrDefault(size, 0) + 1
    }
    
    /**
     * Resets all collected statistics to initial state.
     */
    fun reset() {
        _consumedCount = 0
        _totalSize = 0
        _sizeCounts.clear()
    }
}
