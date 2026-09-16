package dev.esosa.risk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    @Test
    @DisplayName("Main with example files prints baseline and accepted event results")
    void mainWithValidFilesPrintsBaselineAndAcceptedEvents() {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;

        try {
            System.setOut(new PrintStream(outContent, true, StandardCharsets.UTF_8));
            Main.main(new String[]{"examples/positions.csv", "examples/prices.csv"});

            String expected = String.join(System.lineSeparator(),
                    "Baseline: 751.70",
                    "1 e1 2024-08-29 AAPL 784.40 +32.70",
                    "2 e2 2024-08-29 WMT 777.80 +26.10",
                    "3 e3 2024-08-30 AAPL 770.00 +18.30",
                    "4 e4 2024-08-30 WMT 754.00 +2.30"
            ) + System.lineSeparator();

            assertEquals(expected, outContent.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("Main with missing or excess arguments throws IllegalArgumentException with usage message")
    void mainWithInvalidArgCountThrowsIllegalArgumentException() {
        IllegalArgumentException ex0 = assertThrows(IllegalArgumentException.class, () -> Main.main(new String[0]));
        assertTrue(ex0.getMessage().contains("Usage: dev.esosa.risk.Main <positions.csv> <prices.csv> [scenario.csv]"));

        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () -> Main.main(new String[]{"positions.csv"}));
        assertTrue(ex1.getMessage().contains("Usage: dev.esosa.risk.Main <positions.csv> <prices.csv> [scenario.csv]"));

        IllegalArgumentException ex4 = assertThrows(IllegalArgumentException.class, () ->
                Main.main(new String[]{"pos.csv", "prices.csv", "scenario.csv", "extra.csv"}));
        assertTrue(ex4.getMessage().contains("Usage: dev.esosa.risk.Main <positions.csv> <prices.csv> [scenario.csv]"));
    }

    @Test
    @DisplayName("Main with three arguments evaluates scenario after replay and prints scenario section")
    void mainWithThreeArgumentsEvaluatesScenarioAndAppendsOutput() {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;

        try {
            System.setOut(new PrintStream(outContent, true, StandardCharsets.UTF_8));
            Main.main(new String[]{"examples/positions.csv", "examples/prices.csv", "examples/scenario.csv"});

            String expected = String.join(System.lineSeparator(),
                    "Baseline: 751.70",
                    "1 e1 2024-08-29 AAPL 784.40 +32.70",
                    "2 e2 2024-08-29 WMT 777.80 +26.10",
                    "3 e3 2024-08-30 AAPL 770.00 +18.30",
                    "4 e4 2024-08-30 WMT 754.00 +2.30",
                    "Scenario: Tech Surge",
                    "Base Marked Value: 754.00",
                    "Scenario Prices: AAPL=238.46, WMT=75.85",
                    "Scenario Marked Value: 867.60",
                    "Change: +113.60"
            ) + System.lineSeparator();

            assertEquals(expected, outContent.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("Main with invalid scenario file throws exception and produces no partial report in stdout")
    void mainWithInvalidScenarioFileProducesNoPartialReport() throws IOException {
        Path tempScenario = Files.createTempFile("bad-scenario-", ".csv");
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;

        try {
            Files.writeString(tempScenario, "scenario,symbol,percentage_change\n"
                    + "Tech Surge,UNKNOWN,0.05\n");

            System.setOut(new PrintStream(outContent, true, StandardCharsets.UTF_8));
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    Main.main(new String[]{"examples/positions.csv", "examples/prices.csv", tempScenario.toString()})
            );
            assertTrue(ex.getMessage().contains("Unknown portfolio symbol in scenario: 'UNKNOWN'"));
            assertEquals("", outContent.toString(StandardCharsets.UTF_8), "Stdout must remain completely empty when scenario validation fails");
        } finally {
            System.setOut(originalOut);
            Files.deleteIfExists(tempScenario);
        }
    }

    @Test
    @DisplayName("Main with valid CSV whose later event has unknown symbol rejects and leaves stdout empty")
    void mainWithLaterRejectedEventProducesNoPartialReport() throws IOException {
        Path tempPrices = Files.createTempFile("prices-unknown-symbol-", ".csv");
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;

        try {
            Files.writeString(tempPrices, "eventId,sequence,date,symbol,price\n"
                    + "e1,1,2024-08-29,AAPL,227.88\n"
                    + "e2,2,2024-08-29,UNKNOWN,100.00\n");

            System.setOut(new PrintStream(outContent, true, StandardCharsets.UTF_8));
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    Main.main(new String[]{"examples/positions.csv", tempPrices.toString()})
            );
            assertTrue(ex.getMessage().contains("Unknown symbol in price event: UNKNOWN"));
            assertEquals("", outContent.toString(StandardCharsets.UTF_8), "Stdout must remain completely empty when an event is rejected");
        } finally {
            System.setOut(originalOut);
            Files.deleteIfExists(tempPrices);
        }
    }
}
