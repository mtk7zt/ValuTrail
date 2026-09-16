package dev.esosa.risk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayEngineTest {

    private List<Position> createExamplePositions() {
        return List.of(
                new Position("AAPL", new BigDecimal("10"), new BigDecimal("224.61")),
                new Position("WMT", new BigDecimal("-20"), new BigDecimal("74.72"))
        );
    }

    private List<PriceEvent> createExampleEvents() {
        return List.of(
                new PriceEvent("e1", 1, LocalDate.of(2024, 8, 29), "AAPL", new BigDecimal("227.88")),
                new PriceEvent("e2", 2, LocalDate.of(2024, 8, 29), "WMT", new BigDecimal("75.05")),
                new PriceEvent("e3", 3, LocalDate.of(2024, 8, 30), "AAPL", new BigDecimal("227.10")),
                new PriceEvent("e4", 4, LocalDate.of(2024, 8, 30), "WMT", new BigDecimal("75.85"))
        );
    }

    @Test
    @DisplayName("Replays example scenario producing exact reference values and P&L")
    void replaysExampleScenarioAccurately() {
        ReplayEngine engine = new ReplayEngine(createExamplePositions());

        // Assert baseline value (751.70)
        assertEquals(0, new BigDecimal("751.70").compareTo(engine.getBaselineValue()));
        assertEquals(0, new BigDecimal("751.70").compareTo(engine.getCurrentValue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(engine.getCumulativePnL()));

        List<PriceEvent> events = createExampleEvents();

        // Event e1: AAPL @ 227.88
        Optional<ReplayResult> r1 = engine.process(events.get(0));
        assertTrue(r1.isPresent());
        assertEquals(0, new BigDecimal("784.40").compareTo(r1.get().portfolioValue()));
        assertEquals(0, new BigDecimal("32.70").compareTo(r1.get().cumulativePnL()));
        assertEquals(0, new BigDecimal("227.88").compareTo(engine.getLatestPrice("AAPL")));
        assertEquals(0, new BigDecimal("74.72").compareTo(engine.getLatestPrice("WMT"))); // WMT unchanged

        // Event e2: WMT @ 75.05 (same date 2024-08-29, sequence 2)
        Optional<ReplayResult> r2 = engine.process(events.get(1));
        assertTrue(r2.isPresent());
        assertEquals(0, new BigDecimal("777.80").compareTo(r2.get().portfolioValue()));
        assertEquals(0, new BigDecimal("26.10").compareTo(r2.get().cumulativePnL()));
        assertEquals(0, new BigDecimal("227.88").compareTo(engine.getLatestPrice("AAPL"))); // AAPL unchanged
        assertEquals(0, new BigDecimal("75.05").compareTo(engine.getLatestPrice("WMT")));

        // Event e3: AAPL @ 227.10
        Optional<ReplayResult> r3 = engine.process(events.get(2));
        assertTrue(r3.isPresent());
        assertEquals(0, new BigDecimal("770.00").compareTo(r3.get().portfolioValue()));
        assertEquals(0, new BigDecimal("18.30").compareTo(r3.get().cumulativePnL()));
        assertEquals(0, new BigDecimal("227.10").compareTo(engine.getLatestPrice("AAPL")));
        assertEquals(0, new BigDecimal("75.05").compareTo(engine.getLatestPrice("WMT"))); // WMT unchanged

        // Event e4: WMT @ 75.85
        Optional<ReplayResult> r4 = engine.process(events.get(3));
        assertTrue(r4.isPresent());
        assertEquals(0, new BigDecimal("754.00").compareTo(r4.get().portfolioValue()));
        assertEquals(0, new BigDecimal("2.30").compareTo(r4.get().cumulativePnL()));
        assertEquals(0, new BigDecimal("227.10").compareTo(engine.getLatestPrice("AAPL"))); // AAPL unchanged
        assertEquals(0, new BigDecimal("75.85").compareTo(engine.getLatestPrice("WMT")));
        assertEquals(4L, engine.getLastAcceptedSequence());
    }

    @Test
    @DisplayName("Freshly constructed identical duplicate event is ignored without state mutation")
    void freshlyConstructedIdenticalDuplicateIsIgnored() {
        ReplayEngine engine = new ReplayEngine(createExamplePositions());
        PriceEvent e1 = new PriceEvent("e1", 1, LocalDate.of(2024, 8, 29), "AAPL", new BigDecimal("227.88"));
        Optional<ReplayResult> r1 = engine.process(e1);
        assertTrue(r1.isPresent());

        // Separate, freshly constructed instance with identical full record contents
        PriceEvent e1FreshDuplicate = new PriceEvent("e1", 1, LocalDate.of(2024, 8, 29), "AAPL", new BigDecimal("227.88"));
        Optional<ReplayResult> rDuplicate = engine.process(e1FreshDuplicate);

        assertFalse(rDuplicate.isPresent(), "Freshly constructed duplicate must be ignored idempotently");
        assertEquals(0, new BigDecimal("784.40").compareTo(engine.getCurrentValue()));
        assertEquals(0, new BigDecimal("32.70").compareTo(engine.getCumulativePnL()));
        assertEquals(1L, engine.getLastAcceptedSequence());
        assertEquals(1, engine.getAcceptedEventIds().size());
        assertTrue(engine.getAcceptedEventIds().contains("e1"));
    }

    @Test
    @DisplayName("Conflicting duplicate event is rejected and leaves engine state completely unchanged")
    void conflictingDuplicateLeavesStateUnchanged() {
        ReplayEngine engine = new ReplayEngine(createExamplePositions());
        PriceEvent e1 = new PriceEvent("e1", 1, LocalDate.of(2024, 8, 29), "AAPL", new BigDecimal("227.88"));
        engine.process(e1);

        BigDecimal valueBefore = engine.getCurrentValue();
        BigDecimal pnlBefore = engine.getCumulativePnL();
        long seqBefore = engine.getLastAcceptedSequence();
        BigDecimal priceAAPLBefore = engine.getLatestPrice("AAPL");
        BigDecimal priceWMTBefore = engine.getLatestPrice("WMT");
        Set<String> acceptedBefore = Set.copyOf(engine.getAcceptedEventIds());

        // Conflicting price
        PriceEvent conflictPrice = new PriceEvent("e1", 1, LocalDate.of(2024, 8, 29), "AAPL", new BigDecimal("999.99"));
        assertThrows(IllegalArgumentException.class, () -> engine.process(conflictPrice));

        // Conflicting sequence
        PriceEvent conflictSeq = new PriceEvent("e1", 2, LocalDate.of(2024, 8, 29), "AAPL", new BigDecimal("227.88"));
        assertThrows(IllegalArgumentException.class, () -> engine.process(conflictSeq));

        // Conflicting date
        PriceEvent conflictDate = new PriceEvent("e1", 1, LocalDate.of(2024, 8, 30), "AAPL", new BigDecimal("227.88"));
        assertThrows(IllegalArgumentException.class, () -> engine.process(conflictDate));

        // Conflicting symbol
        PriceEvent conflictSymbol = new PriceEvent("e1", 1, LocalDate.of(2024, 8, 29), "WMT", new BigDecimal("227.88"));
        assertThrows(IllegalArgumentException.class, () -> engine.process(conflictSymbol));

        // Assert all getters remain completely identical
        assertEquals(valueBefore, engine.getCurrentValue());
        assertEquals(pnlBefore, engine.getCumulativePnL());
        assertEquals(seqBefore, engine.getLastAcceptedSequence());
        assertEquals(priceAAPLBefore, engine.getLatestPrice("AAPL"));
        assertEquals(priceWMTBefore, engine.getLatestPrice("WMT"));
        assertEquals(acceptedBefore, engine.getAcceptedEventIds());
    }

    @Test
    @DisplayName("Accepted ReplayResult matches current engine getters exactly")
    void acceptedResultMatchesEngineGetters() {
        ReplayEngine engine = new ReplayEngine(createExamplePositions());
        PriceEvent e1 = new PriceEvent("e1", 1, LocalDate.of(2024, 8, 29), "AAPL", new BigDecimal("227.88"));

        ReplayResult result = engine.process(e1).orElseThrow();

        assertEquals(result.portfolioValue(), engine.getCurrentValue());
        assertEquals(result.cumulativePnL(), engine.getCumulativePnL());
        assertEquals(result.eventPrice(), engine.getLatestPrice(result.symbol()));
        assertEquals(result.sequence(), engine.getLastAcceptedSequence());
        assertTrue(engine.getAcceptedEventIds().contains(result.eventId()));
    }

    @Test
    @DisplayName("Validates and rejects unknown symbol without changing state")
    void rejectsUnknownSymbol() {
        ReplayEngine engine = new ReplayEngine(createExamplePositions());
        PriceEvent invalidEvent = new PriceEvent("e_unknown", 1, LocalDate.of(2024, 8, 29), "MSFT", new BigDecimal("400.00"));

        assertThrows(IllegalArgumentException.class, () -> engine.process(invalidEvent));
        assertEquals(0, new BigDecimal("751.70").compareTo(engine.getCurrentValue()));
        assertEquals(0L, engine.getLastAcceptedSequence());
        assertTrue(engine.getAcceptedEventIds().isEmpty());
    }

    @Test
    @DisplayName("Validates and rejects non-positive price")
    void rejectsNonPositivePrice() {
        assertThrows(IllegalArgumentException.class, () ->
                new PriceEvent("e_zero", 1, LocalDate.of(2024, 8, 29), "AAPL", BigDecimal.ZERO)
        );
        assertThrows(IllegalArgumentException.class, () ->
                new PriceEvent("e_neg", 1, LocalDate.of(2024, 8, 29), "AAPL", new BigDecimal("-10.00"))
        );
    }

    @Test
    @DisplayName("Validates and rejects blank event ID")
    void rejectsBlankEventId() {
        assertThrows(IllegalArgumentException.class, () ->
                new PriceEvent("   ", 1, LocalDate.of(2024, 8, 29), "AAPL", new BigDecimal("220.00"))
        );
    }

    @Test
    @DisplayName("Rejects a genuinely new event whose sequence does not advance")
    void rejectsNonAdvancingSequenceForNewEvent() {
        ReplayEngine engine = new ReplayEngine(createExamplePositions());
        PriceEvent e1 = new PriceEvent("e1", 5, LocalDate.of(2024, 8, 29), "AAPL", new BigDecimal("227.88"));
        engine.process(e1);

        // Sequence equal to last accepted (5)
        PriceEvent eEqual = new PriceEvent("e2", 5, LocalDate.of(2024, 8, 29), "WMT", new BigDecimal("75.05"));
        assertThrows(IllegalArgumentException.class, () -> engine.process(eEqual));

        // Sequence less than last accepted (4 < 5)
        PriceEvent eLess = new PriceEvent("e3", 4, LocalDate.of(2024, 8, 29), "WMT", new BigDecimal("75.05"));
        assertThrows(IllegalArgumentException.class, () -> engine.process(eLess));

        assertEquals(5L, engine.getLastAcceptedSequence());
    }

    @Test
    @DisplayName("Rejects invalid event, then accepts valid one and matches clean replay exactly")
    void rejectingInvalidEventPreservesEngineIntegrity() {
        // Clean engine running e1 then e2
        ReplayEngine cleanEngine = new ReplayEngine(createExamplePositions());
        PriceEvent e1 = new PriceEvent("e1", 1, LocalDate.of(2024, 8, 29), "AAPL", new BigDecimal("227.88"));
        PriceEvent e2 = new PriceEvent("e2", 2, LocalDate.of(2024, 8, 29), "WMT", new BigDecimal("75.05"));
        cleanEngine.process(e1);
        ReplayResult cleanResult = cleanEngine.process(e2).orElseThrow();

        // Dirty attempt engine: processes e1, fails on invalid events, then accepts e2
        ReplayEngine dirtyEngine = new ReplayEngine(createExamplePositions());
        dirtyEngine.process(e1);

        // Attempt 1: Unknown symbol
        assertThrows(IllegalArgumentException.class, () ->
                dirtyEngine.process(new PriceEvent("bad1", 2, LocalDate.of(2024, 8, 29), "UNKNOWN", new BigDecimal("100.00")))
        );

        // Attempt 2: Non-advancing sequence
        assertThrows(IllegalArgumentException.class, () ->
                dirtyEngine.process(new PriceEvent("bad2", 1, LocalDate.of(2024, 8, 29), "WMT", new BigDecimal("75.05")))
        );

        // Attempt 3: Same ID with changed content
        assertThrows(IllegalArgumentException.class, () ->
                dirtyEngine.process(new PriceEvent("e1", 2, LocalDate.of(2024, 8, 29), "AAPL", new BigDecimal("999.00")))
        );

        // Now process valid e2
        ReplayResult dirtyResult = dirtyEngine.process(e2).orElseThrow();

        // Both engines must be in the exact same state
        assertEquals(cleanResult.portfolioValue(), dirtyResult.portfolioValue());
        assertEquals(cleanResult.cumulativePnL(), dirtyResult.cumulativePnL());
        assertEquals(cleanEngine.getCurrentValue(), dirtyEngine.getCurrentValue());
        assertEquals(cleanEngine.getCumulativePnL(), dirtyEngine.getCumulativePnL());
        assertEquals(cleanEngine.getLastAcceptedSequence(), dirtyEngine.getLastAcceptedSequence());
        assertEquals(cleanEngine.getLatestPrice("AAPL"), dirtyEngine.getLatestPrice("AAPL"));
        assertEquals(cleanEngine.getLatestPrice("WMT"), dirtyEngine.getLatestPrice("WMT"));
    }

    @Test
    @DisplayName("Evaluates scenario on baseline portfolio using hand-checked AAPL and WMT shifts")
    void evaluatesScenarioOnBaselinePortfolioWithHandCheckedValues() {
        ReplayEngine engine = new ReplayEngine(createExamplePositions());

        // Baseline: AAPL=224.61 (10 shares), WMT=74.72 (-20 shares), baseValue=751.70
        // Scenario "Tech Rally / Retail Dip":
        // AAPL shift: +10% (0.10) -> 224.61 * 1.10 = 247.071 -> 247.07
        // WMT shift:  -5% (-0.05) -> 74.72 * 0.95 = 70.984 -> 70.98
        // Marked Values:
        // AAPL: 10 * 247.07 = 2470.70
        // WMT: -20 * 70.98 = -1419.60
        // Scenario Value: 2470.70 - 1419.60 = 1051.10
        // Value Change: 1051.10 - 751.70 = +299.40
        Scenario scenario = new Scenario("Tech Rally / Retail Dip", Map.of(
                "AAPL", new BigDecimal("0.10"),
                "WMT", new BigDecimal("-0.05")
        ));

        ScenarioResult result = engine.evaluateScenario(scenario);

        assertEquals("Tech Rally / Retail Dip", result.scenarioName());
        assertEquals(0, new BigDecimal("751.70").compareTo(result.baseValue()));
        assertEquals(0, new BigDecimal("1051.10").compareTo(result.scenarioValue()));
        assertEquals(0, new BigDecimal("299.40").compareTo(result.valueChange()));
        assertEquals(0, new BigDecimal("247.07").compareTo(result.scenarioPrices().get("AAPL")));
        assertEquals(0, new BigDecimal("70.98").compareTo(result.scenarioPrices().get("WMT")));

        // Verify engine state remains completely untouched
        assertEquals(0, new BigDecimal("751.70").compareTo(engine.getCurrentValue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(engine.getCumulativePnL()));
        assertEquals(0, new BigDecimal("224.61").compareTo(engine.getLatestPrice("AAPL")));
        assertEquals(0, new BigDecimal("74.72").compareTo(engine.getLatestPrice("WMT")));
        assertEquals(0L, engine.getLastAcceptedSequence());
        assertTrue(engine.getAcceptedEventIds().isEmpty());
    }

    @Test
    @DisplayName("Evaluates scenario on post-replay portfolio with single shifted symbol and unshifted symbol")
    void evaluatesScenarioOnPostReplayPortfolioWithHandCheckedValues() {
        ReplayEngine engine = new ReplayEngine(createExamplePositions());
        for (PriceEvent event : createExampleEvents()) {
            engine.process(event);
        }

        // After e1-e4: AAPL=227.10 (10 shares), WMT=75.85 (-20 shares), currentValue=754.00
        // Scenario "Tech Surge": AAPL +5% (0.05), WMT unshifted
        // AAPL price: 227.10 * 1.05 = 238.455 -> 238.46
        // WMT price: 75.85 (retained)
        // Marked Values:
        // AAPL: 10 * 238.46 = 2384.60
        // WMT: -20 * 75.85 = -1517.00
        // Scenario Value: 2384.60 - 1517.00 = 867.60
        // Value Change: 867.60 - 754.00 = +113.60
        Scenario scenario = new Scenario("Tech Surge", Map.of(
                "AAPL", new BigDecimal("0.05")
        ));

        ScenarioResult result = engine.evaluateScenario(scenario);

        assertEquals("Tech Surge", result.scenarioName());
        assertEquals(0, new BigDecimal("754.00").compareTo(result.baseValue()));
        assertEquals(0, new BigDecimal("867.60").compareTo(result.scenarioValue()));
        assertEquals(0, new BigDecimal("113.60").compareTo(result.valueChange()));
        assertEquals(0, new BigDecimal("238.46").compareTo(result.scenarioPrices().get("AAPL")));
        assertEquals(0, new BigDecimal("75.85").compareTo(result.scenarioPrices().get("WMT")));

        // Verify engine state remains unchanged after evaluation
        assertEquals(0, new BigDecimal("754.00").compareTo(engine.getCurrentValue()));
        assertEquals(0, new BigDecimal("2.30").compareTo(engine.getCumulativePnL()));
        assertEquals(0, new BigDecimal("227.10").compareTo(engine.getLatestPrice("AAPL")));
        assertEquals(0, new BigDecimal("75.85").compareTo(engine.getLatestPrice("WMT")));
        assertEquals(4L, engine.getLastAcceptedSequence());
        assertEquals(Set.of("e1", "e2", "e3", "e4"), engine.getAcceptedEventIds());
    }

    @Test
    @DisplayName("Evaluates negative shock scenario on post-replay portfolio with hand-checked values")
    void evaluatesNegativeShockScenarioWithHandCheckedValues() {
        ReplayEngine engine = new ReplayEngine(createExamplePositions());
        for (PriceEvent event : createExampleEvents()) {
            engine.process(event);
        }

        // After e1-e4: AAPL=227.10, WMT=75.85, currentValue=754.00
        // Scenario "Market Selloff":
        // AAPL shift: -8% (-0.08) -> 227.10 * 0.92 = 208.932 -> 208.93
        // WMT shift:  -4% (-0.04) -> 75.85 * 0.96 = 72.816 -> 72.82
        // Marked Values:
        // AAPL: 10 * 208.93 = 2089.30
        // WMT: -20 * 72.82 = -1456.40
        // Scenario Value: 2089.30 - 1456.40 = 632.90
        // Value Change: 632.90 - 754.00 = -121.10
        Scenario scenario = new Scenario("Market Selloff", Map.of(
                "AAPL", new BigDecimal("-0.08"),
                "WMT", new BigDecimal("-0.04")
        ));

        ScenarioResult result = engine.evaluateScenario(scenario);

        assertEquals("Market Selloff", result.scenarioName());
        assertEquals(0, new BigDecimal("754.00").compareTo(result.baseValue()));
        assertEquals(0, new BigDecimal("632.90").compareTo(result.scenarioValue()));
        assertEquals(0, new BigDecimal("-121.10").compareTo(result.valueChange()));
        assertEquals(0, new BigDecimal("208.93").compareTo(result.scenarioPrices().get("AAPL")));
        assertEquals(0, new BigDecimal("72.82").compareTo(result.scenarioPrices().get("WMT")));

        // Verify engine state remains unchanged after evaluation
        assertEquals(0, new BigDecimal("754.00").compareTo(engine.getCurrentValue()));
        assertEquals(0, new BigDecimal("2.30").compareTo(engine.getCumulativePnL()));
        assertEquals(0, new BigDecimal("227.10").compareTo(engine.getLatestPrice("AAPL")));
        assertEquals(0, new BigDecimal("75.85").compareTo(engine.getLatestPrice("WMT")));
        assertEquals(4L, engine.getLastAcceptedSequence());
        assertEquals(Set.of("e1", "e2", "e3", "e4"), engine.getAcceptedEventIds());
    }

    @Test
    @DisplayName("Scenario evaluation is read-only and idempotent: running twice yields identical result")
    void scenarioEvaluationIsReadOnlyAndIdempotent() {
        ReplayEngine engine = new ReplayEngine(createExamplePositions());
        engine.process(new PriceEvent("e1", 1, LocalDate.of(2024, 8, 29), "AAPL", new BigDecimal("227.88")));

        Scenario scenario = new Scenario("Stress Test", Map.of(
                "AAPL", new BigDecimal("0.15"),
                "WMT", new BigDecimal("-0.10")
        ));

        BigDecimal valBefore = engine.getCurrentValue();
        BigDecimal pnlBefore = engine.getCumulativePnL();
        long seqBefore = engine.getLastAcceptedSequence();
        BigDecimal aaplPriceBefore = engine.getLatestPrice("AAPL");
        BigDecimal wmtPriceBefore = engine.getLatestPrice("WMT");
        Set<String> acceptedBefore = Set.copyOf(engine.getAcceptedEventIds());

        ScenarioResult r1 = engine.evaluateScenario(scenario);
        ScenarioResult r2 = engine.evaluateScenario(scenario);

        assertEquals(r1, r2);
        assertEquals(valBefore, engine.getCurrentValue());
        assertEquals(pnlBefore, engine.getCumulativePnL());
        assertEquals(seqBefore, engine.getLastAcceptedSequence());
        assertEquals(aaplPriceBefore, engine.getLatestPrice("AAPL"));
        assertEquals(wmtPriceBefore, engine.getLatestPrice("WMT"));
        assertEquals(acceptedBefore, engine.getAcceptedEventIds());

        // Historical replay continues normally after scenario evaluations
        PriceEvent e2 = new PriceEvent("e2", 2, LocalDate.of(2024, 8, 29), "WMT", new BigDecimal("75.05"));
        ReplayResult replayResult = engine.process(e2).orElseThrow();
        assertEquals(2L, replayResult.sequence());
        assertEquals(0, new BigDecimal("777.80").compareTo(replayResult.portfolioValue()));
    }

    @Test
    @DisplayName("Rejects unknown symbol in scenario without modifying engine state")
    void rejectsUnknownSymbolInScenario() {
        ReplayEngine engine = new ReplayEngine(createExamplePositions());
        engine.process(new PriceEvent("e1", 1, LocalDate.of(2024, 8, 29), "AAPL", new BigDecimal("227.88")));

        BigDecimal valBefore = engine.getCurrentValue();
        BigDecimal pnlBefore = engine.getCumulativePnL();
        long seqBefore = engine.getLastAcceptedSequence();
        BigDecimal aaplPriceBefore = engine.getLatestPrice("AAPL");
        BigDecimal wmtPriceBefore = engine.getLatestPrice("WMT");
        Set<String> acceptedBefore = Set.copyOf(engine.getAcceptedEventIds());

        Scenario invalidScenario = new Scenario("Bad Symbol", Map.of(
                "AAPL", new BigDecimal("0.05"),
                "MSFT", new BigDecimal("0.10")
        ));

        assertThrows(IllegalArgumentException.class, () -> engine.evaluateScenario(invalidScenario));

        // All 5 engine state aspects must remain strictly identical
        assertEquals(valBefore, engine.getCurrentValue());
        assertEquals(pnlBefore, engine.getCumulativePnL());
        assertEquals(seqBefore, engine.getLastAcceptedSequence());
        assertEquals(aaplPriceBefore, engine.getLatestPrice("AAPL"));
        assertEquals(wmtPriceBefore, engine.getLatestPrice("WMT"));
        assertEquals(acceptedBefore, engine.getAcceptedEventIds());
    }

    @Test
    @DisplayName("Rejects invalid percentages and leaves engine state unchanged")
    void rejectsInvalidPercentagesInScenario() {
        // Constructor validation
        // Shift <= -1.0 (-100%)
        assertThrows(IllegalArgumentException.class, () ->
                new Scenario("Wipeout", Map.of("AAPL", new BigDecimal("-1.0")))
        );
        assertThrows(IllegalArgumentException.class, () ->
                new Scenario("NegativePrice", Map.of("AAPL", new BigDecimal("-1.50")))
        );

        // Null percentage shift
        assertThrows(NullPointerException.class, () ->
                new Scenario("NullShift", Collections.singletonMap("AAPL", null))
        );

        // Blank name
        assertThrows(IllegalArgumentException.class, () ->
                new Scenario("   ", Map.of("AAPL", new BigDecimal("0.05")))
        );

        // Engine-level evaluation rejection and state integrity
        ReplayEngine engine = new ReplayEngine(createExamplePositions());
        engine.process(new PriceEvent("e1", 1, LocalDate.of(2024, 8, 29), "AAPL", new BigDecimal("227.88")));

        BigDecimal valBefore = engine.getCurrentValue();
        BigDecimal pnlBefore = engine.getCumulativePnL();
        long seqBefore = engine.getLastAcceptedSequence();
        BigDecimal aaplPriceBefore = engine.getLatestPrice("AAPL");
        BigDecimal wmtPriceBefore = engine.getLatestPrice("WMT");
        Set<String> acceptedBefore = Set.copyOf(engine.getAcceptedEventIds());

        assertThrows(IllegalArgumentException.class, () ->
                engine.evaluateScenario("Wipeout", Map.of("AAPL", new BigDecimal("-1.0")))
        );
        assertThrows(IllegalArgumentException.class, () ->
                engine.evaluateScenario("Negative", Map.of("AAPL", new BigDecimal("-1.25")))
        );
        assertThrows(NullPointerException.class, () ->
                engine.evaluateScenario("NullShift", Collections.singletonMap("AAPL", null))
        );

        assertEquals(valBefore, engine.getCurrentValue());
        assertEquals(pnlBefore, engine.getCumulativePnL());
        assertEquals(seqBefore, engine.getLastAcceptedSequence());
        assertEquals(aaplPriceBefore, engine.getLatestPrice("AAPL"));
        assertEquals(wmtPriceBefore, engine.getLatestPrice("WMT"));
        assertEquals(acceptedBefore, engine.getAcceptedEventIds());
    }

    @Test
    @DisplayName("Scenario constructor makes defensive copy of caller map and rejects subsequent mutations")
    void scenarioConstructorMakesDefensiveCopyOfCallerMap() {
        Map<String, BigDecimal> mutableMap = new HashMap<>();
        mutableMap.put("AAPL", new BigDecimal("0.10"));
        Scenario scenario = new Scenario("DefensiveTest", mutableMap);

        // Mutate caller map after construction
        mutableMap.put("AAPL", new BigDecimal("0.99"));
        mutableMap.put("WMT", new BigDecimal("-0.05"));

        // Record must remain unchanged
        assertEquals(1, scenario.percentageChanges().size());
        assertEquals(new BigDecimal("0.10"), scenario.percentageChanges().get("AAPL"));
        assertFalse(scenario.percentageChanges().containsKey("WMT"));

        // Record's getter map cannot be directly mutated
        assertThrows(UnsupportedOperationException.class, () ->
                scenario.percentageChanges().put("GOOG", new BigDecimal("0.05"))
        );
    }

    @Test
    @DisplayName("ScenarioResult constructor makes defensive copy of scenarioPrices map and rejects subsequent mutations")
    void scenarioResultConstructorMakesDefensiveCopyOfCallerMap() {
        Map<String, BigDecimal> mutablePrices = new HashMap<>();
        mutablePrices.put("AAPL", new BigDecimal("230.00"));
        ScenarioResult result = new ScenarioResult(
                "ResultDefensiveTest",
                new BigDecimal("750.00"),
                new BigDecimal("800.00"),
                new BigDecimal("50.00"),
                mutablePrices
        );

        // Mutate caller map after construction
        mutablePrices.put("AAPL", new BigDecimal("999.00"));
        mutablePrices.put("WMT", new BigDecimal("75.00"));

        // Record must remain unchanged
        assertEquals(1, result.scenarioPrices().size());
        assertEquals(new BigDecimal("230.00"), result.scenarioPrices().get("AAPL"));
        assertFalse(result.scenarioPrices().containsKey("WMT"));

        // Record's getter map cannot be directly mutated
        assertThrows(UnsupportedOperationException.class, () ->
                result.scenarioPrices().put("GOOG", new BigDecimal("100.00"))
        );
    }
}
