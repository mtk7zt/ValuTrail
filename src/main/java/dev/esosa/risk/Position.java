package dev.esosa.risk;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable representation of an equity position held in a portfolio.
 */
public record Position(String symbol, BigDecimal quantity, BigDecimal baselinePrice) {

    public Position {
        Objects.requireNonNull(symbol, "symbol must not be null");
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        Objects.requireNonNull(quantity, "quantity must not be null");
        Objects.requireNonNull(baselinePrice, "baselinePrice must not be null");
        if (baselinePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("baselinePrice must be strictly positive");
        }
    }

    public BigDecimal marketValue(BigDecimal price) {
        Objects.requireNonNull(price, "price must not be null");
        return quantity.multiply(price);
    }

    public BigDecimal baselineValue() {
        return quantity.multiply(baselinePrice);
    }
}
