/*
 * Applies shared conventions and wires dependencies for this JVM/Kotlin module.
 *
 * ---------
 * Key ideas
 * ---------
 *  - Conventions live in the precompiled plugins (`knob.library`, `knob.jvm`).
 *  - A central constraints/BOM project (`:dependency-constraints`) defines versions.
 *  - Detekt runs with an extra ruleset (formatting).
 *  - Some modules (e.g., `:util:math`) are JVM-exclusive by design, since they rely on efficient JVM numeric APIs
 *    (e.g., Math.fma, Vector API). These cannot be shared across multiplatform builds without re-implementation.
 *
 * --------------------
 * Tips for maintainers
 * --------------------
 *  - Use `api` only if the types leak into this module’s public API (e.g., Arrow's `Either`); otherwise prefer
 *    `implementation`.
 *  - When adding math-intensive functionality, prefer depending on `:util:math` instead of duplicating numeric kernels
 *    locally.
 */

plugins {
    id("knob.library") // Project-wide library conventions (tests, publishing knobs, etc.)
    id("knob.jvm") // JVM/Kotlin toolchain + compiler defaults (property-driven Java version)
    alias(libs.plugins.detekt) // Static analysis (Detekt) for Kotlin sources
    `maven-publish`
}

group = "cl.ravenhill.knob"
version = "0.1.0-SNAPSHOT"

dependencies {
    // Import the project-local platform/BOM that pins versions for your house stack (e.g., Arrow, Kotest).
    // This does not add classes to the classpath; it only contributes *version constraints* to resolution.
    implementation(platform(projects.dependencyConstraints))

    // JVM-specific math utilities (vectorized ops, numeric kernels).
    implementation(projects.utils.math)
    // Domain-specific types (Size, HasSize) and utilities.
    api(projects.utils.domain)

    // Attach Detekt’s formatting ruleset (ktlint rules packaged for Detekt).
    detektPlugins(libs.detekt.formatting.get().apply { "$group:$module:$version" })

    // Arrow libraries (bundle from the catalog).
    api(libs.bundles.arrow)

    // Test dependencies
    testImplementation(projects.utils.testCommons)
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("knob-core")
                description.set("Core module of the Knob optimization framework")
                url.set("https://gitlab.com/r8vnhill/knob/-/tree/main/core")

                licenses {
                    license {
                        name.set("BSD-2-Clause")
                        url.set("https://opensource.org/license/bsd-2-clause/")
                    }
                }
                developers {
                    developer {
                        id.set("r8vnhill")
                        name.set("Ignacio Slater-Muñoz")
                    }
                }
                scm {
                    url.set("https://gitlab.com/r8vnhill/knob/")
                    connection.set("scm:git://gitlab.com/r8vnhill/knob.git")
                    developerConnection.set("scm:git:ssh://gitlab.com/r8vnhill/knob.git")
                }
            }
        }
    }
    repositories {
        mavenLocal() // publishes to ~/.m2/repository
    }
}
