/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.repr

import arrow.core.NonEmptyList

/**
 * A non-empty, read-only solution vector used across KNOB.
 *
 * This interface extends [List] for natural integration with Kotlin collection utilities, while enforcing non-emptiness
 * through companion factories.
 *
 * Implementations of [Solution] apply the delegation pattern: instead of re-implementing [List] operations, they
 * forward all calls to a backing [NonEmptyList]. This keeps the implementation concise, avoids duplication, and
 * guarantees consistency with the semantics of [NonEmptyList].
 *
 * @param T Element type stored in the solution vector.
 */
public interface Solution<out T> : List<T> {

    /**
     * Returns the underlying [NonEmptyList] view of this solution.
     *
     * The default implementation in KNOB returns the backing instance without copying.
     */
    public fun toNonEmptyList(): NonEmptyList<T>

    public companion object {

        /**
         * Creates a [Solution] backed by the given [NonEmptyList].
         *
         * @param values Backing non-empty sequence for this solution.
         */
        @JvmStatic
        public operator fun <T> invoke(values: NonEmptyList<T>): Solution<T> =
            DelegatedSolution(values)

        /**
         * Creates a [Solution] from a mandatory head element and an optional tail.
         *
         * Internally constructs a [NonEmptyList] to guarantee non-emptiness.
         *
         * @param head First element (ensures non-emptiness).
         * @param tail Optional remaining elements.
         */
        @JvmStatic
        public fun <T> of(head: T, vararg tail: T): Solution<T> =
            DelegatedSolution(NonEmptyList(head, tail.asList()))
    }
}

/**
 * Concrete implementation of [Solution] that delegates all [List] operations to a backing [NonEmptyList] using Kotlin’s
 * delegation syntax (`by`).
 *
 * This is an application of the delegation pattern: behavior is reused from the wrapped [NonEmptyList], ensuring
 * correctness and reducing boilerplate. The class remains lightweight, with no copying of elements.
 *
 * @property values Backing non-empty list. Kept private to preserve invariants.
 */
private class DelegatedSolution<T>(
    private val values: NonEmptyList<T>
) : Solution<T>, List<T> by values {

    override fun toNonEmptyList(): NonEmptyList<T> = values

    // equals/hashCode/toString are not delegated by `by`, so they are overridden explicitly.

    override fun equals(other: Any?): Boolean =
        this === other || (other is Solution<*> && values == other.toNonEmptyList())

    override fun hashCode(): Int = values.hashCode()

    override fun toString(): String = "Solution(values=$values)"
}
