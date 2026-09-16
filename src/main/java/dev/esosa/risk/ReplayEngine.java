package dev.esosa.risk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic replay engine that manages portfolio valuation against an ordered sequence
 * of market price events.
 */
public final class ReplayEngine {

    private final Map<String, Position> positions;
    private final Map<String, BigDecimal> latestPrices;
    private final Map<String, PriceEvent> acceptedEvents;
    private final BigDecimal baselineValue;
    private long lastAcceptedSequence;

    public ReplayEngine(Collection<Position> initialPositions) {
        Objects.requireNonNull(initialPositions, "initialPositions must not be null");
        if (initialPositions.isEmpty()) {
            throw new IllegalArgumentException("initialPositions must not be empty");
        }

        Map<String, Position> posMap = new LinkedHashMap<>();
        Map<String, BigDecimal> priceMap = new LinkedHashMap<>();
        BigDecimal initialValue = BigDecimal.ZERO;

        for (Position pos : initialPositions) {
            Objects.requireNonNull(pos, "Position entry must not be null");
            if (posMap.containsKey(pos.symbol())) {
                throw new IllegalArgumentException("Duplicate symbol in initial positions: " + pos.symbol());
            }
            posMap.put(pos.symbol(), pos);
            priceMap.put(pos.symbol(), pos.baselinePrice());
            initialValue = initialValue.add(pos.baselineValue());
        }

        this.positions = Collections.unmodifiableMap(posMap);
        this.latestPrices = priceMap;
        this.acceptedEvents = new LinkedHashMap<>();
        this.baselineValue = initialValue;
        this.lastAcceptedSequence = 0L;
    }

    /**
     * Validates and processes an incoming price event.
     *
     * Invariants enforced before state modification:
     * 1. Event must not be null.
     * 2. Event symbol must exist in the initial portfolio.
     * 3. Event price must be strictly positive.
     * 4. Event ID must not be blank.
     * 5. If event ID was previously accepted:
     *    - If identical in full content: ignored idempotently (returns Optional.empty()).
     *    - If contents differ: rejected with IllegalArgumentException.
     * 6. If event ID is genuinely new, sequence must strictly advance beyond lastAcceptedSequence.
     *
     * Candidate portfolio valuation, P&L, and ReplayResult are fully computed and validated
     * before mutating any internal state.
     *
     * @param event the price event to replay
     * @return Optional containing ReplayResult if accepted, or Optional.empty() if duplicate was ignored
     */
    public Optional<ReplayResult> process(PriceEvent event) {
        validateEvent(event);

        PriceEvent existing = acceptedEvents.get(event.eventId());
        if (existing != null) {
            if (existing.equals(event)) {
                // Repeated ID with identical full contents: ignore without changing state or producing another result
                return Optional.empty();
            } else {
                throw new IllegalArgumentException("Event ID already accepted with different contents: " + event.eventId());
            }
        }

        if (event.sequence() <= lastAcceptedSequence) {
            throw new IllegalArgumentException(
                    "Sequence must strictly advance. Received: " + event.sequence()
                            + ", last accepted: " + lastAcceptedSequence);
        }

        // Compute candidate marked value, P&L, and ReplayResult BEFORE modifying mutable state.
        // Uses incoming event price for its symbol, and current latestPrices for all others.
        BigDecimal candidatePortfolioValue = calculateCandidateValue(event);
        BigDecimal candidateCumulativePnL = candidatePortfolioValue.subtract(baselineValue);

        ReplayResult result = new ReplayResult(
                event.eventId(),
                event.sequence(),
                event.date(),
                event.symbol(),
                event.price(),
                candidatePortfolioValue,
                candidateCumulativePnL
        );

        // Commit state mutation only after candidate calculation and result validation succeed
        acceptedEvents.put(event.eventId(), event);
        lastAcceptedSequence = event.sequence();
        latestPrices.put(event.symbol(), event.price());

        return Optional.of(result);
    }

