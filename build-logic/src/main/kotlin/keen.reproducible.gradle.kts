// Apply the base plugin, which provides core lifecycle tasks like `clean` and `assemble`.
// This is a minimal, non-language-specific plugin useful for convention logic.
plugins {
    base
}

// Configure all archive-producing tasks (Jar, Zip, Tar, etc.) to support reproducible builds.
// Reproducible builds generate archives (e.g., JARs) that are byte-for-byte identical regardless of when or where they
// were built.
// This improves:
// - Build cache hit rates (across CI machines or local/dev builds)
// - Artifact verification and integrity (e.g., for supply chain security)
// - Compliance with projects like https://reproducible-builds.org
//
// Official Gradle docs:
// https://docs.gradle.org/current/userguide/working_with_files.html#sec:reproducible_archives
tasks.withType<AbstractArchiveTask>().configureEach {
    // Prevent timestamps from being written into the archive entries.
    // Without this, even builds with the same content will produce different outputs.
    isPreserveFileTimestamps = false

    // Ensure files are added to the archive in a predictable and consistent order.
    // File ordering may otherwise vary across platforms and filesystems.
    isReproducibleFileOrder = true
}
