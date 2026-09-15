# ValuTrail

ValuTrail is a portfolio valuation tool built in Java 17. It replays ordered market price events across an initial signed portfolio and tracks how each event changes marked value and cumulative P&L.

> **Current State**: The repository contains the tested Java 17 foundation, in-memory domain records (`Position`, `PriceEvent`, `ReplayResult`), `ReplayEngine`, strict unquoted CSV parsing (`CsvParser`), and the file-driven CLI entrypoint (`Main`). All six fixture prices match StatMuse's displayed daily `CLOSE` table.

## Prerequisites

- **Java Development Kit (JDK)**: Java 17+ (configured with `--release 17`)
- **Apache Maven**: 3.8+
- **Git**: 2.x+

## Verified Commands

### Build & Test
Compile the project and run the automated test suite (verifying replay engine logic, CSV parsing, and arithmetic against fixture inputs):
```bash
mvn -B test
```

### Build Verification
The repository includes a GitHub Actions workflow (`.github/workflows/ci.yml`) configured to run `mvn -B clean test` on pushes and pull requests to `main` using Java 17.
- **What CI checks**: Automated compilation, syntax/schema and CSV parsing validation, and JUnit test suite execution against local fixture files.
- **What CI does not check**: CI does not verify the truth or market accuracy of historical prices against external exchanges, nor does it test live market feeds or uncommitted local files. Passing CI verifies only that the codebase compiles and passes test assertions in the build environment. CI status will be reported by GitHub once the workflow has run on GitHub Actions.

### Run Application Entrypoint
Execute the file-driven replay entrypoint `dev.esosa.risk.Main` via Maven by passing the positions file first and prices file second:
```bash
mvn compile exec:java "-Dexec.args=examples/positions.csv examples/prices.csv"
```
Or in quiet mode (suppressing Maven lifecycle logs):
```bash
mvn -q compile exec:java "-Dexec.args=examples/positions.csv examples/prices.csv"
```
Expected output:
```text
Baseline: 751.70
1 e1 2024-08-29 AAPL 784.40 +32.70
2 e2 2024-08-29 WMT 777.80 +26.10
3 e3 2024-08-30 AAPL 770.00 +18.30
4 e4 2024-08-30 WMT 754.00 +2.30
```

## Replay Example Contract & Calculation Model

The first functional release replays ordered price events against an initial synthetic signed portfolio. Example files are in [`examples/`](examples/).

### Example Data Files

#### 1. Initial Holdings: [`examples/positions.csv`](examples/positions.csv)
Defines starting holdings and baseline mark prices at the initial valuation date.

```csv
symbol,quantity,baseline_price
AAPL,10,224.61
WMT,-20,74.72
```

- `symbol`: Equity ticker symbol.
- `quantity`: Signed share quantity held (+10 long AAPL, -20 short WMT). I chose these illustrative quantities to test a mixed long/short portfolio with different position sizes.
- `baseline_price`: Starting USD price per share as of 2024-08-28.

#### 2. Ordered Price Events: [`examples/prices.csv`](examples/prices.csv)
Defines the sequence of incoming market price updates to be replayed in order.

```csv
eventId,sequence,date,symbol,price
e1,1,2024-08-29,AAPL,227.88
e2,2,2024-08-29,WMT,75.05
e3,3,2024-08-30,AAPL,227.10
e4,4,2024-08-30,WMT,75.85
```

- `eventId`: Stable identifier (`e1`–`e4`).
- `sequence`: Replay processing order (`1`–`4`). I use sequence to define an unambiguous processing order when events share a trading date; it is not a claim about intraday market arrival order.
- `date`: Calendar trading date.
- `symbol`: Ticker symbol of the asset being updated.
- `price`: USD reference price.

> **Supported CSV Format & Validation**: The file reader accepts plain, unquoted UTF-8 comma-delimited rows with the exact headers shown above. Quotation marks (`"`) and quoted or multiline fields are unsupported; rows containing quotation marks, blank values, or unexpected column counts are rejected immediately with an error identifying the file and row number. Duplicate position symbols are rejected before replay begins.

> **Data Provenance & Source Review**: On 2026-09-15, I checked and updated these fixture prices against historical tables on StatMuse Money. All six fixture prices match the values displayed in StatMuse's `CLOSE` column for August 28, 29, and 30, 2024 (without claiming a price convention StatMuse has not defined). The share quantities (+10 AAPL, -20 WMT) remain illustrative synthetic holdings chosen to test signed portfolio arithmetic. See [`docs/DATA-PROVENANCE.md`](docs/DATA-PROVENANCE.md) for full provenance details.

### Valuation Rules & Reference Results

- **Replay Rule**: An incoming price event updates **only its own symbol’s latest price**. All other portfolio symbols retain their most recently observed price (or baseline price).
- **Marked-Value Model**: Position Value = $\text{quantity} \times \text{latest\_price}$; Portfolio Value = $\sum \text{Position Value}$; Cumulative P&L = $\text{Current Value} - \text{Baseline Value}$.
- **State Integrity & Execution Model**: Rejected events leave engine state unchanged. The engine currently processes events in a single thread.
- **Accounting Note**: This signed sum is a **simplified marked-value test**, not a complete account balance for a real short sale (which requires cash collateral, borrow fees, and separate short liability tracking). Detailed step-by-step arithmetic is documented in [`docs/EXAMPLE-CALCULATION.md`](docs/EXAMPLE-CALCULATION.md).

| Step | Date | Trigger Event | AAPL Price | WMT Price | Marked Value (USD) | Cumulative P&L (USD) |
|---|---|---|---|---|---|---|
| **Baseline (T0)** | 2024-08-28 | Initial Holdings | $224.61 | $74.72 | $751.70 | $0.00 |
| **Event `e1`** (seq 1) | 2024-08-29 | AAPL @ 227.88 | $227.88 | $74.72 | $784.40 | +$32.70 |
| **Event `e2`** (seq 2) | 2024-08-29 | WMT @ 75.05 | $227.88 | $75.05 | $777.80 | +$26.10 |
| **Event `e3`** (seq 3) | 2024-08-30 | AAPL @ 227.10 | $227.10 | $75.05 | $770.00 | +$18.30 |
| **Event `e4`** (seq 4) | 2024-08-30 | WMT @ 75.85 | $227.10 | $75.85 | $754.00 | +$2.30 |

## Next Steps

- **Output Formatting & Export**: Provide options for JSON or CSV report output formats alongside standard CLI stdout lines.
- **Performance & Large Feeds**: Evaluate streaming event processing for large datasets while preserving single-threaded replay determinism.
