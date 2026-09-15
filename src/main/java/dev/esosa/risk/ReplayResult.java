package dev.esosa.risk;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable valuation outcome produced after an accepted price event is replayed.
 */
public record ReplayResult(
        String eventId,
        long sequence,
        LocalDate date,
        String symbol,
        BigDecimal eventPrice,
        BigDecimal portfolioValue,
        BigDecimal cumulativePnL) {

    public ReplayResult {
        Objects.requireNonNull(eventId, "eventId must not be null");
        if (eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (sequence <= 0) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(symbol, "symbol must not be null");
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        Objects.requireNonNull(eventPrice, "eventPrice must not be null");
        Objects.requireNonNull(portfolioValue, "portfolioValue must not be null");
        Objects.requireNonNull(cumulativePnL, "cumulativePnL must not be null");
    }
}
