---
name: testing-quality
description: Testing strategy and quality gates for this project — JUnit 5, Testcontainers, the test pyramid, resiliency/deduplication tests, and coverage expectations. Use when writing or reviewing any test, or setting up the Gradle test tasks.
---

# Testing & Quality

The brief requires a **comprehensive test suite (unit and integration)**. Aim for a real pyramid, not just happy-path.

## The pyramid

1. **Unit tests (most of them) — pure domain, no Spring.**
   - Deduplication: same itinerary from 3 providers → single cheapest chosen.
   - Markup policy: 5% applied correctly, currency/rounding (`BigDecimal`, `RoundingMode.HALF_UP`, scale 2) — never `double`.
   - Dead-leg / invalid-connection filtering.
   - Keyset cursor encode/decode round-trip.
   - Provider parsers (JSON / XML / CSV) via `@ParameterizedTest` with text-block fixtures, incl. malformed input.
   - Fast, no `@SpringBootTest`, no containers.

2. **Slice tests.** `@WebMvcTest` for controllers (validation, pagination params, error mapping), `@DataJpaTest` for repository queries — but prefer Testcontainers Postgres over H2 (H2 behaves differently for keyset SQL and JSON types).

3. **Integration tests (Testcontainers).**
   - Real **PostgreSQL** container for persistence + keyset pagination correctness at depth.
   - Real **Redis** container for cache behavior and graceful degradation when cache is down.
   - `WireMock` (or a mock provider container) to simulate the 3 GDS providers **with artificial latency and 500s** — assert the aggregation returns partial results and does NOT fail when one provider times out or errors. This is the resiliency requirement and a likely live-session talking point.

## Testcontainers conventions (JUnit 5)
- `@Testcontainers` on the class, `static` `@Container` fields (started once per class), `@ServiceConnection` (Spring Boot) to auto-wire datasource/redis — no manual `@DynamicPropertySource` needed.
- A shared abstract `IntegrationTest` base class with reusable singleton containers keeps the suite fast.
- Clean state with `@BeforeEach` truncation or `@Transactional` rollback; never rely on test ordering.

## Resiliency test cases (do not skip — the brief calls these out)
- One provider returns 500 → others still aggregate.
- One provider stalls at 5000ms past the deadline → structured-concurrency scope abandons it, result returns on time.
- All providers down → sensible empty/degraded response, not a 500.
- Circuit breaker opens after threshold and recovers (half-open).

## Quality gates
- **JaCoCo** coverage report; set a meaningful threshold on the **domain** package (aim high there, e.g. 90%+) rather than a blanket number.
- Deterministic tests: no `Thread.sleep` for timing — use Awaitility or controllable clocks; inject a `Clock` into time-dependent domain logic.
- Run `./gradlew test` (unit) and a separate `integrationTest` source set / task so fast tests can run without Docker.
- Assertions: AssertJ fluent assertions; Mockito for out-port fakes in application-layer tests (prefer hand-written fakes for domain ports where simple).
