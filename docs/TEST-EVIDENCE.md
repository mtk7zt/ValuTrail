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

---

## 4. Scenario Replay Verification (2026-09-15)

### Automated Test Suite Execution
- **Command**: `mvn -B clean test`
- **Targets**: `dev.esosa.risk.CsvParserTest`, `dev.esosa.risk.MainTest`, `dev.esosa.risk.ReplayEngineTest`
- **Result**: PASSED (39 tests, 0 failures, 0 errors, 0 skipped)

```text
[INFO] Running dev.esosa.risk.CsvParserTest
[INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.231 s -- in dev.esosa.risk.CsvParserTest
[INFO] Running dev.esosa.risk.MainTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.015 s -- in dev.esosa.risk.MainTest
[INFO] Running dev.esosa.risk.ReplayEngineTest
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.066 s -- in dev.esosa.risk.ReplayEngineTest
[INFO]
[INFO] Results:
[INFO]
[INFO] Tests run: 39, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### CLI Execution Verification
- **Command**: `mvn compile exec:java "-Dexec.args=examples/positions.csv examples/prices.csv"`
- **Result**: SUCCESS

```text
Baseline: 751.70
1 e1 2024-08-29 AAPL 784.40 +32.70
2 e2 2024-08-29 WMT 777.80 +26.10
3 e3 2024-08-30 AAPL 770.00 +18.30
4 e4 2024-08-30 WMT 754.00 +2.30
```

### Verified Behavior
- **Baseline Scenario Evaluation**: Evaluating scenario "Tech Rally / Retail Dip" (AAPL +10%, WMT -5%) against starting positions produces:
  - AAPL scenario price: $224.61 \times 1.10 = 247.071 \rightarrow \$247.07$
  - WMT scenario price: $74.72 \times 0.95 = 70.984 \rightarrow \$70.98$
  - Scenario portfolio value: $10 \times 247.07 + (-20) \times 70.98 = 2470.70 - 1419.60 = \$1051.10$ USD
  - Value change from base ($751.70): $+\$299.40$ USD.
- **Post-Replay Scenario Evaluation**: Evaluating scenario "Tech Surge" (AAPL +5%, WMT unshifted) after processing events `e1`–`e4` produces:
  - AAPL scenario price: $227.10 \times 1.05 = 238.455 \rightarrow \$238.46$
  - WMT scenario price: $\$75.85$ (retained)
  - Scenario portfolio value: $10 \times 238.46 + (-20) \times 75.85 = 2384.60 - 1517.00 = \$867.60$ USD
  - Value change from current ($754.00): $+\$113.60$ USD.
- **Negative Shock Scenario Evaluation**: Evaluating scenario "Market Selloff" (AAPL -8%, WMT -4%) after processing events `e1`–`e4` produces:
  - AAPL scenario price: $227.10 \times 0.92 = 208.932 \rightarrow \$208.93$
  - WMT scenario price: $75.85 \times 0.96 = 72.816 \rightarrow \$72.82$
  - Scenario portfolio value: $10 \times 208.93 + (-20) \times 72.82 = 2089.30 - 1456.40 = \$632.90$ USD
  - Value change from current ($754.00): $-\$121.10$ USD.
- **Rounding Policy**: Scenario prices are rounded to 2 decimal places using `RoundingMode.HALF_UP` before computing position market values.
- **Read-Only / Idempotence**: Running the same scenario twice returns identical `ScenarioResult` values and leaves engine state (`getCurrentValue()`, `getCumulativePnL()`, `getLastAcceptedSequence()`, `getLatestPrice()`) unchanged. Subsequent historical price events continue processing as expected.
- **Validation**: Unknown symbols in scenario maps throw `IllegalArgumentException` without modifying engine state.
- **Percentage Shift Bounds**: Percentage shifts $\le -1.0$ (-100% or worse) and null values are rejected with `IllegalArgumentException` or `NullPointerException`.
- **Existing Historical Replay**: The existing file-driven replay behavior and CLI stdout format remain completely unchanged.

### Limitations
- Scenarios are evaluated programmatically via the Java API; CLI arguments or CSV scenario files are not yet supported.
- Scenario marked-value changes reflect instantaneous static arithmetic revaluations under hypothetical price shifts; they are not forecasts or comprehensive market-risk calculations.
- Replay and scenario evaluation remain single-threaded.

