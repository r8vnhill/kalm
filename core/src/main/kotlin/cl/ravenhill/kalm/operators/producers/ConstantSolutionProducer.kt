/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.operators.producers

import cl.ravenhill.kalm.operators.CovariantOperator
import cl.ravenhill.knob.repr.Solution

/**
 * A covariant operator that produces a constant solution.
 *
 * This operator always produces the same pre-defined solution, useful for:
 * - Testing and debugging
 * - Baseline comparisons
 * - Providing default or fallback solutions
 *
 * ## Covariance in Action:
 * The `out` variance modifier allows safe upcast assignment:
 * ```kotlin
 * class Dog : Animal
 * val dogConst: CovariantOperator<Solution<Dog>> = ConstantSolutionProducer(dogSolution)
 * val animalConst: CovariantOperator<Solution<Animal>> = dogConst  // ✓ Type-safe
 * ```
 *
 * This works because the operator only *produces* values, never consumes them.
 * It's always safe to treat a "Dog producer" as an "Animal producer".
 *
 * @param T The type parameter of the solution.
 * @param constantSolution The solution to return on every [produce] call.
 */
class ConstantSolutionProducer<out T : Solution<*>>(
    private val constantSolution: T
) : CovariantOperator<T> {
    
    override fun produce(): T = constantSolution
}