    private void validateEvent(PriceEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        if (event.eventId().isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (event.price().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("price must be strictly positive");
        }
        if (!positions.containsKey(event.symbol())) {
            throw new IllegalArgumentException("Unknown symbol in price event: " + event.symbol());
        }
    }

    private BigDecimal calculateCandidateValue(PriceEvent event) {
        BigDecimal total = BigDecimal.ZERO;
        for (Position pos : positions.values()) {
            BigDecimal priceToUse = pos.symbol().equals(event.symbol()) ? event.price() : latestPrices.get(pos.symbol());
            total = total.add(pos.marketValue(priceToUse));
        }
        return total;
    }

    private BigDecimal calculatePortfolioValue() {
        BigDecimal total = BigDecimal.ZERO;
        for (Position pos : positions.values()) {
            BigDecimal price = latestPrices.get(pos.symbol());
            total = total.add(pos.marketValue(price));
        }
        return total;
    }

    /**
     * Evaluates a named hypothetical scenario against the portfolio's current prices without modifying
     * internal engine state.
     *
     * <p>Validation rules enforced before any valuation:
     * 1. Scenario must not be null.
     * 2. All symbols specified in the scenario must exist in the portfolio.
     * 3. Percentage changes must be strictly greater than -1.0 (-100%).
     *
     * <p>Calculation rules:
     * 1. For each symbol in the scenario, the shocked price is calculated as
     *    {@code currentPrice * (1 + shift)} and rounded to 2 decimal places using {@link RoundingMode#HALF_UP}.
     * 2. Portfolio symbols omitted from the scenario retain their current marked price.
     * 3. The scenario portfolio value is the sum of position values under the scenario prices.
     * 4. The change in portfolio value is {@code scenarioValue - currentValue}.
     *
     * <p>This method is strictly read-only and idempotent: running it multiple times does not alter
     * the portfolio valuation, latest prices, sequence counter, or event history.
     *
     * @param scenario the hypothetical scenario to evaluate
     * @return an immutable ScenarioResult containing base value, scenario value, value change, and scenario prices
     */
    public ScenarioResult evaluateScenario(Scenario scenario) {
        Objects.requireNonNull(scenario, "scenario must not be null");

        for (String symbol : scenario.percentageChanges().keySet()) {
            if (!positions.containsKey(symbol)) {
                throw new IllegalArgumentException("Unknown symbol in scenario: " + symbol);
            }
        }

        Map<String, BigDecimal> scenarioPrices = new LinkedHashMap<>();
        for (String symbol : positions.keySet()) {
            BigDecimal currentPrice = latestPrices.get(symbol);
            BigDecimal shift = scenario.percentageChanges().get(symbol);
            if (shift != null) {
                BigDecimal shockedPrice = currentPrice.multiply(BigDecimal.ONE.add(shift))
                        .setScale(2, RoundingMode.HALF_UP);
                scenarioPrices.put(symbol, shockedPrice);
            } else {
                scenarioPrices.put(symbol, currentPrice);
            }
        }

        BigDecimal baseValue = getCurrentValue();
        BigDecimal scenarioValue = BigDecimal.ZERO;
        for (Position pos : positions.values()) {
            BigDecimal price = scenarioPrices.get(pos.symbol());
            scenarioValue = scenarioValue.add(pos.marketValue(price));
        }

        BigDecimal valueChange = scenarioValue.subtract(baseValue);

        return new ScenarioResult(
                scenario.name(),
                baseValue,
                scenarioValue,
                valueChange,
                scenarioPrices
        );
    }

    public ScenarioResult evaluateScenario(String name, Map<String, BigDecimal> percentageChanges) {
        return evaluateScenario(new Scenario(name, percentageChanges));
    }

    public BigDecimal getBaselineValue() {
        return baselineValue;
    }

    public BigDecimal getCurrentValue() {
        return calculatePortfolioValue();
    }

    public BigDecimal getCumulativePnL() {
        return getCurrentValue().subtract(baselineValue);
    }

    public BigDecimal getLatestPrice(String symbol) {
        BigDecimal price = latestPrices.get(symbol);
        if (price == null) {
            throw new IllegalArgumentException("Unknown symbol: " + symbol);
        }
        return price;
    }

    public long getLastAcceptedSequence() {
        return lastAcceptedSequence;
    }

    public Set<String> getAcceptedEventIds() {
        return Collections.unmodifiableSet(acceptedEvents.keySet());
    }

    public Map<String, Position> getPositions() {
        return positions;
    }
}
