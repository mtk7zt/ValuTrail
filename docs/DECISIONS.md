# Architecture Decision Records (ADR)

## ADR-001: Project Foundation & Baseline Tooling

### Status
Accepted

### Context
The Risk Replay Engine requires a clean, robust, and minimal local project foundation without unnecessary frameworks or premature abstractions.

### Decisions
1. **Language Version**: Java 17 LTS as the compilation target (`<maven.compiler.release>17</maven.compiler.release>`) to provide standard modern language features and LTS stability.
2. **Build System**: Apache Maven for standard dependency management and lifecycle phases.
3. **Testing Framework**: JUnit 5 (JUnit Jupiter) combined with `maven-surefire-plugin` for automated test execution.
4. **Execution Plugin**: `exec-maven-plugin` configured with `dev.esosa.risk.Main` as the main class to enable standard CLI execution via `mvn exec:java`.
5. **Strict Scope**: No application framework dependencies (no Spring, Spark, cloud SDKs, AI integrations, or market APIs); JUnit Jupiter is used for tests.

### Consequences
- Minimal build overhead, fast build times, and no application framework dependencies.
- Foundation provides verified compilation and test verification out of the box.
