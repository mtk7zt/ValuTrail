package dev.esosa.risk;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable price update event for a specific symbol on a given trading date.
 */
public record PriceEvent(
        String eventId,
        long sequence,
        LocalDate date,
        String symbol,
        BigDecimal price) {

    public PriceEvent {
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
        Objects.requireNonNull(price, "price must not be null");
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("price must be strictly positive");
        }
    }
}
