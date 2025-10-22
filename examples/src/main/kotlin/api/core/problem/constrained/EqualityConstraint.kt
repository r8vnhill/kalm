/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package api.core.problem.constrained

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.recover
import cl.ravenhill.knob.repr.Solution
import cl.ravenhill.knob.exceptions.InvalidThresholdException
import cl.ravenhill.knob.problem.constrained.EqualityConstraint
import cl.ravenhill.knob.utils.EqualityThreshold

// Uses a context-provided threshold to build and evaluate an equality constraint.
context(threshold: Either<InvalidThresholdException, EqualityThreshold>)
private fun <T> example1(
    f: (Solution<T>) -> Double, // Left-hand side expression
    g: (Solution<T>) -> Double, // Right-hand side expression
    sol: Solution<T>            // Candidate solution to evaluate
) {
    EqualityConstraint(left = f, right = g)
        .getOrNull() // Gets the constraint only if the threshold is valid (Right)
        ?.invoke(sol)  // Evaluates the constraint: |f(sol) - g(sol)| <= threshold
        ?.let { result ->
            println("Constraint satisfied: $result") // Prints true/false
        }
}

// Uses Arrow's `either` block to safely create and evaluate an equality constraint
private fun <T> example2(
    f: (Solution<T>) -> Double,
    g: (Solution<T>) -> Double,
    sol: Solution<T>
): Either<InvalidThresholdException, Unit> = either {
    // Attempts to construct the constraint with the default threshold
    val constraint = EqualityConstraint.withDefaultThreshold(
        left = f,
        right = g
    ).bind() // Fails early if the threshold is invalid (this should not happen with the default)

    // Evaluate the constraint with the given solution
    val result = constraint(sol)

    // Output result
    println("Constraint satisfied: $result")
}

// Manually implements an EqualityConstraint using a directly provided threshold
private fun <T> example3(
    f: (Solution<T>) -> Double,
    g: (Solution<T>) -> Double,
    sol: Solution<T>,
    threshold: EqualityThreshold
) {
    // Direct instantiation of an anonymous implementation of EqualityConstraint
    val constraint = object : EqualityConstraint<T> {
        override val threshold: EqualityThreshold = threshold
        override val left: (Solution<T>) -> Double = f
        override val right: (Solution<T>) -> Double = g

        // Calls the default invoke implementation from the interface
        override fun invoke(solution: Solution<T>) = super.invoke(solution)
    }

    println("Constraint satisfied with custom threshold: ${constraint(sol)}")
}

fun main() {
    // Example 1: Uses context receiver to inject the threshold (default)
    with(EqualityThreshold.default) {
        example1(
            { it[0] + it[1] }, // f: sum of the first two elements
            { it[0] * it[1] }, // g: product of the first two elements
            Solution.of(1.0, 2.0) // solution
        )
    }

    // Example 2: Uses a safe functional block to create and evaluate a constraint
    example2(
        { it[0] },               // left side is 1.0
        { it[1] },               // right side is 1.0000000001 (within tolerance)
    Solution.of(1.0, 1.0000000001)
    ).recover<_, InvalidThresholdException, _> {
        // Handles any invalid threshold construction
        println("Error: ${it.message}")
    }

    // Example 3: Directly instantiates the constraint with a validated threshold
    either {
        example3(
            { it[0] + it[1] },    // sum
            { it[0] * it[1] },    // product
            Solution.of(1.0, 2.0),
            EqualityThreshold(1e-9).bind() // Validated creation
        )
    }.recover<_, InvalidThresholdException, _> {
        // Handles failure due to an invalid threshold
        println("Error: ${it.message}")
    }
}
