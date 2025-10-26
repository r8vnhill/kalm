/*
 * Copyright (c) 2025, Ignacio Slater M.
 * 2-Clause BSD License.
 */

import org.gradle.api.tasks.testing.Test

// region TEST CONFIGURATION

// Configure testing behavior for all tasks of type Test.
//
// This ensures consistent logging and test platform usage across all projects that apply the `keen.library` convention
// plugin.
tasks.withType<Test>().all {
    // Use the JUnit Platform (required for JUnit 5 and compatible frameworks like Kotest)
    useJUnitPlatform()

    // Configure test logging to show results for passed, skipped, and failed tests, and to display output from standard
    // streams (e.g., println, System.out)
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
// endregion
