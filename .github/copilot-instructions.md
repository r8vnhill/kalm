# KNOB Copilot Instructions

## Architecture
- `Problem<T>` in `core/src/main/kotlin/cl/ravenhill/knob/problem` wraps one or more `Objective<T>` plus optional `Constraint<T>`; prefer using `Problem(...)` helpers so `NonEmptyList` invariants stay enforced.
- Gradient-based flows use `GradientAwareProblem` and `DifferentiableObjective`; objectives are stored without copies, so treat passed collections as immutable to avoid aliasing issues.
- `Solution<T>` (`core/.../repr/Solution.kt`) delegates to Arrow `NonEmptyList`, and `Gradient` (`core/.../repr/Gradient.kt`) encapsulates a `DoubleArray` with Arrow `Either`-backed operations; extend these rather than raw collections to keep invariants and defensive-copy semantics.
- Module boundaries: `core` depends on `utils:domain` (validation primitives) and `utils:math` (SIMD vector ops); `benchmark` and `examples` consume `core`; central version alignment lives in `dependency-constraints`.

## Tooling & Build Logic
- Shared Gradle conventions live under `build-logic/`; apply `knob.library`, `knob.jvm`, `knob.testing`, `knob.reproducible` instead of configuring projects manually.
- `knob.jvm` reads `knob.java.default` (set to 21 in `gradle.properties`) and wires both Java/Kotlin toolchains plus `--add-modules jdk.incubator.vector`; use that property (or `-Pknob.java.default=`) when bumping JDKs so the `verifyKnobJavaDefault` task keeps root/build-logic in sync.
- Kotlin is pinned to `2.2.20-Beta2`; prefer K2 syntax (context receivers, value classes) already used in `Problem` and constraint APIs.
- SIMD helpers in `utils:math` rely on the incubator Vector API; avoid calling them from non-JVM code and preserve the existing `VectorOps.run { ... }` delegation pattern.

## Developer Workflows
- Run `./gradlew build` for the whole suite; use `./gradlew :core:test` or similar for module-specific cycles.
- Static analysis is grouped under `./gradlew lint`; dependency hygiene lives in `./gradlew dependencyMaintenance` (runs Version Catalog Update + Ben Manes).
- Examples execute via `./gradlew :examples:run -PexampleMain=com.foo.MainKt [-PexampleArgs=...]`; the task disables itself when `exampleMain` is missing.
- Configuration cache and parallel execution are enabled globally; keep new Gradle logic CC-friendly (no captured script lambdas, prefer `configureEach`).

## Coding Conventions
- Validation-first APIs return `Either<Error, T>` (see `Gradient.fromArray`, `EqualityThreshold.invoke`); propagate with `arrow.core.raise.either { ... }` or `.mapLeft { }` instead of throwing.
- Keep collections non-empty by construction using Arrow factories (`nonEmptyListOf`, `NonEmptyCollection`) and reuse `Size`/`EqualityThreshold` value classes from `utils:domain` for bounds or tolerances.
- Unsafe constructors marked with `@UnsafeSizeCreation` deliberately skip checks (e.g., `Gradient.unsafeFromOwnedArray`); only use inside tightly-controlled hot paths and document the aliasing contract.
- Constraints are built via the provided factories (context-based for equality, enum-driven for inequality) to ensure thresholds/operators remain validated.

## Testing Patterns
- Tests use Kotest (`FreeSpec`, property-based `checkAll`) with Arrow matchers; see `core/src/test/kotlin/cl/ravenhill/knob/repr/GradientTest.kt` for idioms like `shouldBeRight` and generator utilities under `core/src/test/.../generators`.
- Shared test dependencies and helpers live in `utils/test-commons`; add new cross-module test tooling there and consume it via `testImplementation(projects.utils.testCommons)`.
- Vector/gradient tests often assert aliasing semantics (defensive copy vs unsafe alias); follow the same structure when covering new factories or operations.

## Documentation & Style
- Keep module-level rationale comments (present in existing `build.gradle.kts` files) intact; expand them when adding conventions so future readers understand the "why".
- Prefer ASCII in sources and continue annotating tricky sections with brief intent comments rather than restating the code.
