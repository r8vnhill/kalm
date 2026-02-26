/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

package cl.ravenhill.kalm.eval

import cl.ravenhill.kalm.operators.ContravariantOperator
import cl.ravenhill.knob.repr.Solution

interface SolutionEvaluator<in T: Solution<*>> : ContravariantOperator<T>
