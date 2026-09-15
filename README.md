# Risk Replay Engine

A Java 17 foundation for simulating and analyzing risk replay scenarios.

> **Notice**: This repository currently contains only the project foundation. No portfolio calculations, external market APIs, or streaming integrations have been added yet.

## Prerequisites

- **Java Development Kit (JDK)**: Java 17+ (configured with `--release 17`)
- **Apache Maven**: 3.8+
- **Git**: 2.x+

## Verified Commands

### Build & Test
Compile the project and run the automated test suite:
```bash
mvn -B test
```

### Run Application
Execute the entrypoint `dev.esosa.risk.Main` via Maven:
```bash
mvn compile exec:java
```
Expected output:
```text
Risk Replay Engine — project initialized
```

## Next Steps

- **Portfolio Replay**: Implement the core portfolio replay model, position tracking, and historical replay event processing.
