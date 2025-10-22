/*
 * Academic / illustrative evolutionary computation algorithms
 */

plugins {
    id("knob.library")
}

dependencies {
    api(projects.core)
    // Academic algorithms may reuse utilities from production algorithms
    implementation(projects.ec.production)
}
