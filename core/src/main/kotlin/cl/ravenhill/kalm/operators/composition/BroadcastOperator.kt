/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.operators.composition

import cl.ravenhill.kalm.operators.ContravariantOperator
import cl.ravenhill.knob.repr.Solution

/**
 * Composes multiple contravariant operators into a single operator that applies all of them.
 *
 * This broadcast pattern leverages contravariance to allow a more general consumer to
 * accept more specific solution types:
 *
 * ## Contravariance in Composition:
 * ```kotlin
 * val validator: ContravariantOperator<Solution<Number>> = ...
 * val logger: ContravariantOperator<Solution<Number>> = ...
 * val collector: ContravariantOperator<Solution<Number>> = ...
 * 
 * val broadcast = BroadcastOperator(listOf(validator, logger, collector))
 * 
 * // Can consume more specific types thanks to contravariance
 * val intBroadcast: ContravariantOperator<Solution<Int>> = broadcast  // ✓
 * ```
 *
 * @param T The type of solution this broadcaster can consume.
 * @param consumers The list of operators to broadcast to.
 */
class BroadcastOperator<in T : Solution<*>>(
    private val consumers: List<ContravariantOperator<T>>
) : ContravariantOperator<T> {
    
    override fun consume(solution: T) {
        consumers.forEach { it.consume(solution) }
    }
    
    companion object {
        /**
         * Creates a broadcast operator from vararg consumers.
         */
        fun <T : Solution<*>> of(
            vararg consumers: ContravariantOperator<T>
        ): BroadcastOperator<T> = BroadcastOperator(consumers.toList())
    }
}

/**
 * Combines two contravariant operators into a broadcast operator.
 */
infix fun <T : Solution<*>> ContravariantOperator<T>.and(
    other: ContravariantOperator<T>
): BroadcastOperator<T> = BroadcastOperator.of(this, other)
