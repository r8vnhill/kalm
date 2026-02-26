/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.operators.consumers

import cl.ravenhill.kalm.operators.ContravariantOperator
import cl.ravenhill.knob.repr.Solution

/**
 * A contravariant operator that validates solutions against constraints.
 *
 * Demonstrates the consumer pattern where the operator processes solutions but
 * doesn't produce them. The contravariance (`in T`) allows this operator to accept
 * more specific solution types than originally declared.
 *
 * ## Contravariance Benefit Example:
 * ```kotlin
 * val animalValidator: ContravariantOperator<Solution<Animal>> = SolutionValidator(...)
 * val dogValidator: ContravariantOperator<Solution<Dog>> = animalValidator  // ✓ Safe
 * ```
 *
 * This reversal is safe because if you can validate any Animal, you can certainly
 * validate a specific Dog (which is an Animal). Contravariance on input types!
 *
 * @param T The type of solution this validator can consume.
 * @param predicate The validation predicate that returns true if the solution is valid.
 * @param onValidationFailure Callback invoked when validation fails.
 */
class SolutionValidator<in T : Solution<*>>(
    private val predicate: (T) -> Boolean,
    private val onValidationFailure: ((T) -> Unit)? = null
) : ContravariantOperator<T> {
    
    var lastValidationResult: Boolean = true
        private set
    
    override fun consume(solution: T) {
        lastValidationResult = predicate(solution)
        if (!lastValidationResult) {
            onValidationFailure?.invoke(solution)
        }
    }
    
    /**
     * Checks if the last consumed solution was valid.
     */
    fun isValid(): Boolean = lastValidationResult
}
