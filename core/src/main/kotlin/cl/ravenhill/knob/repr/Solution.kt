/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.repr

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.toNonEmptyListOrNull
import cl.ravenhill.knob.utils.size.SizeError

public interface Solution<out T> {
    public fun toNonEmptyList(): NonEmptyList<T>

    public companion object {
        @JvmStatic
        public operator fun <T> invoke(values: NonEmptyList<T>): Solution<T> =
            DelegatedSolution(values)

        @JvmStatic
        public fun <T> of(head: T, vararg tail: T): Solution<T> =
            DelegatedSolution(nonEmptyListOf(head, *tail))

        @JvmStatic
        public fun <T> fromValidatedList(list: List<T>): Either<SizeError, Solution<T>> = either {
            ensure(list.isNotEmpty()) { SizeError.StrictlyPositiveExpected(list.size) }
            Solution(NonEmptyList(list.first(), list.drop(1)))
        }

        @JvmStatic
        public fun <T> fromListOrNull(list: List<T>): Solution<T>? =
            list.toNonEmptyListOrNull()?.let(::invoke)

        @JvmStatic
        public fun <T> fromListOrThrow(list: List<T>): Solution<T> =
            fromListOrNull(list) ?: throw SizeError.StrictlyPositiveExpected(list.size)

        @JvmStatic
        public fun <T> fromIterableOrNull(values: Iterable<T>): Solution<T>? =
            values.toList().toNonEmptyListOrNull()?.let(::invoke)

        @JvmStatic
        public fun <T> fromSequenceOrNull(values: Sequence<T>): Solution<T>? =
            values.firstOrNull()?.let { first ->
                val rest = values.drop(1).toList()
                Solution(NonEmptyList(first, rest))
            }
    }
}

private class DelegatedSolution<T>(
    private val values: NonEmptyList<T>
) : Solution<T> {
    override fun toNonEmptyList(): NonEmptyList<T> =
        values

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return when (other) {
            is Solution<*> -> values == other.toNonEmptyList()
            is List<*> -> values == other
            else -> false
        }
    }

    override fun hashCode(): Int =
        values.hashCode()

    override fun toString(): String =
        "Solution${values.joinToString(", ", "[", "]")}"
}
