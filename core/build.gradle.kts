/*
 * Copyright (c) 2026, Ignacio Slater-Muñoz.
 * 2-Clause BSD License.
 */

plugins {
    id("kalm.library")
    id("kalm.jvm")
    id("kalm.detekt-redmadrobot")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.arrow.core)
    testImplementation(libs.bundles.kotest)
}
