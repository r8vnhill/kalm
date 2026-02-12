/*
 * KALM Platform Bill of Materials (BOM)
 *
 * This Gradle module defines a *platform project* that provides dependency alignment for all modules and external
 * consumers of KALM.
 *
 * The platform uses `java-platform` and publishes a Maven BOM artifact  that pins versions for Kotlin, Kotest, and KALM
 * core itself.
 *
 * Once published, users can depend on the BOM like:
 *
 * ```kotlin title="build.gradle.kts"
 * implementation(platform("cl.ravenhill:kalm-platform:<version>"))
 * ```
 *
 * This ensures consumers get consistent versions for transitive KALM dependencies without managing them manually.
 */

plugins {
    // Enables the Java Platform plugin, which allows defining a set of dependency constraints (used for BOM
    // publication).
    id("java-platform")

    // Reuse dependency locking conventions without cross-project blocks.
    id("kalm.reproducible")
    id("kalm.dependency-locking")

    // Adds support for publishing this platform as a Maven artifact.
    `maven-publish`
}

// Access the shared version catalog (`libs.versions.toml`)
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

javaPlatform {
    // Allow this platform to declare dependencies on other projects or libraries.
    // Without this, only version constraints can be added (no project dependencies).
    allowDependencies()
}

dependencies {
    constraints {
        // ---- Core alignment ----
        // Ensure any consumer that uses this BOM automatically aligns with the version of KALM's core module (once
        // published).
        api(projects.core)

        // ---- Kotlin alignment ----
        // Lock the Kotlin stdlib version to the one used across all KALM modules.
        // Prevents mismatched runtime versions when KALM is used as a dependency.
        api(libs.findLibrary("kotlin-stdlib").orElseThrow())

        // ---- Testing alignment ----
        // Align Kotest components so users who write tests with KALM get a consistent testing toolchain (assertions +
        // engine).
        api(libs.findLibrary("kotest-assertions-core").orElseThrow())
        api(libs.findLibrary("kotest-framework-engine").orElseThrow())
    }
}

// --- Maven publication configuration ---
// Publishes a BOM artifact (pom packaging) under the same coordinates as other KALM artifacts, with optional metadata
// controlled by project properties.
publishing {
    publications {
        create<MavenPublication>("bom") {
            // Publishes the platform component defined above.
            from(components["javaPlatform"])

            // The artifact ID defaults to "kalm-platform" unless overridden via property.
            artifactId = project.findProperty("kalm.pom.platformArtifactId")?.toString()
                ?: "kalm-platform"

            // Define the metadata for the POM
            pom {
                name.set(
                    project.findProperty("kalm.pom.platformName")?.toString()
                        ?: "KALM Platform BOM"
                )

                description.set(
                    project.findProperty("kalm.pom.platformDescription")?.toString()
                        ?: "Bill of materials aligning KALM dependencies."
                )

                // The default URL points to the project namespace in GitLab.
                url.set(
                    project.findProperty("kalm.pom.url")?.toString()
                        ?: "https://gitlab.com/${rootProject.name}"
                )

                // License metadata for downstream consumers.
                licenses {
                    license {
                        name.set("BSD-2-Clause")
                        url.set("https://opensource.org/license/bsd-2-clause/")
                    }
                }

                // Source control metadata for generated POMs.
                scm {
                    url.set(
                        project.findProperty("kalm.pom.url")?.toString()
                            ?: "https://gitlab.com/${rootProject.name}"
                    )
                }
            }
        }
    }

    // --- Repository configuration ---
    // Dynamically set the Maven repository if publishing properties are present.
    // This lets CI/CD or local scripts publish to internal or snapshot repositories  without hardcoding credentials or
    // URLs in the build script.
    project.findProperty("kalm.publish.repoUrl")?.toString()?.let { repoUrl ->
        repositories {
            maven {
                // Use a configurable name (defaults to "internal")
                name = project.findProperty("kalm.publish.repoName")?.toString() ?: "internal"
                url = project.uri(repoUrl)

                // Configure credentials only if both username and password are defined.
                val username = project.findProperty("kalm.publish.repoUsername")?.toString()
                val password = project.findProperty("kalm.publish.repoPassword")?.toString()
                if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                    credentials {
                        this.username = username
                        this.password = password
                    }
                }
            }
        }
    }
}
