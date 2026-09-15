package dev.esosa.risk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainTest {

    @Test
    @DisplayName("Main prints Risk Replay Engine — project initialized")
    void mainPrintsExpectedMessage() {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;

        try {
            System.setOut(new PrintStream(outContent, true, StandardCharsets.UTF_8));
            Main.main(new String[0]);
            String expected = "Risk Replay Engine \u2014 project initialized" + System.lineSeparator();
            assertEquals(expected, outContent.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
        }
    }
}
