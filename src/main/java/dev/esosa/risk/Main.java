package dev.esosa.risk;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException("Usage: dev.esosa.risk.Main <positions.csv> <prices.csv> [scenario.csv]");
        }

        Path positionsPath = Path.of(args[0]);
        Path pricesPath = Path.of(args[1]);
        Path scenarioPath = (args.length == 3) ? Path.of(args[2]) : null;

        try {
            List<Position> positions = CsvParser.parsePositions(positionsPath);
            List<PriceEvent> events = CsvParser.parsePriceEvents(pricesPath);

            Scenario scenario = null;
            if (scenarioPath != null) {
                Set<String> portfolioSymbols = positions.stream()
                        .map(Position::symbol)
                        .collect(Collectors.toSet());
                scenario = CsvParser.parseScenario(scenarioPath, portfolioSymbols);
            }

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

            if (scenario != null) {
                ScenarioResult sr = engine.evaluateScenario(scenario);
                reportLines.add("Scenario: " + sr.scenarioName());
                reportLines.add("Base Marked Value: " + sr.baseValue());

                List<String> priceParts = new ArrayList<>();
                for (Map.Entry<String, BigDecimal> entry : sr.scenarioPrices().entrySet()) {
                    priceParts.add(entry.getKey() + "=" + entry.getValue());
                }
                reportLines.add("Scenario Prices: " + String.join(", ", priceParts));
                reportLines.add("Scenario Marked Value: " + sr.scenarioValue());

                String changeStr = (sr.valueChange().signum() > 0 ? "+" : "") + sr.valueChange();
                reportLines.add("Change: " + changeStr);
            }

            for (String line : reportLines) {
                System.out.println(line);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read file: " + e.getMessage(), e);
        }
    }
}
