package dev.esosa.risk;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable valuation outcome of evaluating a scenario against current portfolio prices.
 *
 * @param scenarioName   the identifier or name of the evaluated scenario
 * @param baseValue      the marked portfolio value immediately prior to scenario evaluation
 * @param scenarioValue  the marked portfolio value under the scenario prices
 * @param valueChange    the net change in portfolio value ({@code scenarioValue - baseValue})
 * @param scenarioPrices an unmodifiable map of the effective prices used for each portfolio symbol
 */
public record ScenarioResult(
        String scenarioName,
        BigDecimal baseValue,
        BigDecimal scenarioValue,
        BigDecimal valueChange,
        Map<String, BigDecimal> scenarioPrices) {

    public ScenarioResult {
        Objects.requireNonNull(scenarioName, "scenarioName must not be null");
        if (scenarioName.isBlank()) {
            throw new IllegalArgumentException("scenarioName must not be blank");
        }
        Objects.requireNonNull(baseValue, "baseValue must not be null");
        Objects.requireNonNull(scenarioValue, "scenarioValue must not be null");
        Objects.requireNonNull(valueChange, "valueChange must not be null");
        Objects.requireNonNull(scenarioPrices, "scenarioPrices must not be null");

        Map<String, BigDecimal> copy = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : scenarioPrices.entrySet()) {
            String symbol = entry.getKey();
            BigDecimal price = entry.getValue();
            Objects.requireNonNull(symbol, "symbol in scenarioPrices must not be null");
            if (symbol.isBlank()) {
                throw new IllegalArgumentException("symbol in scenarioPrices must not be blank");
            }
            Objects.requireNonNull(price, "price for " + symbol + " must not be null");
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("price for " + symbol + " must be strictly positive");
            }
            copy.put(symbol, price);
        }
        scenarioPrices = Collections.unmodifiableMap(copy);
    }
}
