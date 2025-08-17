/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.utils.size

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure

/**
 * Common contract for domain elements that expose a [Size].
 *
 * Keep this interface minimal and push behaviors into **extensions** so callers can reuse size-aware utilities across
 * types (e.g., `Gradient`, vectors, datasets).
 *
 * @property size The non-negative [Size] of the receiver.
 */
public interface HasSize {
    public val size: Size
}

/**
 * Checks whether this [HasSize] has the same [Size] as [other].
 *
 * ## Usage:
 * Suppose we have a `Vector` class that implements [HasSize]:
 * ```kotlin
 * val a: HasSize = Vector(1.0, 2.0, 3.0)
 * val b: HasSize = Vector(4.0, 5.0, 6.0)
 *
 * if (a sameSizeAs b) {
 *     println("Vectors have the same size.")
 * }
 * ```
 *
 * @receiver The first [HasSize] instance.
 * @param other Another [HasSize] instance to compare against.
 * @return `true` if both instances have equal [Size], `false` otherwise.
 */
public infix fun HasSize.sameSizeAs(other: HasSize): Boolean =
    this.size == other.size

/**
 * Ensures that this [HasSize] has the same [Size] as [other].
 *
 * This function performs a size equality check between the two receivers:
 * - If [HasSize.size] and `other.size` are equal, it returns `Right(this)` allowing fluent use in functional chains.
 * - If the sizes differ, it returns `Left(SizeError.MatchingSizesExpected)` containing the expected and actual sizes.
 *
 * It does **not** throw; use [checkSameSizeOrThrow] if you prefer exception-based error handling.
 *
 * ## Usage:
 * ```kotlin
 * val result = a requireSameSize b
 * result.fold(
 *     ifLeft = { err -> println("Size mismatch: $err") },
 *     ifRight = { println("Sizes match!") }
 * )
 * ```
 *
 * @receiver The first [HasSize] instance.
 * @param other Another [HasSize] instance to compare against.
 * @return An [Either] containing `Right(this)` if sizes match,
 *         or `Left(SizeError.MatchingSizesExpected)` if they differ.
 */
public infix fun HasSize.requireSameSize(
    other: HasSize
): Either<SizeError, HasSize> = either {
    ensure(other.size == size) {
        SizeError.MatchingSizesExpected(other.size, size)
    }
    this@requireSameSize
}

/**
 * Checks if this [HasSize] has the same size as another [HasSize], throwing an error if not.
 *
 * This infix function compares the `size` of both instances. If they differ, it throws a
 * [SizeError.MatchingSizesExpected] containing the expected and actual sizes.
 *
 * @param other The [HasSize] instance to compare against.
 * @throws SizeError.MatchingSizesExpected If the sizes are not equal.
 */
@Throws(SizeError.MatchingSizesExpected::class)
public infix fun HasSize.checkSameSizeOrThrow(other: HasSize) {
    if (this.size != other.size) {
        throw SizeError.MatchingSizesExpected(
            expected = this.size,
            actual = other.size
        )
    }
}
