import org.gradle.api.artifacts.dsl.LockMode

/*
 * Applies a consistent dependency locking policy to the current project.
 *
 * We avoid blanket `allprojects`/`subprojects` configuration by attaching the locking
 * behavior through a dedicated convention plugin. Projects opt-in explicitly, which
 * keeps configuration ordering predictable and supports configuration cache isolation.
 */
dependencyLocking {
    // Lock every configuration so dependency versions are reproducible everywhere.
    lockAllConfigurations()

    // Fail fast when a configuration resolves without a lock entry.
    lockMode.set(LockMode.STRICT)
}
