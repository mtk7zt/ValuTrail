package dev.esosa.risk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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
}
