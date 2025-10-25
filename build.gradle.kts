/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

// Apply shared conventions and quality tools at the root level.
plugins {
    id("kalm.reproducible")                         // Ensures byte-for-byte reproducible archives
    alias { libs.plugins.kotlin.bin.compatibility } // Kotlin binary compatibility validator
    alias { libs.plugins.detekt }                   // Static code analysis tool
}

// Configure Kotlin binary compatibility validation
apiValidation {
    ignoredProjects += listOf(
        // Uncomment when needed
        // "test-utils", "examples"
    )
}
