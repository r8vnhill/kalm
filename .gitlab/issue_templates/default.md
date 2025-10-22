# KNOB Issue Template

> Fill every section. Remove guidance that does not apply before submitting.

## Summary

- Concise description of the problem or enhancement.
- Link to any discussions, docs, or specs that provide background.

## Motivation / Context

- Why is this needed now?
- What part of KNOB (module, feature, workflow) is affected?
- What happens if we do nothing?

## Steps to Reproduce (bugs only)

1. Describe the minimal sequence of commands or API calls.
2. Include any required configuration (e.g., `knob.java.default`, vector API toggles).
3. Attach logs, stack traces, or screenshots as needed.

## Expected Result

- What *should* happen after following the steps above?

## Observed Result

- What actually happens? Include error messages or incorrect outcomes.

## Proposed Fix / Work Items

- [ ] Suggested change or task 1
- [ ] Suggested change or task 2
- [ ] Additional notes, open questions, or alternatives

## Validation Checklist

- [ ] `./gradlew build`
- [ ] `./gradlew lint`
- [ ] Module tests (e.g., `./gradlew :core:test`)
- [ ] Other verification (describe):

## Environment

- OS / architecture:
- Gradle version (run `./gradlew --version`):
- Resolved Java toolchain (default is 21 via `knob.java.default`):
- Extra flags (e.g., `-Ptest.jvmArgs=` toggles vector module):

## Attachments / Links

- Related issues or merge requests:
- Logs, traces, or performance metrics:

<!-- Quick actions: adjust labels/assignees as needed -->
/label ~"status::triage"
