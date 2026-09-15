package dev.esosa.risk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvParserTest {

    private static final Path POSITIONS_FILE = Path.of("examples/positions.csv");
    private static final Path PRICES_FILE = Path.of("examples/prices.csv");

    @Test
    @DisplayName("Parses actual examples/positions.csv fixture file correctly")
    void parsesActualExamplePositionsFile() throws IOException {
        List<Position> positions = CsvParser.parsePositions(POSITIONS_FILE);

        assertEquals(2, positions.size());

        Position aapl = positions.get(0);
        assertEquals("AAPL", aapl.symbol());
        assertEquals(0, new BigDecimal("10").compareTo(aapl.quantity()));
        assertEquals(0, new BigDecimal("224.61").compareTo(aapl.baselinePrice()));

        Position wmt = positions.get(1);
        assertEquals("WMT", wmt.symbol());
        assertEquals(0, new BigDecimal("-20").compareTo(wmt.quantity()));
        assertEquals(0, new BigDecimal("74.72").compareTo(wmt.baselinePrice()));
    }

    @Test
    @DisplayName("Parses actual examples/prices.csv fixture file correctly")
    void parsesActualExamplePricesFile() throws IOException {
        List<PriceEvent> events = CsvParser.parsePriceEvents(PRICES_FILE);

        assertEquals(4, events.size());

        PriceEvent e1 = events.get(0);
        assertEquals("e1", e1.eventId());
        assertEquals(1L, e1.sequence());
        assertEquals("2024-08-29", e1.date().toString());
        assertEquals("AAPL", e1.symbol());
        assertEquals(0, new BigDecimal("227.88").compareTo(e1.price()));

        PriceEvent e4 = events.get(3);
        assertEquals("e4", e4.eventId());
        assertEquals(4L, e4.sequence());
        assertEquals("2024-08-30", e4.date().toString());
        assertEquals("WMT", e4.symbol());
        assertEquals(0, new BigDecimal("75.85").compareTo(e4.price()));
    }

    @Test
    @DisplayName("File-driven replay verifies baseline 751.70, event values 784.40, 777.80, 770.00, 754.00, and cumulative changes 32.70, 26.10, 18.30, 2.30")
    void fileDrivenReplayMatchesContract() throws IOException {
        List<Position> positions = CsvParser.parsePositions(POSITIONS_FILE);
        List<PriceEvent> events = CsvParser.parsePriceEvents(PRICES_FILE);

        ReplayEngine engine = new ReplayEngine(positions);

        // Baseline verification: 751.70
        assertEquals(0, new BigDecimal("751.70").compareTo(engine.getBaselineValue()));
        assertEquals(0, new BigDecimal("751.70").compareTo(engine.getCurrentValue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(engine.getCumulativePnL()));

        // Event e1: marked value 784.40, P&L +32.70
        Optional<ReplayResult> r1 = engine.process(events.get(0));
        assertTrue(r1.isPresent());
        assertEquals(0, new BigDecimal("784.40").compareTo(r1.get().portfolioValue()));
        assertEquals(0, new BigDecimal("32.70").compareTo(r1.get().cumulativePnL()));

        // Event e2: marked value 777.80, P&L +26.10
        Optional<ReplayResult> r2 = engine.process(events.get(1));
        assertTrue(r2.isPresent());
        assertEquals(0, new BigDecimal("777.80").compareTo(r2.get().portfolioValue()));
        assertEquals(0, new BigDecimal("26.10").compareTo(r2.get().cumulativePnL()));

        // Event e3: marked value 770.00, P&L +18.30
        Optional<ReplayResult> r3 = engine.process(events.get(2));
        assertTrue(r3.isPresent());
        assertEquals(0, new BigDecimal("770.00").compareTo(r3.get().portfolioValue()));
        assertEquals(0, new BigDecimal("18.30").compareTo(r3.get().cumulativePnL()));

        // Event e4: marked value 754.00, P&L +2.30
        Optional<ReplayResult> r4 = engine.process(events.get(3));
        assertTrue(r4.isPresent());
        assertEquals(0, new BigDecimal("754.00").compareTo(r4.get().portfolioValue()));
        assertEquals(0, new BigDecimal("2.30").compareTo(r4.get().cumulativePnL()));
        assertEquals(4L, engine.getLastAcceptedSequence());
    }

    @Test
    @DisplayName("Rejects bad header in positions file naming file and row 1")
    void rejectsBadHeaderInPositionsFile() {
        String csv = "ticker,quantity,baseline_price\nAAPL,10,224.61\n";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                CsvParser.parsePositions(new StringReader(csv), "test-positions.csv")
        );
        assertTrue(ex.getMessage().contains("test-positions.csv:1: Invalid header"));
    }

    @Test
    @DisplayName("Rejects bad header in prices file naming file and row 1")
    void rejectsBadHeaderInPricesFile() {
        String csv = "id,seq,date,symbol,price\ne1,1,2024-08-29,AAPL,227.88\n";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                CsvParser.parsePriceEvents(new StringReader(csv), "test-prices.csv")
        );
        assertTrue(ex.getMessage().contains("test-prices.csv:1: Invalid header"));
    }

    @Test
    @DisplayName("Rejects empty file with missing header row")
    void rejectsEmptyFileMissingHeader() {
        IllegalArgumentException exPos = assertThrows(IllegalArgumentException.class, () ->
                CsvParser.parsePositions(new StringReader(""), "empty-positions.csv")
        );
        assertTrue(exPos.getMessage().contains("empty-positions.csv:1: Empty file; missing header"));

        IllegalArgumentException exPrice = assertThrows(IllegalArgumentException.class, () ->
                CsvParser.parsePriceEvents(new StringReader(""), "empty-prices.csv")
        );
        assertTrue(exPrice.getMessage().contains("empty-prices.csv:1: Empty file; missing header"));
    }

    @Test
    @DisplayName("Rejects file with header but zero data rows")
    void rejectsFileWithNoDataRows() {
        IllegalArgumentException exPos = assertThrows(IllegalArgumentException.class, () ->
                CsvParser.parsePositions(new StringReader("symbol,quantity,baseline_price\n"), "no-data-pos.csv")
        );
        assertTrue(exPos.getMessage().contains("no-data-pos.csv: Empty positions file; no data rows found"));

        IllegalArgumentException exPrice = assertThrows(IllegalArgumentException.class, () ->
                CsvParser.parsePriceEvents(new StringReader("eventId,sequence,date,symbol,price\n"), "no-data-prices.csv")
        );
        assertTrue(exPrice.getMessage().contains("no-data-prices.csv: Empty price events file; no data rows found"));
    }

    @Test
    @DisplayName("Rejects malformed row with missing columns naming file and row number")
    void rejectsMalformedRowMissingColumns() {
        String csvPos = "symbol,quantity,baseline_price\nAAPL,10\n";
        IllegalArgumentException exPos = assertThrows(IllegalArgumentException.class, () ->
                CsvParser.parsePositions(new StringReader(csvPos), "test-positions.csv")
        );
        assertTrue(exPos.getMessage().contains("test-positions.csv:2: Expected 3 columns"));

        String csvPrices = "eventId,sequence,date,symbol,price\ne1,1,2024-08-29,AAPL\n";
        IllegalArgumentException exPrice = assertThrows(IllegalArgumentException.class, () ->
                CsvParser.parsePriceEvents(new StringReader(csvPrices), "test-prices.csv")
        );
        assertTrue(exPrice.getMessage().contains("test-prices.csv:2: Expected 5 columns"));
    }

    @Test
    @DisplayName("Rejects malformed row with extra columns naming file and row number")
    void rejectsMalformedRowExtraColumns() {
        String csvPos = "symbol,quantity,baseline_price\nAAPL,10,224.61,EXTRA\n";
        IllegalArgumentException exPos = assertThrows(IllegalArgumentException.class, () ->
                CsvParser.parsePositions(new StringReader(csvPos), "test-positions.csv")
        );
        assertTrue(exPos.getMessage().contains("test-positions.csv:2: Expected 3 columns"));

        String csvPrices = "eventId,sequence,date,symbol,price\ne1,1,2024-08-29,AAPL,227.88,EXTRA\n";
        IllegalArgumentException exPrice = assertThrows(IllegalArgumentException.class, () ->
                CsvParser.parsePriceEvents(new StringReader(csvPrices), "test-prices.csv")
        );
        assertTrue(exPrice.getMessage().contains("test-prices.csv:2: Expected 5 columns"));
    }

    @Test
    @DisplayName("Rejects malformed row with blank fields naming file and row number")
    void rejectsMalformedRowBlankField() {
        String csvPos = "symbol,quantity,baseline_price\nAAPL,10,  \n";
        IllegalArgumentException exPos = assertThrows(IllegalArgumentException.class, () ->
                CsvParser.parsePositions(new StringReader(csvPos), "test-positions.csv")
        );
        assertTrue(exPos.getMessage().contains("test-positions.csv:2: Blank baseline_price at column 3"));

        String csvPrices = "eventId,sequence,date,symbol,price\n ,1,2024-08-29,AAPL,227.88\n";
        IllegalArgumentException exPrice = assertThrows(IllegalArgumentException.class, () ->
                CsvParser.parsePriceEvents(new StringReader(csvPrices), "test-prices.csv")
        );
        assertTrue(exPrice.getMessage().contains("test-prices.csv:2: Blank eventId at column 1"));
    }

    @Test
    @DisplayName("Rejects blank lines within data rows naming file and row number")
    void rejectsBlankLineInMiddle() {
        String csv = "symbol,quantity,baseline_price\nAAPL,10,224.61\n   \nWMT,-20,74.72\n";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                CsvParser.parsePositions(new StringReader(csv), "test-positions.csv")
        );
        assertTrue(ex.getMessage().contains("test-positions.csv:3: Malformed row: empty or blank line"));
    }

    @Test
    @DisplayName("Rejects malformed row with invalid date format naming file and row number")
    void rejectsMalformedRowInvalidDate() {
        String csvSlash = "eventId,sequence,date,symbol,price\ne1,1,2024/08/29,AAPL,227.88\n";
        IllegalArgumentException exSlash = assertThrows(IllegalArgumentException.class, () ->
                CsvParser.parsePriceEvents(new StringReader(csvSlash), "test-prices.csv")
        );
        assertTrue(exSlash.getMessage().contains("test-prices.csv:2: Invalid date format '2024/08/29'"));

        String csvBadDate = "eventId,sequence,date,symbol,price\ne1,1,not-a-date,AAPL,227.88\n";
        IllegalArgumentException exBadDate = assertThrows(IllegalArgumentException.class, () ->
                CsvParser.parsePriceEvents(new StringReader(csvBadDate), "test-prices.csv")
        );
        assertTrue(exBadDate.getMessage().contains("test-prices.csv:2: Invalid date format 'not-a-date'"));
    }

    @Test
    @DisplayName("Rejects malformed row with invalid numeric value naming file and row number")
    void rejectsMalformedRowInvalidNumericValue() {
        String csvPos = "symbol,quantity,baseline_price\nAAPL,ten,224.61\n";
        IllegalArgumentException exPos = assertThrows(IllegalArgumentException.class, () ->
                CsvParser.parsePositions(new StringReader(csvPos), "test-positions.csv")
        );
        assertTrue(exPos.getMessage().contains("test-positions.csv:2: Invalid quantity number: 'ten'"));

        String csvPrice = "eventId,sequence,date,symbol,price\ne1,one,2024-08-29,AAPL,227.88\n";
        IllegalArgumentException exPrice = assertThrows(IllegalArgumentException.class, () ->
                CsvParser.parsePriceEvents(new StringReader(csvPrice), "test-prices.csv")
        );
        assertTrue(exPrice.getMessage().contains("test-prices.csv:2: Invalid sequence number: 'one'"));
    }

    @Test
    @DisplayName("Rejects non-positive numeric prices and sequences naming file and row number")
    void rejectsNonPositiveNumericValues() {
        String csvZeroBase = "symbol,quantity,baseline_price\nAAPL,10,0\n";
        IllegalArgumentException exZeroBase = assertThrows(IllegalArgumentException.class, () ->
                CsvParser.parsePositions(new StringReader(csvZeroBase), "test-positions.csv")
        );
        assertTrue(exZeroBase.getMessage().contains("test-positions.csv:2: Baseline price must be strictly positive: 0"));

        String csvZeroSeq = "eventId,sequence,date,symbol,price\ne1,0,2024-08-29,AAPL,227.88\n";
        IllegalArgumentException exZeroSeq = assertThrows(IllegalArgumentException.class, () ->
                CsvParser.parsePriceEvents(new StringReader(csvZeroSeq), "test-prices.csv")
        );
        assertTrue(exZeroSeq.getMessage().contains("test-prices.csv:2: Sequence must be positive: 0"));

        String csvNegPrice = "eventId,sequence,date,symbol,price\ne1,1,2024-08-29,AAPL,-5.00\n";
        IllegalArgumentException exNegPrice = assertThrows(IllegalArgumentException.class, () ->
                CsvParser.parsePriceEvents(new StringReader(csvNegPrice), "test-prices.csv")
        );
        assertTrue(exNegPrice.getMessage().contains("test-prices.csv:2: Price must be strictly positive: -5.00"));
    }

    @Test
    @DisplayName("Rejects quoting format with plain error naming file and row number")
    void rejectsQuotedFieldsPlainly() {
        String csvPos = "symbol,quantity,baseline_price\n\"AAPL\",10,224.61\n";
        IllegalArgumentException exPos = assertThrows(IllegalArgumentException.class, () ->
                CsvParser.parsePositions(new StringReader(csvPos), "test-positions.csv")
        );
        assertTrue(exPos.getMessage().contains("test-positions.csv:2: Quoted fields and quotation marks (\") are not supported in plain CSV reader"));

        String csvPrices = "eventId,sequence,date,symbol,price\ne1,1,2024-08-29,\"AAPL\",227.88\n";
        IllegalArgumentException exPrice = assertThrows(IllegalArgumentException.class, () ->
                CsvParser.parsePriceEvents(new StringReader(csvPrices), "test-prices.csv")
        );
        assertTrue(exPrice.getMessage().contains("test-prices.csv:2: Quoted fields and quotation marks (\") are not supported in plain CSV reader"));
    }

    @Test
    @DisplayName("Rejects duplicate position symbol naming file and row number")
    void rejectsDuplicatePositionSymbol() {
        String csv = "symbol,quantity,baseline_price\nAAPL,10,224.61\nAAPL,5,220.00\n";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                CsvParser.parsePositions(new StringReader(csv), "test-positions.csv")
        );
        assertTrue(ex.getMessage().contains("test-positions.csv:3: Duplicate position symbol: 'AAPL'"));
    }

    @Test
    @DisplayName("Rejects unreadable or nonexistent file path with clear error")
    void rejectsUnreadableOrNonexistentFile() {
        Path missingFile = Path.of("examples/nonexistent-file.csv");
        IllegalArgumentException exPos = assertThrows(IllegalArgumentException.class, () ->
                CsvParser.parsePositions(missingFile)
        );
        assertTrue(exPos.getMessage().contains("File not found: " + missingFile));

        IllegalArgumentException exPrice = assertThrows(IllegalArgumentException.class, () ->
                CsvParser.parsePriceEvents(missingFile)
        );
        assertTrue(exPrice.getMessage().contains("File not found: " + missingFile));

        Path dirPath = Path.of("examples");
        IllegalArgumentException exDir = assertThrows(IllegalArgumentException.class, () ->
                CsvParser.parsePositions(dirPath)
        );
        assertTrue(exDir.getMessage().contains("Path is a directory, not a regular file: " + dirPath));
    }

    @Test
    @DisplayName("Malformed price-event file fails parsing before replay can produce misleading report")
    void malformedPriceEventFileFailsBeforeReplayProducesReport() throws IOException {
        Path tempBadPrices = Files.createTempFile("bad-prices-", ".csv");
        try {
            Files.writeString(tempBadPrices, "eventId,sequence,date,symbol,price\n"
                    + "e1,1,2024-08-29,AAPL,227.88\n"
                    + "e2,2,not-a-date,WMT,75.05\n");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PrintStream originalOut = System.out;
            try {
                System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                        Main.main(new String[]{POSITIONS_FILE.toString(), tempBadPrices.toString()})
                );
                assertTrue(ex.getMessage().contains("Invalid date format 'not-a-date'"));

                // Verify nothing was printed to stdout: no partial baseline, no accepted events
                assertEquals("", out.toString(StandardCharsets.UTF_8), "Stdout must be completely empty on parsing failure");
            } finally {
                System.setOut(originalOut);
            }
        } finally {
            Files.deleteIfExists(tempBadPrices);
        }
    }

    @Test
    @DisplayName("File-driven path rejects engine rule violation (conflicting duplicate) leaving engine state unchanged")
    void fileDrivenRejectionLeavesEngineStateUnchanged() throws IOException {
        List<Position> positions = CsvParser.parsePositions(POSITIONS_FILE);
        ReplayEngine engine = new ReplayEngine(positions);

        // Parse a prices CSV containing e1 followed by conflicting e1 (different price)
        String conflictingCsv = "eventId,sequence,date,symbol,price\n"
                + "e1,1,2024-08-29,AAPL,227.88\n"
                + "e1,2,2024-08-29,AAPL,230.00\n";
        List<PriceEvent> events = CsvParser.parsePriceEvents(new StringReader(conflictingCsv), "conflicting-prices.csv");

        // Process first event e1
        Optional<ReplayResult> r1 = engine.process(events.get(0));
        assertTrue(r1.isPresent());
        BigDecimal valueAfterE1 = engine.getCurrentValue();
        assertEquals(0, new BigDecimal("784.40").compareTo(valueAfterE1));
        assertEquals(1L, engine.getLastAcceptedSequence());

        // Process second event (conflicting duplicate e1) -> Engine rejects it
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                engine.process(events.get(1))
        );
        assertTrue(ex.getMessage().contains("Event ID already accepted with different contents: e1"));

        // Verify engine state is completely unchanged
        assertEquals(0, valueAfterE1.compareTo(engine.getCurrentValue()));
        assertEquals(1L, engine.getLastAcceptedSequence());
        assertEquals(1, engine.getAcceptedEventIds().size());
    }
}
