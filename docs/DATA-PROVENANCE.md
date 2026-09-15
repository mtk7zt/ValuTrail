# Data Provenance & Source Review

This document tracks the origin, provider conventions, and review status of reference market data used in ValuTrail test fixtures.

## Reference Price Fixture

The calculation examples and test fixtures use six price observations across Apple Inc. (`AAPL`) and Walmart Inc. (`WMT`):

| Symbol | Trading Date | Fixture Price (USD) | Fixture Role | Event ID |
|---|---|---|---|---|
| `AAPL` | 2024-08-28 | 224.61 | Baseline Mark | N/A (Baseline) |
| `WMT` | 2024-08-28 | 74.90 | Baseline Mark | N/A (Baseline) |
| `AAPL` | 2024-08-29 | 227.88 | Price Event | `e1` |
| `WMT` | 2024-08-29 | 75.23 | Price Event | `e2` |
| `AAPL` | 2024-08-30 | 227.10 | Price Event | `e3` |
| `WMT` | 2024-08-30 | 76.03 | Price Event | `e4` |

---

## Provider & URL References

- **Provider**: Aggregator financial query summaries via [StatMuse Money](https://www.statmuse.com/money).
- **Direct Search Links**:
  - AAPL Historical Price Queries: [StatMuse AAPL Search](https://www.statmuse.com/money/ask?q=aapl+closing+price+august+2024)
  - WMT Historical Price Queries: [StatMuse WMT Search](https://www.statmuse.com/money/ask?q=wmt+closing+price+august+2024)

---

## Provider Convention Status & Source Review Flag

> **Source Review Flag**: The six figures above are **flagged for source review**. 
> 
> While retrieved via public aggregator summaries for late August 2024 trading dates, financial web providers vary in whether reported figures represent unadjusted regular-session closes, dividend/split-adjusted closes, composite tape closes, or intraday sample points. 
> 
> Because primary exchange SIP tape data was not independently audited for each timestamp, these prices are treated as **illustrative, realistic reference inputs** for deterministic calculation verification rather than certified market quotes. They are preserved unchanged to ensure exact, reproducible hand arithmetic across tests and documentation.
