package dev.esosa.risk;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Lightweight, unquoted comma-delimited CSV parser for ValuTrail portfolio and price event files.
 *
 * <p>Supported Format:
 * <ul>
 *   <li>Encoding: UTF-8.</li>
 *   <li>Line delimiter: Standard newline (\n or \r\n). Each line represents exactly one record.</li>
 *   <li>Field delimiter: Comma (,).</li>
 *   <li>Headers: Exact match required (case-sensitive, no extra columns).</li>
 *   <li>Quoting: Not supported. Any line containing quotation marks (") is explicitly rejected
 *       with an error naming the file and row number, preventing silent misparsing.</li>
 *   <li>Multiline records or escaped commas: Not supported.</li>
 * </ul>
 */
public final class CsvParser {

    public static final String POSITIONS_HEADER = "symbol,quantity,baseline_price";
    public static final String PRICES_HEADER = "eventId,sequence,date,symbol,price";

    private CsvParser() {
        // utility class
    }

    /**
     * Parses initial positions from a CSV file.
     *
     * @param path path to the positions CSV file
     * @return ordered list of validated Position records
     * @throws IOException if reading fails
     * @throws IllegalArgumentException if file is unreadable, or headers/data rows are invalid
     */
    public static List<Position> parsePositions(Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File not found: " + path);
        }
        if (Files.isDirectory(path)) {
            throw new IllegalArgumentException("Path is a directory, not a regular file: " + path);
        }
        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException("File is not readable: " + path);
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parsePositions(reader, path.toString());
        }
    }

    /**
     * Parses initial positions from a Reader with a named source for error reporting.
     *
     * @param reader reader providing CSV text
     * @param sourceName name of file or source used in error messages
     * @return ordered list of validated Position records
     * @throws IOException if reading fails
     * @throws IllegalArgumentException if headers, row structure, or data types are invalid
     */
    public static List<Position> parsePositions(Reader reader, String sourceName) throws IOException {
        Objects.requireNonNull(reader, "reader must not be null");
        String src = (sourceName != null && !sourceName.isBlank()) ? sourceName : "<unknown>";
        BufferedReader br = (reader instanceof BufferedReader) ? (BufferedReader) reader : new BufferedReader(reader);

        String header = br.readLine();
        if (header == null) {
            throw new IllegalArgumentException(src + ":1: Empty file; missing header '" + POSITIONS_HEADER + "'");
        }
        if (!header.equals(POSITIONS_HEADER)) {
            throw new IllegalArgumentException(src + ":1: Invalid header: expected '" + POSITIONS_HEADER + "' but found '" + header + "'");
        }

        List<Position> positions = new ArrayList<>();
        Set<String> seenSymbols = new HashSet<>();
        String line;
        int rowNum = 1;

        while ((line = br.readLine()) != null) {
            rowNum++;
            if (line.isBlank()) {
                throw new IllegalArgumentException(src + ":" + rowNum + ": Malformed row: empty or blank line");
            }
            if (line.contains("\"")) {
                throw new IllegalArgumentException(src + ":" + rowNum + ": Quoted fields and quotation marks (\") are not supported in plain CSV reader");
            }

            String[] tokens = line.split(",", -1);
            if (tokens.length != 3) {
                throw new IllegalArgumentException(src + ":" + rowNum + ": Expected 3 columns (" + POSITIONS_HEADER + ") but found " + tokens.length);
            }

            String symbol = tokens[0].trim();
            if (symbol.isEmpty()) {
                throw new IllegalArgumentException(src + ":" + rowNum + ": Blank symbol at column 1");
            }
            if (!seenSymbols.add(symbol)) {
                throw new IllegalArgumentException(src + ":" + rowNum + ": Duplicate position symbol: '" + symbol + "'");
            }

            String qtyStr = tokens[1].trim();
            if (qtyStr.isEmpty()) {
                throw new IllegalArgumentException(src + ":" + rowNum + ": Blank quantity at column 2");
            }
            BigDecimal quantity;
            try {
                quantity = new BigDecimal(qtyStr);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(src + ":" + rowNum + ": Invalid quantity number: '" + qtyStr + "'");
            }

            String priceStr = tokens[2].trim();
            if (priceStr.isEmpty()) {
                throw new IllegalArgumentException(src + ":" + rowNum + ": Blank baseline_price at column 3");
            }
            BigDecimal baselinePrice;
            try {
                baselinePrice = new BigDecimal(priceStr);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(src + ":" + rowNum + ": Invalid baseline price number: '" + priceStr + "'");
            }
            if (baselinePrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(src + ":" + rowNum + ": Baseline price must be strictly positive: " + baselinePrice);
            }

            positions.add(new Position(symbol, quantity, baselinePrice));
        }

        if (positions.isEmpty()) {
            throw new IllegalArgumentException(src + ": Empty positions file; no data rows found");
        }

        return List.copyOf(positions);
    }

    /**
     * Parses price events from a CSV file.
     *
     * @param path path to the prices CSV file
     * @return ordered list of validated PriceEvent records
     * @throws IOException if reading fails
     * @throws IllegalArgumentException if file is unreadable, or headers/data rows are invalid
     */
    public static List<PriceEvent> parsePriceEvents(Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File not found: " + path);
        }
        if (Files.isDirectory(path)) {
            throw new IllegalArgumentException("Path is a directory, not a regular file: " + path);
        }
        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException("File is not readable: " + path);
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parsePriceEvents(reader, path.toString());
        }
    }

    /**
     * Parses price events from a Reader with a named source for error reporting.
     *
     * @param reader reader providing CSV text
     * @param sourceName name of file or source used in error messages
     * @return ordered list of validated PriceEvent records
     * @throws IOException if reading fails
     * @throws IllegalArgumentException if headers, row structure, or data types are invalid
     */
    public static List<PriceEvent> parsePriceEvents(Reader reader, String sourceName) throws IOException {
        Objects.requireNonNull(reader, "reader must not be null");
        String src = (sourceName != null && !sourceName.isBlank()) ? sourceName : "<unknown>";
        BufferedReader br = (reader instanceof BufferedReader) ? (BufferedReader) reader : new BufferedReader(reader);

        String header = br.readLine();
        if (header == null) {
            throw new IllegalArgumentException(src + ":1: Empty file; missing header '" + PRICES_HEADER + "'");
        }
        if (!header.equals(PRICES_HEADER)) {
            throw new IllegalArgumentException(src + ":1: Invalid header: expected '" + PRICES_HEADER + "' but found '" + header + "'");
        }

        List<PriceEvent> events = new ArrayList<>();
        String line;
        int rowNum = 1;

        while ((line = br.readLine()) != null) {
            rowNum++;
            if (line.isBlank()) {
                throw new IllegalArgumentException(src + ":" + rowNum + ": Malformed row: empty or blank line");
            }
            if (line.contains("\"")) {
                throw new IllegalArgumentException(src + ":" + rowNum + ": Quoted fields and quotation marks (\") are not supported in plain CSV reader");
            }

            String[] tokens = line.split(",", -1);
            if (tokens.length != 5) {
                throw new IllegalArgumentException(src + ":" + rowNum + ": Expected 5 columns (" + PRICES_HEADER + ") but found " + tokens.length);
            }

            String eventId = tokens[0].trim();
            if (eventId.isEmpty()) {
                throw new IllegalArgumentException(src + ":" + rowNum + ": Blank eventId at column 1");
            }

            String seqStr = tokens[1].trim();
            if (seqStr.isEmpty()) {
                throw new IllegalArgumentException(src + ":" + rowNum + ": Blank sequence at column 2");
            }
            long sequence;
            try {
                sequence = Long.parseLong(seqStr);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(src + ":" + rowNum + ": Invalid sequence number: '" + seqStr + "'");
            }
            if (sequence <= 0) {
                throw new IllegalArgumentException(src + ":" + rowNum + ": Sequence must be positive: " + sequence);
            }

            String dateStr = tokens[2].trim();
            if (dateStr.isEmpty()) {
                throw new IllegalArgumentException(src + ":" + rowNum + ": Blank date at column 3");
            }
            LocalDate date;
            try {
                date = LocalDate.parse(dateStr);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(src + ":" + rowNum + ": Invalid date format '" + dateStr + "', expected YYYY-MM-DD");
            }

            String symbol = tokens[3].trim();
            if (symbol.isEmpty()) {
                throw new IllegalArgumentException(src + ":" + rowNum + ": Blank symbol at column 4");
            }

            String priceStr = tokens[4].trim();
            if (priceStr.isEmpty()) {
                throw new IllegalArgumentException(src + ":" + rowNum + ": Blank price at column 5");
            }
            BigDecimal price;
            try {
                price = new BigDecimal(priceStr);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(src + ":" + rowNum + ": Invalid price number: '" + priceStr + "'");
            }
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(src + ":" + rowNum + ": Price must be strictly positive: " + price);
            }

            events.add(new PriceEvent(eventId, sequence, date, symbol, price));
        }

        if (events.isEmpty()) {
            throw new IllegalArgumentException(src + ": Empty price events file; no data rows found");
        }

        return List.copyOf(events);
    }
}
