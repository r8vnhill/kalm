/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.repr

import cl.ravenhill.kalm.NonEmptyList

public interface Solution<out T> {
    public fun toNonEmptyList(): NonEmptyList<T>

    public companion object {
        @JvmStatic
        public operator fun <T> invoke(values: NonEmptyList<T>): Solution<T> = object : Solution<T> {
            override fun toNonEmptyList(): NonEmptyList<T> = values
        }

        @JvmStatic
        public fun <T> of(head: T, vararg tail: T): Solution<T> =
            invoke(NonEmptyList(head, tail.toList()))
    }
}
