import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.credentials
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.maven

plugins {
    id("java-platform")
    `maven-publish`
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        // Ensure consumers align with the Kalm core module once published.
        api(project(":core"))

        // Align consumers with the Kotlin runtime version used across the project.
        api(libs.findLibrary("kotlin-stdlib").get())

        // Provide test-aligned dependencies for users adopting Kalm's recommended stack.
        api(libs.findLibrary("kotest-assertions-core").get())
        api(libs.findLibrary("kotest-framework-engine").get())
    }
}

publishing {
    publications {
        create<MavenPublication>("bom") {
            from(components["javaPlatform"])
            artifactId = project.findProperty("kalm.pom.platformArtifactId")?.toString() ?: "kalm-platform"
            pom {
                name.set(project.findProperty("kalm.pom.platformName")?.toString() ?: "Kalm Platform BOM")
                description.set(
                    project.findProperty("kalm.pom.platformDescription")?.toString()
                        ?: "Bill of materials aligning Kalm dependencies."
                )
                url.set(project.findProperty("kalm.pom.url")?.toString() ?: "https://gitlab.com/${rootProject.name}")
                licenses {
                    license {
                        name.set("BSD-2-Clause")
                        url.set("https://opensource.org/license/bsd-2-clause/")
                    }
                }
                scm {
                    url.set(project.findProperty("kalm.pom.url")?.toString() ?: "https://gitlab.com/${rootProject.name}")
                }
            }
        }
    }

    project.findProperty("kalm.publish.repoUrl")?.toString()?.let { repoUrl ->
        repositories {
            maven {
                name = project.findProperty("kalm.publish.repoName")?.toString() ?: "internal"
                url = project.uri(repoUrl)
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
