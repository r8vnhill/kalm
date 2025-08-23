/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.repr

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.either
import arrow.core.raise.ensure
import cl.ravenhill.knob.exceptions.GradientError
import cl.ravenhill.knob.jvm.VectorOps
import cl.ravenhill.knob.repr.Gradient.Companion.fill
import cl.ravenhill.knob.repr.Gradient.Companion.fromArray
import cl.ravenhill.knob.repr.Gradient.Companion.unsafeFromOwnedArray
import cl.ravenhill.knob.repr.Gradient.Companion.zeros
import cl.ravenhill.knob.utils.size.HasSize
import cl.ravenhill.knob.utils.size.Size
import cl.ravenhill.knob.utils.size.SizeError
import cl.ravenhill.knob.utils.size.UnsafeSizeCreation
import cl.ravenhill.knob.utils.size.requireSameSize
import kotlin.math.sqrt


public class Gradient private constructor(private val components: DoubleArray) : Iterable<Double>, HasSize {

    public override val size: Size = Size.ofOrThrow(components.size) // Non-negative by construction, never throws

    //#region element access

    public operator fun get(index: Int): Double = components[index]

    public fun getOrNull(index: Int): Double? = components.getOrNull(index)

    public fun getOrElse(index: Int, default: (Int) -> Double): Double =
        if (index in components.indices) components[index] else default(index)

    //#endregion

    //#region conversions

    public fun toDoubleArray(): DoubleArray = components.copyOf()

    public fun toList(): List<Double> = components.asList()

    //#endregion

    //#region metrics

    public fun squaredL2Norm(): Double {
        var s = 0.0
        val a = components
        for (i in a.indices) {
            val x = a[i]
            s += x * x
        }
        return s
    }

    public fun l2Norm(): Double = sqrt(squaredL2Norm())

    //#endregion

    //#region algebraic ops

    public infix fun dot(other: Gradient): Either<GradientError.InvalidOperation, Double> =
        computeDotProduct(other) { components dotProduct other.components }

    public infix fun dotKahan(other: Gradient): Either<GradientError.InvalidOperation, Double> =
        computeDotProduct(other) { components dotProduct other.components }

    private fun computeDotProduct(other: Gradient, op: VectorOps.() -> Double) =
        requireSameSize(other)
            .mapLeft { GradientError.InvalidOperation("dot", this@Gradient, other, cause = it) }
            .map { VectorOps.run(op) }

    public operator fun times(alpha: Double): Gradient =
        Gradient(DoubleArray(size.toInt()) { alpha * components[it] })

    public fun safeDiv(alpha: Double): Either<GradientError.InvalidOperation, Gradient> = either {
        ensure(alpha != 0.0) {
            GradientError.InvalidOperation(
                "div", this@Gradient, alpha, ArithmeticException("Division by zero")
            )
        }
        Gradient(DoubleArray(size.toInt()) { components[it] / alpha })
    }

    public operator fun div(alpha: Double): Gradient =
        Gradient(DoubleArray(size.toInt()) { components[it] / alpha })

    public operator fun plus(other: Gradient): Either<GradientError.InvalidOperation, Gradient> =
        requireSameSize(other)
            .mapLeft { GradientError.InvalidOperation("plus", this@Gradient, other, it) }
            .map {
                val a = components
                val b = other.components
                Gradient(DoubleArray(size.toInt()) { i -> a[i] + b[i] })
            }

    public operator fun minus(other: Gradient): Either<GradientError.InvalidOperation, Gradient> =
        requireSameSize(other)
            .mapLeft { GradientError.InvalidOperation("minus", this@Gradient, other, it) }
            .map {
                val a = components
                val b = other.components
                Gradient(DoubleArray(size.toInt()) { i -> a[i] - b[i] })
            }

    public operator fun unaryMinus(): Gradient =
        Gradient(DoubleArray(size.toInt()) { -components[it] })

    //#endregion

    //#region higher-order ops

    public fun map(transform: (Double) -> Double): Gradient =
        Gradient(DoubleArray(size.toInt()) { i -> transform(components[i]) })

    public fun zipWith(
        other: Gradient,
        combine: (Double, Double) -> Double
    ): Either<GradientError.InvalidOperation, Gradient> =
        requireSameSize(other)
            .mapLeft { GradientError.InvalidOperation("zipWith", this@Gradient, other, it) }
            .map {
                val a = components
                val b = other.components
                Gradient(DoubleArray(size.toInt()) { i -> combine(a[i], b[i]) })
            }

    //#endregion

    //#region std overrides

    override fun iterator(): DoubleIterator =
        components.iterator()

