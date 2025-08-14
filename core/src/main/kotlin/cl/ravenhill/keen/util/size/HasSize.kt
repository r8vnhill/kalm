/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.util.size

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

public infix fun HasSize.sameSizeAs(other: HasSize): Boolean =
    this.size == other.size

public fun HasSize.requireSameSize(
    other: HasSize
): Either<SizeError, HasSize> = either {
    ensure(other.size == size) {
        SizeError.MatchingSizesExpected(other.size, size)
    }
    this@requireSameSize
}

@Throws(SizeError.MatchingSizesExpected::class)
public fun HasSize.checkSameSizeOrThrow(other: HasSize) {
    if (this.size != other.size) {
        throw SizeError.MatchingSizesExpected(
            expected = this.size,
            actual = other.size
        )
    }
}
