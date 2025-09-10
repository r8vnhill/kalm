/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.knob.problem.scalarization

import cl.ravenhill.knob.KnobException

public sealed class ScalarizationError(message: String, cause: Throwable? = null) :
    Exception(message, cause), KnobException {

    public data class IncompatibleObjectives(override val message: String, override val cause: Throwable? = null) :
        ScalarizationError(message, cause)
}
