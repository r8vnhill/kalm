import org.gradle.api.artifacts.dsl.LockMode

/*
 * Applies a consistent dependency locking policy to the current project.
 *
 * We avoid blanket `allprojects`/`subprojects` configuration by attaching the locking behavior through a dedicated
 * convention plugin. Projects opt-in explicitly, which keeps configuration ordering predictable and supports
 * configuration cache isolation.
 *
 * ## Lockfile workflow:
 *
 * - Generate/update lockfiles: `./gradlew dependencies --write-locks`
 * - Update selected coordinates: `./gradlew dependencies --update-locks group:name`
 */
configurations.configureEach {
    // Only lock resolvable configurations to avoid lockfile churn from internal buckets.
    if (isCanBeResolved) {
        resolutionStrategy.activateDependencyLocking()
    }
}

dependencyLocking {
    // Fail fast when a resolvable configuration resolves without a lock entry.
    lockMode.set(LockMode.STRICT)
}
