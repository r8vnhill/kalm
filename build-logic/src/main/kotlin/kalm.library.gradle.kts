/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.credentials
import org.gradle.kotlin.dsl.maven
import org.gradle.kotlin.dsl.maybeCreate
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

// region PUBLICATION ARTIFACTS

plugins.apply("maven-publish")

private val strictTrueValues = setOf("true", "strict", "enforce")

val explicitApiModeProperty = project.findProperty("kalm.explicitApiMode")?.toString()?.lowercase()
val explicitApiToggle = project.findProperty("kalm.explicitApi")?.toString()?.lowercase()
val artifactIdOverride = project.findProperty("kalm.pom.artifactId")?.toString()
val pomName = project.findProperty("kalm.pom.name")?.toString() ?: project.name
val pomDescription = project.findProperty("kalm.pom.description")?.toString()
    ?: "Module ${project.path} for the Kalm research framework."
val pomUrl = project.findProperty("kalm.pom.url")?.toString() ?: "https://gitlab.com/" + project.rootProject.name
val publishRepoUrl = project.findProperty("kalm.publish.repoUrl")?.toString()
val publishRepoName = project.findProperty("kalm.publish.repoName")?.toString() ?: "internal"
val publishRepoUsername = project.findProperty("kalm.publish.repoUsername")?.toString()
val publishRepoPassword = project.findProperty("kalm.publish.repoPassword")?.toString()

plugins.withId("org.jetbrains.kotlin.jvm") {
    extensions.configure<JavaPluginExtension>("java") {
        withSourcesJar()
        withJavadocJar()
    }

    extensions.configure<KotlinProjectExtension>("kotlin") {
        when {
            explicitApiModeProperty == "warning" -> explicitApiWarning()
            explicitApiModeProperty == "strict" -> explicitApi()
            explicitApiToggle in strictTrueValues -> explicitApi()
        }
    }

    extensions.configure<PublishingExtension>("publishing") {
        publications {
            maybeCreate<MavenPublication>("library").apply {
                from(components["java"])
                if (artifactIdOverride != null) {
                    artifactId = artifactIdOverride
                }
                pom {
                    name.set(pomName)
                    description.set(pomDescription)
                    url.set(pomUrl)
                    licenses {
                        license {
                            name.set("BSD-2-Clause")
                            url.set("https://opensource.org/license/bsd-2-clause/")
                        }
                    }
                    scm {
                        url.set(pomUrl)
                    }
                }
            }
        }

        publishRepoUrl?.let { repoUrl ->
            repositories {
                maven {
                    name = publishRepoName
                    url = project.uri(repoUrl)
                    if (!publishRepoUsername.isNullOrBlank() && !publishRepoPassword.isNullOrBlank()) {
                        credentials {
                            username = publishRepoUsername
                            password = publishRepoPassword
                        }
                    }
                }
            }
        }
    }
}

// endregion

// region TEST CONFIGURATION

// Configure testing behavior for all tasks of type Test.
//
// This ensures consistent logging and test platform usage across all projects that apply the `kalm.library` convention
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
