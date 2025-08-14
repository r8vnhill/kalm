/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.repr

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.either
import arrow.core.raise.ensure
import cl.ravenhill.keen.exceptions.GradientError
import cl.ravenhill.keen.util.size.SizeError
import cl.ravenhill.keen.util.size.Size
import cl.ravenhill.keen.util.size.UnsafeSizeCreation
import kotlin.math.sqrt


public class Gradient private constructor(private val components: DoubleArray) : Iterable<Double> {

    public val size: Size = Size.ofOrThrow(components.size) // Non-negative by construction, never throws

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

    public infix fun dot(other: Gradient): Either<GradientError.InvalidOperation, Double> = either {
        ensure(size == other.size) {
            GradientError.InvalidOperation(
                operation = "dot",
                self = this@Gradient,
                other = other,
                cause = SizeError.MatchingSizesExpected(size, other.size) // see note below
            )
        }
        var s = 0.0
        val a = components
        val b = other.components
        for (i in a.indices) {
            s += a[i] * b[i]
        }
        s
    }

    public operator fun times(alpha: Double): Gradient =
        Gradient(DoubleArray(size.toInt()) { alpha * components[it] })

    public fun safeDiv(alpha: Double): Either<GradientError.InvalidOperation, Gradient> = either {
        ensure(alpha != 0.0) {
            GradientError.InvalidOperation(
                operation = "div",
                self = this@Gradient,
                other = alpha,
                cause = ArithmeticException("Division by zero")
            )
        }
        Gradient(DoubleArray(size.toInt()) { components[it] / alpha })
    }

    public operator fun div(alpha: Double): Gradient =
        Gradient(DoubleArray(size.toInt()) { components[it] / alpha })

    public operator fun plus(other: Gradient): Either<GradientError.InvalidOperation, Gradient> = either {
        ensure(size == other.size) {
            GradientError.InvalidOperation(
                "plus", this@Gradient, other,
                SizeError.MatchingSizesExpected(size, other.size)
            )
        }
        val a = components
        val b = other.components
        Gradient(DoubleArray(size.toInt()) { i -> a[i] + b[i] })
    }

    public operator fun minus(other: Gradient): Either<GradientError.InvalidOperation, Gradient> = either {
        ensure(size == other.size) {
            GradientError.InvalidOperation(
                "minus", this@Gradient, other,
                SizeError.MatchingSizesExpected(size, other.size)
            )
        }
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
    ): Either<GradientError.InvalidOperation, Gradient> = either {
        ensure(size == other.size) {
            GradientError.InvalidOperation(
                "zipWith", this@Gradient, other,
                SizeError.MatchingSizesExpected(size, other.size)
            )
        }
        val a = components
        val b = other.components
        Gradient(DoubleArray(size.toInt()) { i -> combine(a[i], b[i]) })
    }

    //#endregion

    //#region std overrides

    override fun iterator(): DoubleIterator = components.iterator()

    override fun toString(): String = "Gradient(${components.contentToString()})"

    override fun equals(other: Any?): Boolean =
        this === other || (other is Gradient && components.contentEquals(other.components))

    override fun hashCode(): Int = components.contentHashCode()

    //#endregion

    //#region factories

    public companion object {

        @JvmStatic
        public operator fun invoke(components: NonEmptyList<Double>): Gradient =
            Gradient(components.toDoubleArray())

        @JvmStatic
        public fun of(component: Double, vararg components: Double): Gradient =
            Gradient(doubleArrayOf(component, *components))

        @JvmStatic
        public fun fromArray(components: DoubleArray): Either<SizeError.StrictlyPositiveExpected, Gradient> = either {
            ensure(components.isNotEmpty()) { SizeError.StrictlyPositiveExpected(0) }
            Gradient(components.copyOf())
        }

        /** Unsafe: takes ownership of the provided array without copying. */
        @JvmStatic
        @UnsafeSizeCreation
        public fun unsafeFromOwnedArray(components: DoubleArray): Gradient =
            Gradient(components)

        @JvmStatic
        public fun zeros(size: Size): Either<SizeError.StrictlyPositiveExpected, Gradient> = either {
            ensure(size > Size.zero) { SizeError.StrictlyPositiveExpected(size.toInt()) }
            Gradient(DoubleArray(size.toInt()))
        }

        @JvmStatic
        public fun fill(size: Size, value: Double): Either<SizeError.StrictlyPositiveExpected, Gradient> = either {
            ensure(size > Size.zero) { SizeError.StrictlyPositiveExpected(size.toInt()) }
            Gradient(DoubleArray(size.toInt()) { value })
        }
    }

    //#endregion
}

public operator fun Double.times(g: Gradient): Gradient = g * this
