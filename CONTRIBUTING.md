# Contributing to Risk Replay Engine

## Feature Definition of Done (DoD)

Before any feature or code change is considered complete and ready to merge/review, it must satisfy the following criteria:

1. **Scoped Implementation**:
   - The change directly implements the accepted requirements without introducing speculative features, dead code, or unrequested abstractions.
   - Package boundaries (`dev.esosa.risk.*`) and code conventions are strictly followed.

2. **Automated Testing**:
   - Unit tests are written using JUnit Jupiter (`org.junit.jupiter`).
   - Tests cover normal flows, boundary conditions, and failure/edge cases.
   - All tests run cleanly with zero failures, errors, or flaky behavior.

3. **Build & Quality Gates**:
   - The project compiles and passes verification via `mvn -B test`.
   - Maven compiler targets Java 17 compatibility.
   - No compiler warnings or unmanaged dependency conflicts.

4. **Documentation & Traceability**:
   - Significant architectural decisions are recorded in `docs/DECISIONS.md`.
   - Test execution results and verification notes are logged in `docs/TEST-EVIDENCE.md`.
   - Any user-facing operational steps or CLI commands are reflected in `README.md`.

5. **Clean Working Tree**:
   - Generated files and build outputs (`target/`) are ignored by `.gitignore`.
   - No credentials, API tokens, local configurations, or unintended files are tracked.
   - Git commits are purposeful, granular, and accompanied by clear messages.
