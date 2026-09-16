# Data Provenance & Verification Record

This document records the source audit for market prices and synthetic holdings used in ValuTrail test fixtures. The project-wide review rules and provenance criteria are defined in [`CONTRIBUTING.md`](../CONTRIBUTING.md#data-provenance-review) and [`docs/DECISIONS.md`](DECISIONS.md#adr-003-data-provenance-standard-for-external-values-and-test-fixtures).

## Six-Price Audit: AAPL & WMT Fixtures

On 2026-09-15, the six prices used in [`examples/positions.csv`](../examples/positions.csv) and [`examples/prices.csv`](../examples/prices.csv) were compared against historical tables on **StatMuse Money**:
- AAPL table: [StatMuse Apple Stock Price August 2024](https://www.statmuse.com/money/ask/apple-stock-price-august-2024)
- WMT table: [StatMuse Walmart Stock Price August 2024](https://www.statmuse.com/money/ask/walmart-stock-price-august-2024)

### Comparison Table

| Symbol | Date | Event ID | Role | Fixture Price | StatMuse `CLOSE` | Difference | Status |
|---|---|---|---|---|---|---|---|
| `AAPL` | 2024-08-28 | Baseline | Baseline Mark | $224.61 | $224.61 | $0.00 | Verified |
| `WMT` | 2024-08-28 | Baseline | Baseline Mark | $74.72 | $74.72 | $0.00 | Verified |
| `AAPL` | 2024-08-29 | `e1` | Price Event | $227.88 | $227.88 | $0.00 | Verified |
| `WMT` | 2024-08-29 | `e2` | Price Event | $75.05 | $75.05 | $0.00 | Verified |
| `AAPL` | 2024-08-30 | `e3` | Price Event | $227.10 | $227.10 | $0.00 | Verified |
| `WMT` | 2024-08-30 | `e4` | Price Event | $75.85 | $75.85 | $0.00 | Verified |

### Audit Notes

1. **Source Matching**:
   - All six fixture prices ($224.61, $227.88, and $227.10 for AAPL; $74.72, $75.05, and $75.85 for WMT) match the values displayed in StatMuse's `CLOSE` column for their respective trading dates.

2. **StatMuse Price Convention**:
   - StatMuse displays these values under the column header `CLOSE`. However, StatMuse does not publish documentation defining whether these numbers represent regular-session unadjusted closing quotes, dividend-adjusted closing prices, or composite volume-weighted prints. We state that the figures match StatMuse's displayed `CLOSE` column without asserting a price convention StatMuse has not defined.

3. **Update History**:
   - Prior to this update, the WMT fixture used illustrative prices ($74.90, $75.23, $76.03) that differed by +$0.18 from StatMuse's displayed `CLOSE` table. Following ADR-003, the fixture CSV files, step-by-step hand calculations in [`docs/EXAMPLE-CALCULATION.md`](EXAMPLE-CALCULATION.md), and unit test assertions in `ReplayEngineTest` were updated together in a single reviewed change.

4. **Synthetic Holdings**:
   - The signed share quantities (+10 AAPL, -20 WMT) in [`examples/positions.csv`](../examples/positions.csv) remain synthetic, illustrative inputs chosen to test signed portfolio arithmetic.

5. **Scope of Automated Testing**:
   - Automated tests in `dev.esosa.risk.ReplayEngineTest` verify that the replay engine executes sequence ordering, pre-mutation validation, and portfolio valuation arithmetic correctly against the fixture. Passing tests verify software behavior, not external market data accuracy.

---

## Scenario Fixture Audit: `examples/scenario.csv`

The scenario definition in [`examples/scenario.csv`](../examples/scenario.csv) defines a hypothetical price shock for what-if valuation:

| Scenario Name | Symbol | Percentage Shift | Effective Factor | Role | Status |
|---|---|---|---|---|---|
| `Tech Surge` | `AAPL` | `+0.05` (+5%) | 1.05 | Illustrative Shock | Synthetic Input |

### Audit Notes

1. **Illustrative Classification**:
   - Following ADR-003, the +5% price shift for AAPL is classified as an **illustrative synthetic input**, not an external market quote or empirical forecast. It represents an instantaneous, user-chosen hypothetical price shock designed to verify that the replay engine correctly computes scenario marked values and value deltas on post-replay holdings.
2. **Unshifted Symbols**:
   - Portfolio symbols omitted from `examples/scenario.csv` (specifically `WMT`) retain their current marked prices ($75.85 after event `e4`).

