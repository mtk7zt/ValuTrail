# Architecture Decision Records (ADR)

## ADR-001: Project Foundation & Baseline Tooling

### Status
Accepted

### Context
I needed a clean, minimal Java project foundation to build the valuation engine step by step without extra framework complexity or premature abstractions.

### Decisions
1. **Language Version**: I chose Java 25 LTS as the compilation target (`<maven.compiler.release>25</maven.compiler.release>`) for record types and long-term support.
2. **Build Tool**: I picked standard Apache Maven for dependency management and lifecycle phases.
3. **Testing**: I use JUnit 5 (JUnit Jupiter) with `maven-surefire-plugin` for automated tests.
4. **Execution Plugin**: I configured `exec-maven-plugin` with `dev.esosa.risk.Main` so the entrypoint can run directly with `mvn exec:java`.
5. **No Application Frameworks**: I kept the application free of framework dependencies (no Spring, Spark, cloud SDKs, AI integrations, or market APIs). JUnit Jupiter is used only for tests.

### Consequences
- Builds are fast and straightforward with minimal configuration.
- The project compiles and runs tests out of the box on standard Java 25+ environments.

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

---

## ADR-004: Lightweight Unquoted CSV Parsing & Format Policy

### Status
Accepted

### Context
I needed to ingest portfolio holdings (`examples/positions.csv`) and price events (`examples/prices.csv`) into the domain model without pulling in third-party CSV libraries (e.g. Commons CSV, OpenCSV, Jackson) or conflating file parsing with valuation logic.

### Decisions
1. **Separation of Responsibilities**: CSV parsing and syntax/schema validation live entirely in `CsvParser`. It checks file existence, exact headers, column counts, data types (numeric quantities/prices and ISO-8601 dates), and duplicate position symbols, formatting errors as `<file>:<row>: <reason>`. Business valuation rules, sequence advancement, and duplicate event ID handling remain strictly in `ReplayEngine`.
2. **Unquoted Comma-Delimited Format**: The reader expects plain, unquoted UTF-8 comma-separated rows. To avoid silent misparsing or data corruption, any line containing quotation marks (`"`) is rejected immediately with an explicit error naming the file and row number.
3. **Strict Row Validation**: Blank fields, empty lines, column count mismatches, and non-positive prices/sequences throw `IllegalArgumentException` identifying the specific file and row.

### Consequences
- Zero external dependencies are added to `pom.xml`.
- Contributors know that quotation marks and quoted or multiline CSV records are not supported; if quote parsing is needed in the future, a full RFC 4180 parser must be implemented explicitly rather than relying on regex or string splits.

---

## ADR-005: In-Memory Scenario Evaluation and Rounding Policy

### Status
Accepted

### Context
Beyond replaying historical market price sequences, I needed a way to evaluate hypothetical price shocks (what-if scenarios) against the portfolio's current holdings without polluting the historical event log or altering the engine's internal valuation state.

### Decisions
1. **Read-Only Evaluation**: `evaluateScenario` computes the hypothetical portfolio value and its change from the current mark without mutating any internal state (`latestPrices`, `acceptedEvents`, `lastAcceptedSequence`). Running the same scenario multiple times produces the exact same `ScenarioResult`, and subsequent historical price events continue processing as if the scenario was never run.
2. **Decimal Fraction Representation**: Percentage price shifts are expressed as decimal fractions in `BigDecimal` (for example, `0.10` for +10% and `-0.05` for -5%), where $P_{scenario} = P_{current} \times (1 + \text{shift})$. Symbols omitted from the scenario retain their current marked prices.
3. **Explicit Half-Up Rounding**: Multiplying prices by arbitrary percentage shifts can produce fractional cents (for example, $\$224.61 \times 1.10 = \$247.071$). I explicitly round scenario prices to 2 decimal places using `RoundingMode.HALF_UP` before computing position market values. This keeps scenario prices auditable as quoted dollar figures and ensures the scenario portfolio value matches the sum of displayed position marks without hidden sub-cent precision.
4. **Pre-Evaluation Validation**: Before calculating any prices, the engine verifies that:
   - Scenario names are non-blank.
   - All scenario symbols exist in the initial portfolio (unknown symbols throw `IllegalArgumentException`).
   - Percentage changes are non-null and strictly greater than `-1.0` (-100%). A drop of 100% or more is rejected because market prices must remain strictly positive.

### Consequences
- Callers can evaluate instantaneous hypothetical price shifts on an in-progress replay session without altering engine state.
- The resulting valuation change represents an immediate arithmetic revaluation under specified price shocks, not a predictive forecast or complete measure of market risk (it does not model liquidity, volatility, correlations, or risk factors).
- Engine state integrity is preserved; invalid scenarios fail fast without leaving partial calculations.
- File-based scenario ingestion via the CLI is specified in ADR-006.

---

## ADR-006: File-Driven Scenario Ingestion & CLI Integration

### Status
Accepted

### Context
Following ADR-005's in-memory scenario evaluation capability, I needed a file-driven CLI path for users to specify scenario price shocks in a CSV file and evaluate them on the portfolio after event replay. The existing two-argument replay command and its exact output contract had to remain completely unchanged.

### Decisions
1. **Optional Third Argument**: `dev.esosa.risk.Main` accepts either two or three positional arguments: `dev.esosa.risk.Main <positions.csv> <prices.csv> [scenario.csv]`. When two arguments are passed, output remains byte-for-byte identical to the historical replay report.
2. **Scenario CSV Format & Schema**: The file requires the exact header `scenario,symbol,percentage_change`. To ensure determinism and clarity, each file defines exactly one scenario: all rows must specify the same non-blank scenario name. Duplicate symbols within the scenario file are rejected.
3. **Mandatory Portfolio Symbol Validation**: Scenario parsing in `CsvParser` requires the portfolio's known symbols (`Set<String> portfolioSymbols`). Any symbol not present in the portfolio is rejected immediately with `<file>:<row>: Unknown portfolio symbol in scenario: '<symbol>'`. No unchecked public parsing overload is exposed.
4. **Validation Guardrails**: Quotation marks (`"`), blank fields, column count mismatches, malformed numbers, and shifts $\le -1.0$ are rejected with file and line numbers before replay execution.
5. **Atomic Report Guarantee**: Report lines are buffered in memory and printed to stdout only after all files are parsed, all events are processed, and scenario evaluation succeeds. Any validation or engine failure aborts execution and leaves the application stdout completely empty.
6. **Scenario Report Output**: The scenario section prints the scenario name, base marked value, scenario prices in portfolio order from `positions.csv` (`AAPL=..., WMT=...`), scenario marked value, and signed value change.

### Consequences
- Existing two-argument historical replay scripts and automations remain 100% backward-compatible.
- Users can evaluate hypothetical price shocks against post-replay portfolio holdings using standard CSV files.
- Misleading partial reports are prevented on parsing or valuation errors.


