# ValuTrail

A usable, traceable portfolio-valuation solution built on Java 17, designed to replay ordered market price events across signed portfolios and compute deterministic marked values and P&L.

> **Current State**: The repository contains the tested Java 17 Maven foundation and example fixtures with hand-checked calculations and prices flagged for source review, not independently verified closing quotes. Replay execution logic is planned for the next step; no portfolio replay classes, streaming engines, or market APIs are implemented yet.

## Prerequisites

- **Java Development Kit (JDK)**: Java 17+ (configured with `--release 17`)
- **Apache Maven**: 3.8+
- **Git**: 2.x+

## Verified Commands

### Build & Test
Compile the project and run the automated test suite:
```bash
mvn -B test
```

### Run Application Entrypoint
Execute the entrypoint `dev.esosa.risk.Main` via Maven:
```bash
mvn compile exec:java
```
Expected output:
```text
Risk Replay Engine — project initialized
```

## Replay Example Contract & Calculation Model

The first functional release replays ordered price events against an initial synthetic signed portfolio. Example files are in [`examples/`](examples/).

### Example Data Files

#### 1. Initial Holdings: [`examples/positions.csv`](examples/positions.csv)
Defines starting holdings and baseline mark prices at the initial valuation date.

```csv
symbol,quantity,baseline_price
AAPL,10,224.61
WMT,-20,74.90
```

- `symbol`: Equity ticker symbol.
- `quantity`: Signed share quantity held (+10 long AAPL, -20 short WMT). These are illustrative, synthetic holdings chosen to test a mixed long/short portfolio of differing magnitudes.
- `baseline_price`: Starting USD price per share as of 2024-08-28.

#### 2. Ordered Price Events: [`examples/prices.csv`](examples/prices.csv)
Defines the sequence of incoming market price updates to be replayed in order.

```csv
eventId,sequence,date,symbol,price
e1,1,2024-08-29,AAPL,227.88
e2,2,2024-08-29,WMT,75.23
e3,3,2024-08-30,AAPL,227.10
e4,4,2024-08-30,WMT,76.03
```

- `eventId`: Stable identifier (`e1`–`e4`).
- `sequence`: Replay execution order (`1`–`4`). Note: `sequence` is our **chosen processing order** for the two observations sharing each trading date, not an empirical claim about intraday market arrival order.
- `date`: Calendar trading date.
- `symbol`: Ticker symbol of the asset being updated.
- `price`: USD reference price.

> **Data Provenance & Source Review**: The six prices are sourced from public aggregator queries on StatMuse for late August 2024. Because provider conventions vary across aggregators (e.g. unadjusted vs split-adjusted closes vs intraday points), these figures are flagged for source review and treated as illustrative reference inputs with hand-checked calculations, not independently verified closing quotes. See [`docs/DATA-PROVENANCE.md`](docs/DATA-PROVENANCE.md) for direct links and details.

### Valuation Rules & Reference Results

- **Replay Rule**: An incoming price event updates **only its own symbol’s latest price**. All other portfolio symbols retain their most recently observed price (or baseline price).
- **Marked-Value Model**: Position Value = $\text{quantity} \times \text{latest\_price}$; Portfolio Value = $\sum \text{Position Value}$; Cumulative P&L = $\text{Current Value} - \text{Baseline Value}$.
- **Accounting Note**: This signed sum is a **simplified marked-value test**, not a complete account balance for a real short sale (which requires cash collateral, borrow fees, and separate short liability tracking). Detailed step-by-step arithmetic is documented in [`docs/EXAMPLE-CALCULATION.md`](docs/EXAMPLE-CALCULATION.md).

| Step | Date | Trigger Event | AAPL Price | WMT Price | Marked Value (USD) | Cumulative P&L (USD) |
|---|---|---|---|---|---|---|
| **Baseline (T0)** | 2024-08-28 | Initial Holdings | $224.61 | $74.90 | $748.10 | $0.00 |
| **Event `e1`** (seq 1) | 2024-08-29 | AAPL @ 227.88 | $227.88 | $74.90 | $780.80 | +$32.70 |
| **Event `e2`** (seq 2) | 2024-08-29 | WMT @ 75.23 | $227.88 | $75.23 | $774.20 | +$26.10 |
| **Event `e3`** (seq 3) | 2024-08-30 | AAPL @ 227.10 | $227.10 | $75.23 | $766.40 | +$18.30 |
| **Event `e4`** (seq 4) | 2024-08-30 | WMT @ 76.03 | $227.10 | $76.03 | $750.40 | +$2.30 |

## Next Steps

- **Replay Implementation**: Implement domain classes and an event loader to ingest `examples/positions.csv` and `examples/prices.csv`, replaying events by `sequence` and verifying output against the reference table.
