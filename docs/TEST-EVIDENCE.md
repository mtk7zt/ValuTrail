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
- The baseline portfolio value calculates to $751.70 USD with an initial P&L of $0.00.
- Processing the four sequential price events (`e1`–`e4`) yields the expected marked values ($784.40, $777.80, $770.00, $754.00) and cumulative P&L figures (+$32.70, +$26.10, +$18.30, +$2.30).
- Candidate valuations and `ReplayResult` objects are computed before modifying internal state; if validation fails, nothing is updated.
- A freshly constructed identical duplicate event is ignored and returns `Optional.empty()`.
- Conflicting duplicate events with altered prices, sequences, dates, or symbols throw `IllegalArgumentException` and leave all engine getters unchanged.
- The fields on accepted `ReplayResult` objects match the engine's current state getters.
- Rejected events leave engine state unchanged.
- The engine currently processes events in a single thread.

### Limitations
- The engine processes events sequentially in a single thread; concurrent event processing is not supported.

---

## 3. File-Driven Replay & CLI Execution Verification (2026-09-15)

### Automated Test Suite Execution
- **Command**: `mvn -B clean test`
- **Targets**: `dev.esosa.risk.MainTest`, `dev.esosa.risk.ReplayEngineTest`, `dev.esosa.risk.CsvParserTest`
- **Result**: PASSED (31 tests, 0 failures, 0 errors, 0 skipped)

```text
[INFO] Running dev.esosa.risk.CsvParserTest
[INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.159 s -- in dev.esosa.risk.CsvParserTest
[INFO] Running dev.esosa.risk.MainTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.014 s -- in dev.esosa.risk.MainTest
[INFO] Running dev.esosa.risk.ReplayEngineTest
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.020 s -- in dev.esosa.risk.ReplayEngineTest
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### CLI Replay Execution Across Two Fresh Processes

#### Process 1 Execution
- **Command**: `mvn compile exec:java "-Dexec.args=examples/positions.csv examples/prices.csv"`
- **Result**: SUCCESS

```text
Baseline: 751.70
1 e1 2024-08-29 AAPL 784.40 +32.70
2 e2 2024-08-29 WMT 777.80 +26.10
3 e3 2024-08-30 AAPL 770.00 +18.30
4 e4 2024-08-30 WMT 754.00 +2.30
```

#### Process 2 Execution
- **Command**: `mvn compile exec:java "-Dexec.args=examples/positions.csv examples/prices.csv"`
- **Result**: SUCCESS

```text
Baseline: 751.70
1 e1 2024-08-29 AAPL 784.40 +32.70
2 e2 2024-08-29 WMT 777.80 +26.10
3 e3 2024-08-30 AAPL 770.00 +18.30
4 e4 2024-08-30 WMT 754.00 +2.30
```

#### Process Output Comparison
Comparison of the stdout stream across both fresh executions confirmed identical output (0 byte difference).

### Verified Behavior
- Reads `examples/positions.csv` (exact header `symbol,quantity,baseline_price`) and `examples/prices.csv` (exact header `eventId,sequence,date,symbol,price`).
- Replay outputs baseline value $751.70 USD followed by all accepted events in processing sequence with ID, date, symbol, marked portfolio value, and cumulative P&L ($784.40 / +$32.70, $777.80 / +$26.10, $770.00 / +$18.30, $754.00 / +$2.30).
- Replay report lines are collected locally and written to stdout only after all parsed events have processed successfully; any rejected event aborts execution and produces no partial report in stdout.
- Rejects bad headers, malformed rows, blank fields, invalid numbers, and unparseable dates with an error naming the file and row number without echoing whole untrusted input rows.
- Rejects duplicate position symbols with an error naming the file and row number.
- Explicitly rejects quotation marks (`"`) to prevent silent misparsing of quoted or multiline fields.
- Preserves replay engine invariants: engine rule violations (conflicting duplicate event IDs, unknown symbols, non-advancing sequences) throw `IllegalArgumentException` and leave engine state completely unchanged.
- Execution is completely deterministic across independent fresh processes.

### Limitations
- Supported CSV format is strictly unquoted comma-delimited UTF-8; quotation marks and quoted or multiline fields are unsupported.
- The CLI entrypoint accepts exactly two positional arguments (`<positions.csv> <prices.csv>`).
- Replay processing is single-threaded.