    override fun toString(): String =
        "Gradient(${components.contentToString()})"

    override fun equals(other: Any?): Boolean =
        this === other || (other is Gradient && components.contentEquals(other.components))

    override fun hashCode(): Int = components.contentHashCode()

    //#endregion

    //#region factories

    public companion object {

        /**
         * Creates a [Gradient] from a [NonEmptyList] of [Double] values.
         *
         * @param components The non-empty list of gradient components.
         * @return A [Gradient] with the given components.
         */
        @JvmStatic
        public operator fun invoke(components: NonEmptyList<Double>): Gradient =
            Gradient(components.toDoubleArray())

        /**
         * Creates a [Gradient] from a first component and an arbitrary number of additional components.
         *
         * @param component The first gradient component.
         * @param components Additional gradient components.
         * @return A [Gradient] containing the provided components.
         */
        @JvmStatic
        public fun of(component: Double, vararg components: Double): Gradient =
            Gradient(doubleArrayOf(component, *components))

        /**
         * Builds a [Gradient] by **defensively copying** the given array of components.
         *
         * Requires a **strictly positive** number of elements. If the array is empty, it returns a typed error so
         * callers can handle it functionally.
         *
         * ## Usage:
         * ```kotlin
         * val ok = Gradient.fromArray(doubleArrayOf(1.0, 2.0, 3.0)) // Right(Gradient)
         * val bad = Gradient.fromArray(doubleArrayOf())             // Left(StrictlyPositiveExpected(0))
         * ```
         *
         * ## Ownership note
         * This method **copies** [components]. If you need to **take ownership** without copying (and accept aliasing),
         * use [unsafeFromOwnedArray].
         *
         * @param components The input [DoubleArray] of components.
         * @return `Right(Gradient)` when `components.isNotEmpty()`, otherwise
         *   `Left(SizeError.StrictlyPositiveExpected(components.size))`.
         */
        @JvmStatic
        public fun fromArray(
            components: DoubleArray
        ): Either<SizeError.StrictlyPositiveExpected, Gradient> = either {
            ensure(components.isNotEmpty()) { SizeError.StrictlyPositiveExpected(components.size) }
            Gradient(components.copyOf())
        }

        /**
         * **Unsafe:** builds a [Gradient] by **taking ownership** of [components] **without copying**.
         *
         * The returned instance **aliases** the given array:
         * - Any subsequent mutation of [components] will be **observed** by the [Gradient].
         * - Mutating the [Gradient]’s internal storage (e.g., through other unsafe APIs) will also mutate [components].
         *
         * Use this only in hot paths where you control the input lifecycle and need to avoid the defensive copy
         * performed by [fromArray]. Prefer [fromArray] for general use.
         *
         * ## Preconditions & Notes:
         * - The array length may be zero (size = 0).
         *   If your domain requires a strictly positive size, validate before calling or use [fill]/[zeros], which
         *   enforce positivity.
         * - The caller is responsible for ensuring **exclusive ownership** of [components] after this call.
         *
         * @param components The array to be **adopted** as this [Gradient]’s backing storage. **Not copied.**
         * @return A [Gradient] that **aliases** [components].
         */
        @JvmStatic
        @UnsafeSizeCreation
        public fun unsafeFromOwnedArray(components: DoubleArray): Gradient =
            Gradient(components)

        /**
         * Creates a [Gradient] filled with zeros, of the given size.
         *
         * @param size The size of the gradient; must be strictly positive.
         * @return `Right(Gradient)` if the size is positive, otherwise
         *   `Left(SizeError.StrictlyPositiveExpected(size.toInt()))`.
         */
        @JvmStatic
        public fun zeros(size: Size): Either<SizeError.StrictlyPositiveExpected, Gradient> = either {
            ensure(size > Size.zero) { SizeError.StrictlyPositiveExpected(size.toInt()) }
            Gradient(DoubleArray(size.toInt()))
        }

        /**
         * Creates a [Gradient] filled with the given [value], of the given size.
         *
         * @param size The size of the gradient; must be strictly positive.
         * @param value The value to fill each component with.
         * @return `Right(Gradient)` if the size is positive, otherwise
         *   `Left(SizeError.StrictlyPositiveExpected(size.toInt()))`.
         */
        @JvmStatic
        public fun fill(size: Size, value: Double): Either<SizeError.StrictlyPositiveExpected, Gradient> = either {
            ensure(size > Size.zero) { SizeError.StrictlyPositiveExpected(size.toInt()) }
            Gradient(DoubleArray(size.toInt()) { value })
        }
    }

    //#endregion
}

public operator fun Double.times(g: Gradient): Gradient = g * this
