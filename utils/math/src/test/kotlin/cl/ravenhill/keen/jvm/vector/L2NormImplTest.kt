/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

package cl.ravenhill.keen.jvm.vector

import io.kotest.core.spec.style.FreeSpec
import kotlin.math.sqrt

class L2NormImplTest : FreeSpec({
    "A L2 norm kernel" - {

        "when compared to scalar reference" - {
            "should agree within a few ulps on random slices" {

            }
        }
    }
})

private object ScalarL2Norm : L2Norm {
    override fun DoubleArray.squaredL2Norm(): Double {
        var s = 0.0
        var i = 0
        while (i < size) {
            val x = this[i]
            s = Math.fma(x, x, s) // s += x*x (FMA improves both perf & fidelity)
            i++
        }
        return s
    }

    override fun DoubleArray.l2Norm(): Double = sqrt(this.squaredL2Norm())
}
