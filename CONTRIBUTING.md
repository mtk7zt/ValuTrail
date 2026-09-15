# Contributing to ValuTrail

## Feature Definition of Done (DoD)

Before any feature or code change is considered complete and ready to merge/review, it must satisfy the following criteria:

1. **Scoped Implementation**:
   - The change directly implements the accepted requirements without introducing speculative features, dead code, or unrequested abstractions.
   - Package boundaries (`dev.esosa.risk.*`) and code conventions are strictly followed.

2. **Automated Testing**:
   - Unit tests are written using JUnit Jupiter (`org.junit.jupiter`).
   - Tests cover normal flows, boundary conditions, and failure/edge cases.
   - All tests run cleanly with zero failures, errors, or flaky behavior.
   - Tests verify replay engine behavior and arithmetic against fixture inputs; they do not validate external market data.

3. **Build & Quality Gates**:
   - The project compiles and passes verification via `mvn -B test`.
   - Maven compiler targets Java 17 compatibility.
   - No compiler warnings or unmanaged dependency conflicts.

4. **Documentation & Traceability**:
   - Significant architectural decisions are recorded in `docs/DECISIONS.md`.
   - Test execution results and verification notes are logged in `docs/TEST-EVIDENCE.md`.
   - External data sources, provider conventions, and fixture statuses are documented in `docs/DATA-PROVENANCE.md`.
   - Any user-facing operational steps or CLI commands are reflected in `README.md`.

5. **Clean Working Tree**:
   - Generated files and build outputs (`target/`) are ignored by `.gitignore`.
   - No credentials, API tokens, local configurations, or unintended files are tracked.
   - Git commits are purposeful, granular, and accompanied by clear messages.

---

## Data Provenance Review

Every dataset, historical quote, or externally referenced figure added to ValuTrail must undergo a provenance review. We maintain a strict distinction between three types of data:

1. **Verified Source Observations**:
   To be documented as verified, an observation must include:
   - **Direct Reference**: A direct URL to the source table/page or a specific dataset release identifier.
   - **Publisher**: The official provider or aggregator name (e.g., StatMuse Money, exchange SIP feed).
   - **Symbol & Date**: The instrument ticker and calendar observation date (including timestamp and time zone if intraday).
   - **Exact Source Field & Meaning**: The precise field name and provider convention (e.g., daily `CLOSE` vs split/dividend-adjusted close vs intraday sample).
   - **Access Date**: The calendar date when the source was retrieved and checked.
   - **Comparison Result**: An explicit comparison between the source value and our stored fixture value.
   - **Dataset Metadata**: For downloaded datasets, record the version/retrieval date, applied transformations, and permitted reuse terms if available.
   
   *Policy*: If any reference, field meaning, or comparison is missing, the value must be labeled **unverified**. Never present unverified numbers as confirmed historical prices or silently substitute another value.

2. **Illustrative Inputs**:
   - Synthetic holdings, dummy positions, or unverified prices must be explicitly labeled as illustrative.
   - Using real ticker symbols or realistic amounts does not make an input verified market data.
   - Illustrative fixtures are welcome in examples and unit tests to ensure deterministic arithmetic, provided their status is transparent.

3. **Calculated Outputs**:
   - Every calculated valuation or P&L figure must point directly to its input values, the exact mathematical formula, and a corresponding test or hand calculation.

4. **Handling Discrepancies**:
   - When an external source disagrees with a fixture value, record both numbers and the exact difference.
   - Explain the cause only when an authoritative reference supports the explanation; never guess or speculate.
