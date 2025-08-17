/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.utils

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class InequalityTypeTest : FreeSpec({
    "An 'Inequality Type' when" - {

        "getting the associated symbol should be" - {
            mapOf(
                "<" to InequalityType.LESS_THAN,
                ">" to InequalityType.GREATER_THAN,
                "<=" to InequalityType.LESS_THAN_OR_EQUAL,
                ">=" to InequalityType.GREATER_THAN_OR_EQUAL
            ).forEach { (symbol, type) ->
                "$symbol for ${type.name}" {
                    type.symbol shouldBe symbol
                }
            }
        }
    }
})
