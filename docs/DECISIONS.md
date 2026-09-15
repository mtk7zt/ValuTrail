# Architecture Decision Records (ADR)

## ADR-001: Project Foundation & Baseline Tooling

### Status
Accepted

### Context
I needed a clean, minimal Java project foundation to build the valuation engine step by step without extra framework complexity or premature abstractions.

### Decisions
1. **Language Version**: I chose Java 17 LTS as the compilation target (`<maven.compiler.release>17</maven.compiler.release>`) for record types and long-term support.
2. **Build Tool**: I picked standard Apache Maven for dependency management and lifecycle phases.
3. **Testing**: I use JUnit 5 (JUnit Jupiter) with `maven-surefire-plugin` for automated tests.
4. **Execution Plugin**: I configured `exec-maven-plugin` with `dev.esosa.risk.Main` so the entrypoint can run directly with `mvn exec:java`.
5. **No Application Frameworks**: I kept the application free of framework dependencies (no Spring, Spark, cloud SDKs, AI integrations, or market APIs). JUnit Jupiter is used only for tests.

### Consequences
- Builds are fast and straightforward with minimal configuration.
- The project compiles and runs tests out of the box on standard Java 17+ environments.

---

## ADR-002: Event Retries and Duplicate Handling

### Status
Accepted

### Context
In an event replay workflow, the same event ID might arrive more than once due to retries, network resends, or duplicated input files. The engine needs a predictable rule for duplicate event IDs.

### Decisions
1. **Duplicate Events**: I use the event ID to recognize retries. If the same ID arrives with the same fields, the engine ignores it so the portfolio is not valued twice (returning `Optional.empty()`). If any field changes, the engine rejects it rather than guessing which version is correct.
2. **Exact Equality Policy**: For now, duplicate detection requires exact record equality (`BigDecimal.equals`). That means prices must have the same decimal scale to count as identical.
3. **Pre-Mutation Candidate Computation**: Before updating any internal state (`latestPrices`, `lastAcceptedSequence`, or `acceptedEvents`), `ReplayEngine` computes the candidate marked portfolio value, P&L, and the resulting `ReplayResult`. If any validation fails, the method throws before touching any internal maps or counters.

### Consequences
- Duplicate retries do not alter portfolio valuation or advance the sequence counter.
- Conflicting event payloads cannot silently corrupt engine state.
- Rejected events leave engine state unchanged.
- The engine currently processes events in a single thread.

---

## ADR-003: Data Provenance Standard for External Values and Test Fixtures

### Status
Accepted

### Context
ValuTrail is a portfolio valuation tool where traceable calculation integrity is central. Downstream valuation models, risk reporting, and backtesting rely on auditable data; conflating illustrative test fixtures with verified market quotes produces false confidence. We need a clear, practical provenance standard for all external figures and synthetic inputs.

### Decisions
1. **Three-Tier Data Taxonomy**:
   - **Verified Source Observations**: External quotes audited against a documented provider page or dataset. Must include publisher, symbol, date/timestamp, exact field definition (e.g. daily `CLOSE`), access date, and comparison outcome. If any element is missing, the observation is marked unverified.
   - **Illustrative Inputs**: Synthetic holdings, dummy scenarios, or unverified prices used for calculation tests. Must be plainly labeled; ticker symbols and realistic magnitudes alone never confer verified market status.
   - **Calculated Outputs**: Marked valuations and P&L results. Must link directly to their input values, calculation formula, and verifiable test or hand calculation.
2. **Discrepancy Transparency**: When an external source disagrees with a fixture value, record both values and the exact difference. Explain the cause only when an authoritative reference supports it; never guess or speculate.
3. **Immediate Recording and Coordinated Fixture Updates**: When an external source disagrees with a fixture, record and label the mismatch immediately as illustrative. Retaining inaccurate prices is not a permanent policy; changing a fixture requires updating the CSV files, hand calculations, and tests together in a single reviewed change.
4. **Testing Boundary**: Automated tests verify replay engine behavior and arithmetic against the fixture; they do not attest to the truth or quality of an external market data source.

### Consequences
- Provides transparent auditability for any data entering the codebase.
- Protects users and downstream developers from mistaking synthetic test numbers for verified historical prices.
- Establishes a clear review checklist for contributors in `CONTRIBUTING.md`.
