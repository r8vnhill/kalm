/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.operators.composition

import cl.ravenhill.kalm.operators.InvariantOperator
import cl.ravenhill.knob.repr.Solution

/**
 * Chains multiple invariant operators into a sequential transformation pipeline.
 *
 * Unlike covariant/contravariant operators which allow flexible type relationships,
 * invariant operators require exact type matching. However, they enable powerful
 * transformation chains where each step both consumes and produces solutions.
 *
 * ## Invariance Trade-off:
 * ```kotlin
 * val mutator1: InvariantOperator<Solution<Double>> = ...
 * val mutator2: InvariantOperator<Solution<Double>> = ...
 * 
 * val chain = OperatorChain.of(mutator1, mutator2)
 * 
 * chain.consume(initialSolution)  // Applies mutator1 then mutator2
 * val result = chain.produce()     // Gets the final transformed solution
 * ```
 *
 * Note: Unlike covariant or contravariant operators, you cannot substitute
 * `InvariantOperator<Solution<Double>>` with `InvariantOperator<Solution<Number>>`
 * because invariant types require exact matches.
 *
 * @param T The exact solution type (must match across all operators in the chain).
 * @param operators The sequence of transformations to apply.
 */
class OperatorChain<T : Solution<*>>(
    private val operators: List<InvariantOperator<T>>
) : InvariantOperator<T> {
    
    init {
        require(operators.isNotEmpty()) { "Operator chain cannot be empty" }
    }
    
    private var currentSolution: T? = null
    
    override fun consume(solution: T) {
        currentSolution = operators.fold(solution) { acc, operator ->
            operator.consume(acc)
            operator.produce()
        }
    }
    
    override fun produce(): T = currentSolution
        ?: error("No solution has been consumed yet. Call consume() before produce().")
    
    companion object {
        /**
         * Creates an operator chain from vararg operators.
         */
        fun <T : Solution<*>> of(
            vararg operators: InvariantOperator<T>
        ): OperatorChain<T> = OperatorChain(operators.toList())
    }
}

/**
 * Chains two invariant operators sequentially.
 */
infix fun <T : Solution<*>> InvariantOperator<T>.andThen(
    other: InvariantOperator<T>
): OperatorChain<T> = OperatorChain.of(this, other)
