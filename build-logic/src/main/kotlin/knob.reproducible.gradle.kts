@file:Suppress("SpellCheckingInspection")
/*
 * === Reproducible Archives Convention ===
 * Applies safe, deterministic defaults to all archive-producing tasks (Jar/Zip/Tar) so that outputs are byte-for-byte
 * reproducible across machines and over time.
 *
 * Why this matters:
 *  - Higher remote/local build-cache hit rates
 *  - Easier artifact verification (supply-chain integrity)
 *  - Predictable CI behavior (no timestamp or ordering drift)
 */

// Base plugin provides lifecycle tasks (clean/assemble) and is a lightweight foundation for convention logic.
// No language plugins are required for these settings.
plugins {
    base
}

// === Global archive defaults ===
//  - Remove timestamp variance inside archives
//  - Use a stable, deterministic file ordering
//  - Exclude empty directories (OS/filesystem differences can introduce noise)
//  - Fail on duplicate entries to catch accidental resource clashes early
//
// Docs: https://docs.gradle.org/current/userguide/working_with_files.html#sec:reproducible_archives
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false // normalize entry timestamps
    isReproducibleFileOrder = true // add files in a stable order
    includeEmptyDirs = false // omit empty dirs to avoid variability
    duplicatesStrategy = DuplicatesStrategy.FAIL // fail fast on duplicates (deterministic)
}

// === Jar-specific hardening ===
// - Enable ZIP64 for safety on large projects
// - Strip manifest fields that vary per machine/JDK to keep outputs stable
tasks.withType<Jar>().configureEach {
    isZip64 = true

    manifest {
        attributes.remove("Built-By")
        attributes.remove("Created-By")
        attributes.remove("Build-Jdk")
    }
}

// === Zip-specific hardening ===
// Enable ZIP64 for consistency with Jar settings and large artifacts
tasks.withType<Zip>().configureEach {
    isZip64 = true
}

// === Tar-specific hardening ===
// Normalize POSIX permissions so umask/OS don’t change bits
//
// Files: 0644 (u=rw, g=r, o=r)
// Directories: 0755 (u=rwx, g=rx, o=rx)
tasks.withType<Tar>().configureEach {

    // Files: 0644 (rw-r--r--)
    filePermissions {
        user { read = true; write = true; execute = false }
        group { read = true; write = false; execute = false }
        other { read = true; write = false; execute = false }
    }

    // Directories: 0755 (rwxr-xr-x)
    dirPermissions {
        user { read = true; write = true; execute = true }
        group { read = true; write = false; execute = true }
        other { read = true; write = false; execute = true }
    }

    // Use GZIP compression for consistency with Jar/Zip defaults
    compression = Compression.GZIP
}

// === Javadoc reproducibility ===
// Suppress injected timestamps in generated docs
tasks.withType<Javadoc>().configureEach {
    (options as? StandardJavadocDocletOptions)
        ?.addBooleanOption("notimestamp", true)
}
