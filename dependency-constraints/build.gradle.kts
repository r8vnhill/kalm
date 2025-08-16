/*
 * =========================================================
 * :dependency-constraints — central Bill Of Materials (BOM)
 * =========================================================
 *
 * -------
 * Purpose
 * -------
 *  - Define version constraints for the whole build (or for published consumers) in one place.
 *  - Downstream modules import this platform to align transitive versions consistently.
 *
 * -----
 * Notes
 * -----
 *  - This project does not produce code; it only publishes constraints.
 *  - `api(...)` inside `constraints {}` means “export these rules to consumers.”
 *  - Versions come from the version catalog (libs.versions.toml).
 */

plugins {
    // Java Platform plugin creates a “platform” (BOM) that holds version constraints.
    `java-platform`
}

// Allow this platform to depend on other platforms/BOMs (e.g., vendor BOMs).
javaPlatform {
    allowDependencies()
}

dependencies {
    // This pins the set of modules to the catalog versions for all importers.
    constraints {
        // Export Arrow constraints defined in your version catalog bundle.
        api(libs.bundles.arrow)

        // Export Kotest constraints defined in your version catalog bundle.
        // This pins the set of Kotest modules to the catalog versions for all importers.
        api(libs.bundles.testing)
    }
}
