package dev.esosa.risk;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable scenario definition representing hypothetical percentage price changes for selected symbols.
 *
 * <p>Percentage changes are expressed as decimal fractions (for example, {@code 0.05} represents +5%
 * and {@code -0.03} represents -3%). Symbols omitted from the scenario retain their current prices.
 *
 * <p>Any percentage change less than or equal to {@code -1.0} (-100%) is rejected because market prices
 * must remain strictly positive.
 */
public record Scenario(String name, Map<String, BigDecimal> percentageChanges) {

    public Scenario {
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(percentageChanges, "percentageChanges must not be null");

        Map<String, BigDecimal> copy = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : percentageChanges.entrySet()) {
            String symbol = entry.getKey();
            BigDecimal change = entry.getValue();

            Objects.requireNonNull(symbol, "symbol must not be null");
            if (symbol.isBlank()) {
                throw new IllegalArgumentException("symbol must not be blank");
            }
            Objects.requireNonNull(change, "percentage change for " + symbol + " must not be null");

            if (change.compareTo(new BigDecimal("-1.0")) <= 0) {
                throw new IllegalArgumentException(
                        "Percentage change for " + symbol + " must be strictly greater than -1.0 (-100%): " + change);
            }
            copy.put(symbol, change);
        }
        percentageChanges = Collections.unmodifiableMap(copy);
    }
}
