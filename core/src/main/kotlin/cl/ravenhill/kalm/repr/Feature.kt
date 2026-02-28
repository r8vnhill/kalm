/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.repr

public interface Feature<T, F> where F : Feature<T, F> {

    public fun map(f: (T) -> T): F

    public fun <T2, F2> flatMap(f: (T) -> F2): F2 where F2 : Feature<T2, F2>
}
