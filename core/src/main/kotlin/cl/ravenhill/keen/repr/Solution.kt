/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.repr

import cl.ravenhill.keen.repr.Solution.Companion.invoke
import cl.ravenhill.keen.problem.Objective
import cl.ravenhill.keen.problem.constrained.Constraint

/**
 * Represents a candidate solution in an optimization problem.
 *
 * A [Solution] is a read-only, ordered collection of values of type [T], typically used to represent:
 * - Decision variables in a mathematical model.
 * - Genes in an evolutionary algorithm.
 * - Parameters in a machine learning pipeline.
 *
 * It extends [List] to provide indexed access and iteration, making it easy to manipulate solutions with standard
 * Kotlin collection utilities while maintaining type safety and immutability.
 *
 * This abstraction is central to the framework: [Objective] functions, [Constraint]s, and operators all operate on
 * [Solution]s.
 * You can construct solutions using the provided [invoke] factory methods, or define your own implementations if custom
 * behavior is needed.
 *
 * >[!note]
 * > If you need a custom implementation, prefer [delegation](https://kotlinlang.org/docs/delegation.html) to avoid
 * > re-implementing list behavior.
 *
 * ## Examples
 *
 * ### Example 1: Creating a default solution
 * ```kotlin
 * val solution = Solution(1.0, 2.0, 3.0)
 * println(solution[0]) // Output: 1.0
 * println(solution.sum()) // Output: 6.0 (works as a List)
 * ```
 *
 * ### Example 2: Evaluating an objective function
 * ```kotlin
 * val solution = Solution(1.0, 2.0, 3.0)
 * val objective: Objective<Double> = Objective { it.sum() }
 * println(objective(solution)) // Output: 6.0
 * ```
 *
 * ### Example 3: Defining a custom solution type
 * ```kotlin
 * class BinarySolution(private val bits: List<Boolean>) :
 *         Solution<Boolean>, List<Boolean> by bits {
 *     fun asBitString(): String = joinToString("") { if (it) "1" else "0" }
 * }
 * val binary = BinarySolution(listOf(true, false, true))
 * println(binary.asBitString()) // Output: "101"
 * ```
 *
 * @param T The type of values stored in the solution.
 * @see DelegatedSolution
 */
public interface Solution<T> : List<T> {

    public companion object {
        /**
         * Creates a [Solution] from a list of values.
         *
         * @param values The list of values to wrap.
         * @return A [Solution] instance.
         */
        public operator fun <T> invoke(values: List<T>): Solution<T> = DelegatedSolution(values)

        /**
         * Creates a [Solution] from a variable number of values.
         *
         * @param values The values to wrap.
         * @return A [Solution] instance.
         */
        public operator fun <T> invoke(vararg values: T): Solution<T> = invoke(values.toList())
    }
}

/**
 * Internal implementation of [Solution] that delegates all operations to an underlying [List].
 *
 * This class provides a minimal, read-only implementation for use by [Solution.invoke].
 * It avoids re-implementing list behavior by using Kotlin's delegation mechanism.
 *
 * ## Note on `JavaDefaultMethodsNotOverriddenByDelegation`:
 *
 * Kotlin delegation does not override Java default methods like `toArray(IntFunction)` from [java.util.List], which may
 * lead to warnings such as:
 *
 * ```
 * JavaDefaultMethodsNotOverriddenByDelegation
 * ```
 *
 * In this case, the affected method is deprecated and rarely used in Kotlin, so the warning can safely be suppressed.
 *
 * @param T The type of elements stored in the delegated list.
 */
@Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
private class DelegatedSolution<T>(values: List<T>) : Solution<T>, List<T> by values
