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
| `WMT` | 2024-08-28 | Baseline | Baseline Mark | $74.90 | $74.72 | +$0.18 | Illustrative |
| `AAPL` | 2024-08-29 | `e1` | Price Event | $227.88 | $227.88 | $0.00 | Verified |
| `WMT` | 2024-08-29 | `e2` | Price Event | $75.23 | $75.05 | +$0.18 | Illustrative |
| `AAPL` | 2024-08-30 | `e3` | Price Event | $227.10 | $227.10 | $0.00 | Verified |
| `WMT` | 2024-08-30 | `e4` | Price Event | $76.03 | $75.85 | +$0.18 | Illustrative |

### Audit Notes

1. **Apple (`AAPL`) Observations**:
   - All three fixture prices ($224.61, $227.88, and $227.10) match the values displayed in StatMuse's `CLOSE` column.
   - Note: StatMuse displays these values under the column header `CLOSE`, but does not document whether they represent unadjusted closing quotes, dividend-adjusted closes, or composite tape figures.

2. **Walmart (`WMT`) Mismatches**:
   - StatMuse displays `CLOSE` values of $74.72, $75.05, and $75.85 for August 28, 29, and 30, 2024.
   - The fixture prices ($74.90, $75.23, and $76.03) are each higher by a constant difference of +$0.18.
   - Because the source page does not document the underlying cause of this difference (e.g. dividend adjustment, unadjusted feed difference, or alternate tape), we do not speculate.
   - The three WMT prices are immediately labeled as **illustrative inputs**.

3. **Policy on Fixture Updates**:
   - Retaining inaccurate prices is not a permanent policy.
   - Any update to fixture prices must update the fixture CSV files, the hand calculations in [`docs/EXAMPLE-CALCULATION.md`](EXAMPLE-CALCULATION.md), and the test assertions in `ReplayEngineTest` together in a single reviewed change.

4. **Synthetic Holdings**:
   - The share quantities (+10 AAPL, -20 WMT) in [`examples/positions.csv`](../examples/positions.csv) are synthetic, illustrative holdings chosen to test signed portfolio arithmetic.

5. **Scope of Automated Testing**:
   - Automated tests in `dev.esosa.risk.ReplayEngineTest` verify that the replay engine executes sequence ordering, pre-mutation validation, and portfolio valuation arithmetic correctly against the fixture. Passing tests verify software behavior, not external market data accuracy.
