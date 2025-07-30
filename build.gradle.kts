@file:Suppress("SpellCheckingInspection")

/*
* Copyright (c) 2025, Ignacio Slater M.
* 2-Clause BSD License.
*/

// Apply shared conventions and quality tools at the root level.
plugins {
    id("keen.reproducible") // Ensures byte-for-byte reproducible archives
    alias { libs.plugins.kotlin.bin.compatibility } // Kotlin binary compatibility validator
    alias { libs.plugins.detekt } // Static code analysis tool
    id("nl.littlerobots.version-catalog-update") version "1.0.0"
}

// Configure Kotlin binary compatibility validation
apiValidation {
    ignoredProjects += listOf(
        // Uncomment when needed
        // "test-utils", "examples"
    )
}

versionCatalogUpdate {
    // Keep things readable and deterministic
    sortByKey.set(true)

    // Only upgrade to stable versions
    keep { keepUnusedVersions.set(true) }
}
