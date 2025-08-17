/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.jvm

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.booleans.shouldBeTrue

class VectorApiSmokeTest : FreeSpec({
    "jdk.incubator.vector module is present" {
        val present = ModuleLayer.boot().findModule("jdk.incubator.vector").isPresent
        withClue(
            buildString {
                appendLine("\njava.version = ${System.getProperty("java.version")}")
                appendLine("java.vendor  = ${System.getProperty("java.vendor")}")
                appendLine("java.home    = ${System.getProperty("java.home")}")
                appendLine("module present = $present")
            }
        ) {
            present.shouldBeTrue()
        }
    }
})
