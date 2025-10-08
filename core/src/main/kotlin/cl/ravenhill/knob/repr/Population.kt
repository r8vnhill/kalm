/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.repr

import arrow.core.NonEmptyCollection
import arrow.core.nonEmptyListOf

public interface Population<T> : NonEmptyCollection<Solution<T>> {
    public companion object {
        @JvmStatic
        public operator fun <T> invoke(solutions: NonEmptyCollection<Solution<T>>): Population<T> =
            DelegatedPopulation(solutions)

        @JvmStatic
        public fun <T> of(solution: Solution<T>, vararg solutions: Solution<T>): Population<T> =
            Population(nonEmptyListOf(solution, *solutions))
    }
}

@Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
private class DelegatedPopulation<T>(
    private val values: NonEmptyCollection<Solution<T>>
) : Population<T>, NonEmptyCollection<Solution<T>> by values {

    // equals/hashCode/toString are not delegated by `by`, so they are overridden explicitly.

    override fun equals(other: Any?): Boolean =
        this === other || (other is Population<*> && values == other.toNonEmptyList())

    override fun hashCode(): Int = values.hashCode()

    override fun toString(): String = "Population($values)"
}
