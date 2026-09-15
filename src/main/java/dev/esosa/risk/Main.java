package dev.esosa.risk;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {

    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: dev.esosa.risk.Main <positions.csv> <prices.csv>");
        }

        Path positionsPath = Path.of(args[0]);
        Path pricesPath = Path.of(args[1]);

        try {
            List<Position> positions = CsvParser.parsePositions(positionsPath);
            List<PriceEvent> events = CsvParser.parsePriceEvents(pricesPath);

            ReplayEngine engine = new ReplayEngine(positions);
            List<String> reportLines = new ArrayList<>();
            reportLines.add("Baseline: " + engine.getBaselineValue());

            for (PriceEvent event : events) {
                Optional<ReplayResult> resultOpt = engine.process(event);
                if (resultOpt.isPresent()) {
                    ReplayResult r = resultOpt.get();
                    String pnlStr = (r.cumulativePnL().signum() > 0 ? "+" : "") + r.cumulativePnL();
                    reportLines.add(r.sequence() + " " + r.eventId() + " " + r.date() + " " + r.symbol() + " " + r.portfolioValue() + " " + pnlStr);
                }
            }

            for (String line : reportLines) {
                System.out.println(line);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read file: " + e.getMessage(), e);
        }
    }
}
