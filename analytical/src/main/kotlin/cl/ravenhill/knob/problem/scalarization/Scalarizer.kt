/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.problem.scalarization

import arrow.core.Either
import arrow.core.NonEmptyList
import cl.ravenhill.knob.problem.objective.DifferentiableObjective

public fun interface Scalarizer<T> {
    public typealias Result<T> = Either<ScalarizationError, DifferentiableObjective<T>>

    public fun combine(objectives: NonEmptyList<DifferentiableObjective<T>>): Result<T>
}
