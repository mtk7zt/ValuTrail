# Step-by-Step Example Calculation

This document details the exact hand calculations for the example replay scenario in ValuTrail.

## Valuation Model & Limitations

$$\text{Position Value} = \text{quantity} \times \text{latest\_price}$$
$$\text{Portfolio Value} = \sum \text{Position Value}$$
$$\text{Portfolio P\&L} = \text{Current Portfolio Value} - \text{Baseline Portfolio Value}$$

> **Important Accounting Clarification**: The signed sum ($\sum \text{quantity} \times \text{price}$) is a **simplified marked-value test** used to verify algebraic correctness of signed holdings in the engine. It is **not** a complete accounting ledger for real-world short sales (which involves cash proceeds, borrowing fees, margin requirements, short liability obligations, and collateral interest).

---

## Portfolio Holdings & Baseline (T0)

- **Holdings**:
  - `AAPL`: `+10` shares (long)
  - `WMT`: `-20` shares (short)
- **Baseline Trading Date**: 2024-08-28
- **Baseline Prices**: AAPL = $224.61 USD, WMT = $74.72 USD

### Baseline Valuation
- **AAPL Position Value**: $10 \times 224.61 = 2,246.10\text{ USD}$
- **WMT Position Value**: $-20 \times 74.72 = -1,494.40\text{ USD}$
- **Baseline Portfolio Value**: $2,246.10 + (-1,494.40) = 751.70\text{ USD}$
- **Baseline Cumulative P&L**: $751.70 - 751.70 = 0.00\text{ USD}$

---

## Event-by-Event Replay

Each event updates **only its own symbol’s latest price**. All other symbols maintain their previous marked price.

### Event `e1` (Sequence 1) — 2024-08-29, `AAPL` @ $227.88
- **Price State**: AAPL = $227.88 *(updated)*, WMT = $74.72 *(retained from baseline)*
- **AAPL Position Value**: $10 \times 227.88 = 2,278.80\text{ USD}$
- **WMT Position Value**: $-20 \times 74.72 = -1,494.40\text{ USD}$
- **Portfolio Marked Value**: $2,278.80 - 1,494.40 = 784.40\text{ USD}$
- **Cumulative P&L**: $784.40 - 751.70 = +32.70\text{ USD}$
- *(Step Delta: $10 \times (227.88 - 224.61) = +32.70\text{ USD}$)*

---

### Event `e2` (Sequence 2) — 2024-08-29, `WMT` @ $75.05
- **Price State**: AAPL = $227.88 *(retained from e1)*, WMT = $75.05 *(updated)*
- **AAPL Position Value**: $10 \times 227.88 = 2,278.80\text{ USD}$
- **WMT Position Value**: $-20 \times 75.05 = -1,501.00\text{ USD}$
- **Portfolio Marked Value**: $2,278.80 - 1,501.00 = 777.80\text{ USD}$
- **Cumulative P&L**: $777.80 - 751.70 = +26.10\text{ USD}$
- *(Step Delta: $-20 \times (75.05 - 74.72) = -6.60\text{ USD}$; $32.70 - 6.60 = +26.10\text{ USD}$)*

---

### Event `e3` (Sequence 3) — 2024-08-30, `AAPL` @ $227.10
- **Price State**: AAPL = $227.10 *(updated)*, WMT = $75.05 *(retained from e2)*
- **AAPL Position Value**: $10 \times 227.10 = 2,271.00\text{ USD}$
- **WMT Position Value**: $-20 \times 75.05 = -1,501.00\text{ USD}$
- **Portfolio Marked Value**: $2,271.00 - 1,501.00 = 770.00\text{ USD}$
- **Cumulative P&L**: $770.00 - 751.70 = +18.30\text{ USD}$
- *(Step Delta: $10 \times (227.10 - 227.88) = -7.80\text{ USD}$; $26.10 - 7.80 = +18.30\text{ USD}$)*

---

### Event `e4` (Sequence 4) — 2024-08-30, `WMT` @ $75.85
- **Price State**: AAPL = $227.10 *(retained from e3)*, WMT = $75.85 *(updated)*
- **AAPL Position Value**: $10 \times 227.10 = 2,271.00\text{ USD}$
- **WMT Position Value**: $-20 \times 75.85 = -1,517.00\text{ USD}$
- **Portfolio Marked Value**: $2,271.00 - 1,517.00 = 754.00\text{ USD}$
- **Cumulative P&L**: $754.00 - 751.70 = +2.30\text{ USD}$
- *(Step Delta: $-20 \times (75.85 - 75.05) = -16.00\text{ USD}$; $18.30 - 16.00 = +2.30\text{ USD}$)*

---

## Sequence Ordering Note

The `sequence` column values (1 through 4) represent our **statically chosen replay processing order** for deterministic execution. Because both `e1` and `e2` occur on trading date `2024-08-29`, and `e3` and `e4` occur on `2024-08-30`, `sequence` defines the tie-breaking execution sequence. It does not represent an empirical claim about intraday market arrival timestamps.
