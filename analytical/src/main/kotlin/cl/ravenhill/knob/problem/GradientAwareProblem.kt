/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.problem

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import cl.ravenhill.knob.problem.constrained.Constraint
import cl.ravenhill.knob.problem.objective.DifferentiableObjective
import cl.ravenhill.knob.utils.size.UnsafeSizeCreation

/**
 * A problem whose objectives are differentiable.
 *
 * Refines [Problem] by constraining [objectives] to [DifferentiableObjective]s, enabling gradient-based solvers (e.g.,
 * steepest descent, quasi-Newton) and hybrid strategies.
 *
 * ## Performance notes
 * - **Objectives**: the provided [arrow.core.NonEmptyList] is stored **as-is** (no copy).
 * - **Constraints**: stored **as-is** (no defensive copy) to avoid allocations. If external mutation is a concern,
 *   callers should pass an immutable collection.
 * - **Vararg path:** `of(head, vararg tail)` uses the JVM vararg array; building the `NonEmptyList` from it avoids
 *   extra per-element copies. Construction is O(k) over the number of tail elements.
 * - **Iterable path:** `unsafeFromIterable` performs single materialization to a `List` and one `NonEmptyList`
 *   allocation; it validates non-emptiness once (O(n)).
 *
 * ## Usage:
 * ```kotlin
 * val f: DifferentiableObjective<Double> = ...
 * val g: DifferentiableObjective<Double> = ...
 *
 * // Multiple objectives
 * val p1: GradientAwareProblem<Double> =
 *   GradientAwareProblem(nonEmptyListOf(f, g))
 *
 * // Single objective
 * val p2: GradientAwareProblem<Double> =
 *   GradientAwareProblem.of(f)
 *
 * // Vararg objectives
 * val p3: GradientAwareProblem<Double> =
 *   GradientAwareProblem.of(f, g)
 * ```
 *
 * @param T Element type of the solution representation handled by the objectives/constraints.
 */
public interface GradientAwareProblem<T> : Problem<T> {

    /**
     * Differentiable objective functions; must be non-empty.
     *
     * Stored as provided; no defensive copy is performed.
     */
    override val objectives: NonEmptyList<DifferentiableObjective<T>>

    public companion object {
        /**
         * Creates a problem from a non-empty list of differentiable objectives.
         *
         * The [objectives] reference is stored as-is (no copy). The same applies to [constraints].
         *
         * ## Usage:
         * ```kotlin
         * val p = GradientAwareProblem(
         *   objectives = nonEmptyListOf(f, g),
         *   constraints = listOf(eqConstraint, boundConstraint)
         * )
         * ```
         *
         * @param objectives Non-empty list of differentiable objectives (stored as-is).
         * @param constraints Optional constraints (stored as-is).
         */
        @JvmStatic
        public operator fun <T> invoke(
            objectives: NonEmptyList<DifferentiableObjective<T>>,
            constraints: Collection<Constraint<T>> = emptyList()
        ): GradientAwareProblem<T> =
            Impl(objectives, constraints)

        /**
         * Creates a single-objective problem.
         *
         * Both [objective] and [constraints] are stored as-is (no copies).
         *
         * @param objective Differentiable objective.
         * @param constraints Optional constraints (stored as-is).
         */
        @JvmStatic
        public fun <T> of(
            objective: DifferentiableObjective<T>,
            constraints: Collection<Constraint<T>> = emptyList()
        ): GradientAwareProblem<T> =
            Impl(nonEmptyListOf(objective), constraints)

        /**
         * Creates a problem from a head objective and additional objectives.
         *
         * Internally builds a [NonEmptyList] from the JVM vararg array; no per-element copies beyond constructing the
         * non-empty structure. [constraints] are stored as-is.
         *
         * @param head First differentiable objective (ensures non-emptiness).
         * @param tail Additional differentiable objectives.
         * @param constraints Optional constraints (stored as-is).
         */
        @JvmStatic
        public fun <T> of(
            head: DifferentiableObjective<T>,
            vararg tail: DifferentiableObjective<T>,
            constraints: Collection<Constraint<T>> = emptyList()
        ): GradientAwareProblem<T> =
            Impl(nonEmptyListOf(head, *tail), constraints)

        /**
         * Creates a problem from an iterable of differentiable objectives.
         *
         * Validates non-emptiness by materializing [objectives] once (O(n)) and then creates a single [NonEmptyList].
         * [constraints] are stored as-is.
         *
         * @throws IllegalArgumentException if [objectives] is empty.
         * @param objectives Iterable of differentiable objectives; must contain at least one item.
         * @param constraints Optional constraints (stored as-is).
         */
        @JvmStatic
        @UnsafeSizeCreation
        public fun <T> unsafeFromIterable(
            objectives: Iterable<DifferentiableObjective<T>>,
            constraints: Collection<Constraint<T>> = emptyList()
        ): GradientAwareProblem<T> {
            val list = objectives.toList()
            require(list.isNotEmpty()) { "GradientAwareProblem requires at least one objective." }
            return Impl(NonEmptyList(list.first(), list.drop(1)), constraints)
        }
    }

    /**
     * Minimal, value-based implementation.
     *
     * Keeps the type small and focused; relies on data-class–style semantics for structural equality and readable
     * diagnostics. Both [objectives] and [constraints] are stored as provided.
     *
     * @property objectives Non-empty list of differentiable objectives (stored as-is).
     * @property constraints Constraints applied to feasible solutions (stored as-is).
     */
    private data class Impl<T>(
        override val objectives: NonEmptyList<DifferentiableObjective<T>>,
        override val constraints: Collection<Constraint<T>>
    ) : GradientAwareProblem<T> {

        override fun toString(): String =
            "GradientAwareProblem(objectives=$objectives, constraints=$constraints)"
    }
}