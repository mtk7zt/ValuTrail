# Test Evidence

This document records the commands, test runs, and observed output for the ValuTrail project.

## 1. Foundation Verification (2026-09-15)

### Environment
- **JDK**: OpenJDK 25.0.3 Temurin (target bytecode: Java 17 via `--release 17`)
- **Maven**: Apache Maven 3.9.16
- **OS**: Windows 11 (amd64)

### Automated Test Suite Execution
- **Command**: `mvn -B test`
- **Target**: `dev.esosa.risk.MainTest`
- **Result**: PASSED (1 test, 0 failures)

```text
[INFO] Running dev.esosa.risk.MainTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.055 s -- in dev.esosa.risk.MainTest
[INFO] Results:
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Entrypoint Execution
- **Command**: `mvn compile exec:java`
- **Main Class**: `dev.esosa.risk.Main`
- **Result**: SUCCESS

```text
Risk Replay Engine — project initialized
```

---

## 2. Domain Model & In-Memory Replay Engine Verification (2026-09-15)

### Automated Test Suite Execution
- **Command**: `mvn -B test`
- **Targets**: `dev.esosa.risk.MainTest`, `dev.esosa.risk.ReplayEngineTest`
- **Result**: PASSED (10 tests, 0 failures, 0 errors, 0 skipped)

```text
[INFO] Running dev.esosa.risk.MainTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.059 s -- in dev.esosa.risk.MainTest
[INFO] Running dev.esosa.risk.ReplayEngineTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.065 s -- in dev.esosa.risk.ReplayEngineTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  4.149 s
[INFO] Finished at: 2026-09-15T17:20:32-04:00
[INFO] ------------------------------------------------------------------------
```

### Verified Behavior
- The baseline portfolio value calculates to $748.10 USD with an initial P&L of $0.00.
- Processing the four sequential price events (`e1`–`e4`) yields the expected marked values ($780.80, $774.20, $766.40, $750.40) and cumulative P&L figures (+$32.70, +$26.10, +$18.30, +$2.30).
- Candidate valuations and `ReplayResult` objects are computed before modifying internal state; if validation fails, nothing is updated.
- A freshly constructed identical duplicate event is ignored and returns `Optional.empty()`.
- Conflicting duplicate events with altered prices, sequences, dates, or symbols throw `IllegalArgumentException` and leave all engine getters unchanged.
- The fields on accepted `ReplayResult` objects match the engine's current state getters.
- Rejected events leave engine state unchanged.
- The engine currently processes events in a single thread.

### Limitations
- CSV file parsing and automated ingestion of `examples/positions.csv` and `examples/prices.csv` are not yet implemented.
- CLI arguments and execution in `Main` are not yet implemented.
- The engine processes events sequentially in a single thread; concurrent event processing is not supported.
