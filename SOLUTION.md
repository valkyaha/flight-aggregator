# SOLUTION.md — Flight Aggregation & Cache Optimization System

An Online Travel Agency backend that ingests flight itineraries from three mock GDS providers
(each in a different legacy wire format), deduplicates identical itineraries across providers,
keeps the cheapest fare, applies a 5% OTA markup, and serves a filtered, keyset-paginated search
API behind a React dashboard. The whole stack boots with a single command.

---

## 1. Running it

```bash
docker compose up --build
```

This builds and starts five services and waits for each dependency to be healthy before starting
the next:

| Service | Port | Purpose |
|---|---|---|
| `frontend` | http://localhost:3000 | React dashboard (nginx; reverse-proxies `/api` → backend) |
| `backend` | http://localhost:8080 | Aggregation + search API (Spring Boot) |
| `mock-providers` | (internal) | Simulated Alpha/Beta/Gamma GDS APIs with latency + 500s |
| `postgres` | (internal) | Aggregated itinerary store |
| `redis` | (internal) | Shared L2 cache |

On boot the backend ingests a set of routes automatically, so the dashboard has data immediately.
Key endpoints:

- `GET /api/flights?origin=MAD&destination=LHR&maxPrice=200&carrier=IB&size=20&cursor=<token>`
- `POST /api/ingest` with `{"origin":"MAD","destination":"LHR","date":"2026-07-10"}`
- `GET /actuator/health`

Everything is configurable via environment variables (see `.env.example`): DB/Redis credentials,
markup %, provider URLs and fan-out deadline, cache TTL, mock latency/error rate.

**Toolchain:** Java 25 LTS, Spring Boot 4.1 (Spring Framework 7), Gradle (Kotlin DSL, wrapper),
PostgreSQL 17, Redis 7, React 19 + Vite + TypeScript, Docker Compose V2.

---

## 2. Architecture — Clean / Hexagonal (Ports & Adapters)

The core business rules are isolated from all frameworks, network clients and databases.
Dependencies point **inward**; the `domain` package has zero framework imports (verified by test
coverage and by grep in review).

```
adapter (web / provider / persistence / cache / schedule)
   │  implements driven ports, calls driving ports
   ▼
application (orchestration: ingest fan-out, search cache-aside)
   │  depends only on domain ports
   ▼
domain (pure Java: model + services + port interfaces)
```

```
com.ota.flightagg
├── domain/
│   ├── model/        Itinerary, Leg, Money, Airport, Carrier, FlightNumber,
│   │                 ItineraryKey, ItineraryView, Provider, ParseResult (sealed), RejectionReason
│   ├── service/      AggregationService, MarkupPolicy, ConnectionValidator, ConnectionRules
│   └── port/
│       ├── in/       IngestFlightsUseCase, SearchFlightsUseCase, IngestionResult
│       └── out/      FlightProviderPort, FlightRepositoryPort, SearchCachePort,
│                     ProviderQuery, SearchCriteria, SearchPage, Cursor
├── application/      IngestFlightsService, SearchFlightsService
├── adapter/
│   ├── in/web/       FlightSearchController, IngestController, DTOs, ApiExceptionHandler
│   ├── in/schedule/  ScheduledIngestion
│   ├── out/provider/ Alpha/Beta/Gamma adapters + JSON/XML/CSV parsers (ItineraryParser strategy)
│   ├── out/persistence/ ItineraryRepositoryAdapter (JDBC, keyset)
│   └── out/cache/    TwoLevelSearchCache (Caffeine → Redis)
└── config/           Spring wiring, @ConfigurationProperties, resilience/cache/domain beans
```

The domain never sees a provider-specific shape, a JPA entity, or an HTTP concern. Each provider
adapter translates its legacy format into the same domain model; controllers map domain results to
web DTOs; the persistence adapter maps domain ↔ SQL rows.

---

## 3. Data-structure modeling & justification

**Value objects as records.** Every immutable data carrier is a `record` that validates its own
invariants in a compact constructor: `Money` (normalised to scale 2, `HALF_UP`, non-negative),
`Airport` (3-letter IATA), `Carrier` (2-char IATA), `FlightNumber`, `Leg` (origin ≠ destination,
arrival after departure), `Itinerary` (≥ 1 leg, defensively immutable). This makes illegal states
unrepresentable and pushes validation to construction time.

**The deduplication identity is a first-class type.** `ItineraryKey` = `(airline, ordered flight
numbers, connection airports, departure times truncated to the minute)`. Two itineraries from
different providers are "the same flight" iff their keys are equal; price and source are
deliberately excluded — grouping by this key is exactly what lets us pick the cheapest equivalent
offer. Because records give value-based `equals`/`hashCode` over their (list) components, the key
works directly as a `HashMap` grouping key, so **deduplication is O(n)** over an ingestion batch via
`LinkedHashMap.merge(key, itinerary, cheaper)`. Departure times are minute-truncated so legacy
feeds that jitter by seconds still collapse together.

**Sealed result type.** `ParseResult = Valid | DeadLeg | Invalid` is a sealed interface, so the
switch over parse/validation outcomes is exhaustive — adding a variant becomes a compile error until
every handler deals with it. Parsing malformed source and semantic connection failures both flow
through this one type.

**Persistence shape.** Aggregated itineraries land in a single denormalised, query-optimised table
(`itinerary`). `Money` is `numeric(12,2)` + `char(3)` currency (never a float); times are
`timestamptz`; full leg detail is stored as `jsonb` so it is available without a join. A canonical
`dedup_key` column carries the `ItineraryKey` for cross-ingestion upserts.

---

## 4. Multi-provider ingestion & resiliency

**Adapter + Strategy for format heterogeneity.** Three adapters implement one `FlightProviderPort`;
each delegates to a pure `ItineraryParser` (`JsonItineraryParser`, `XmlItineraryParser`,
`CsvItineraryParser`). The parsers are pure `String → List<ParseResult>` with no HTTP, which makes
them the highest-value unit tests in the suite (text-block fixtures + malformed input). A dedicated
test asserts the *same* flight expressed as JSON, XML and CSV produces an **identical
`ItineraryKey`** — the invariant the whole dedup story rests on.

**Simultaneous fan-out with structured concurrency.** `IngestFlightsService` launches all three
provider calls on virtual threads inside a `StructuredTaskScope` (`Joiner.awaitAll()` +
`Configuration.withTimeout(deadline)`). Each subtask publishes its result into a concurrent queue as
it completes, so **whatever finishes before the deadline is used and slow/failed providers are
abandoned** — one provider dropping out yields a *partial* aggregation, never a total failure.

**Per-provider retry + timeouts.** Each adapter's `fetch` is annotated with Spring Framework 7's
native `@Retryable` (retry only transient 5xx / I/O, never 4xx; exponential backoff; a per-attempt
timeout). The `RestClient` additionally has connect/read timeouts so a stalled provider can't pin a
thread indefinitely. Two independent guards (per-attempt timeout, and the fan-out deadline) bound
the worst case well below the providers' 5000 ms latency ceiling.

This is proven end-to-end by `ProviderResiliencyIT`, which drives the **real** adapters against a
local HTTP server injecting faults: one provider 500 → the other two still aggregate; one provider
stalls past the deadline → abandoned; all providers 500 → degraded empty response, no crash; a
transient 500-then-200 → recovered via retry.

---

## 5. Aggregation & business rules (the core domain)

Pure, deterministic, `Spring`-free domain services:

- **`ConnectionValidator`** turns a structurally-valid `Itinerary` into a `ParseResult`, filtering
  dead legs and invalid connections: geographically disconnected legs, a connection that departs
  before the previous leg arrives (dead leg), layover below the minimum or above the maximum
  connection time, and duplicate flight numbers within one itinerary.
- **`AggregationService`** groups valid itineraries by `ItineraryKey`, keeps the cheapest across
  providers, and applies the markup — preserving which provider won. Exact price ties are broken
  deterministically by provider order, so the winning source is stable across runs regardless of
  fan-out completion order.
- **`MarkupPolicy`** applies the configurable OTA markup (default 5%) with `BigDecimal`,
  `RoundingMode.HALF_UP`, scale 2. The rate is injected from configuration; it is never hardcoded in
  the rule.

Verified live: a single ingest reported `received=150 → aggregated=50` — the same 50 flights from
each of the three providers collapsed to 50 cheapest, marked-up fares.

---

## 6. Persistence & pagination — Keyset vs Offset

**Decision: keyset (cursor) pagination.** This is the central data-handling justification the brief
asks for.

- **Offset (`LIMIT n OFFSET m`) degrades to O(m).** PostgreSQL still walks and discards all `m`
  skipped rows before returning a page, so at up to 1,000,000 rows the deep pages get progressively
  slower (fetching page 5,000 means scanning ~5,000,000 cumulative rows across the session). Offset
  is also **unstable under concurrent ingestion**: as rows are inserted, absolute positions shift,
  so a page can skip or duplicate rows.
- **Keyset stays ~flat with depth.** The query
  `WHERE (price, id) > (:lastPrice, :lastId) ORDER BY price, id LIMIT :n` seeks directly to the
  cursor position through the index and reads only `n` rows regardless of depth (~O(log N + n)). It
  is stable under inserts because the cursor is a *data value*, not a positional offset.
- **Requires a total, unique, stable sort order.** We always append the unique `id` tiebreaker to
  the sort key. The cursor is the last row's `(price, id)` tuple, encoded as an opaque URL-safe
  base64 token so clients treat it as a black box.
- **Supporting indexes** match filter + sort so the scan is index-driven:
  `(origin, destination, price, id)`, `(carrier, price, id)`, and `(price, id)` for the unfiltered
  case; plus a unique index on `dedup_key`.

**Evidence.** `HighVolumeKeysetIT` seeds **100,000 rows** and walks the entire keyset pagination:
every row is visited exactly once in strict `(price, id)` order, per-page latency stays bounded
(max page < 1 s), and a **deep page near row 100,000 is no slower than the first page** — the
concrete demonstration that keyset does not degrade with depth. (The suite runs 100k to stay fast in
CI; the index strategy is identical at 1M.)

**Trade-offs, documented:** keyset gives no random "jump to page N" and no total row count by
default — both acceptable for an infinite-scroll flight-search UX. Offset remains available for a
shallow admin view if ever needed.

**Bulk ingest.** Writes use a batched `INSERT … ON CONFLICT (dedup_key) DO UPDATE … WHERE
EXCLUDED.price < itinerary.price`, so re-ingesting the same flight keeps the cheapest across runs and
avoids row-by-row round trips.

---

## 7. Caching — two-level with graceful degradation

Search results are cached cache-aside through `SearchCachePort`:

- **L1 Caffeine** (in-process, sub-millisecond) fronts **L2 Redis** (shared across instances),
  keyed by a normalised query string (`origin|destination|maxPrice|carrier|cursor|limit`), short
  TTL. Entries are JSON-serialised into Redis so the value objects round-trip.
- **Graceful degradation:** every Redis call is wrapped — an outage is a logged miss, never a failed
  request; L1 keeps serving and the request falls through to the database. Proven both ways: a
  Testcontainers Redis test verifies cross-instance sharing and real serialisation, and a
  Mockito "broken Redis" test verifies `get`/`put`/`invalidateAll` never throw.
- **Freshness:** re-ingestion invalidates the search cache (via a non-blocking Redis `SCAN` over the
  key prefix, not `KEYS`); combined with the short TTL this keeps time-sensitive fare data current.
- **Observability:** Caffeine stats are bound to Micrometer/Actuator (`flight_search_cache`
  hit/miss).

---

## 8. API & Frontend

`FlightSearchController` exposes filtering by origin/destination/max-price/carrier plus `size`
(clamped) and the opaque `cursor`; invalid IATA/carrier/cursor values map to `400` via a
`@RestControllerAdvice`, not a `500`. `IngestController` triggers an on-demand ingest and returns a
summary; its request body is bean-validated (`@Valid` + `@NotBlank`/`@NotNull`) so missing/blank
fields also return `400` rather than a `500`. `ScheduledIngestion` refreshes a set of routes on boot
and periodically.

The React + Vite + TypeScript dashboard provides the filter form (with debounced **real-time**
filtering — results refresh as you type, no button press needed), an ingest panel (shows the
provider/received/rejected/aggregated summary), and a results table with keyset-driven "Load more"
pagination. nginx serves the SPA and reverse-proxies `/api` to the backend (no CORS).

---

## 9. Testing strategy

A real pyramid — **75 backend tests (`./gradlew check` green)** plus a frontend Vitest suite and the
strict TypeScript build:

- **Unit (majority, pure, no Spring):** deduplication + cheapest selection (incl. deterministic
  tie-break), markup rounding, connection/dead-leg rules, the three provider parsers with
  malformed-input cases, cross-provider key consistency, cursor encode/decode, and every value-object
  invariant.
- **Slice:** `@WebMvcTest` controllers (param mapping, ISO time serialisation, pagination, and the
  400 paths incl. ingest body validation).
- **Integration (Testcontainers):** PostgreSQL keyset correctness and upsert-keeps-cheapest, Redis
  two-level cache behaviour and degradation, provider resiliency against a fault-injecting HTTP
  server, the 100k-row high-volume keyset test, and a full-stack `FlightApiIT` driving
  controller → service → cache → repository → Postgres.
- **Frontend:** Vitest + React Testing Library — the debounced search-form behaviour, results-table
  rendering, and the API client's query building.
- **Quality gate:** JaCoCo enforces coverage on the `domain` package —
  **98.7% instruction / 100% branch** (gate: ≥90% / ≥80%), wired into `check`.

A live `docker compose up --build` smoke test confirmed one-command boot end-to-end: automatic
ingestion, dedup (150→50), keyset pagination across pages, filters, manual ingest, and the frontend
serving + proxying the API.

---

## 10. Notable technical decisions & trade-offs

- **Structured concurrency is a JDK 25 *preview* API** (JEP 505 — Scoped Values finalised, but
  Structured Concurrency did not). We use it deliberately for the fan-out and enable `--enable-preview`
  consistently across `compileJava`, `test`, `bootRun` and the Docker runtime. Trade-off: a preview
  API in a production image, mitigated by pinning JDK 25. The alternative
  (`Executors.newVirtualThreadPerTaskExecutor()`) is a drop-in fallback if preview is undesirable.
- **Native `@Retryable` over Resilience4j.** Spring Framework 7 ships native resilience annotations
  that give retry-with-backoff and a per-attempt timeout with zero extra dependencies;
  Resilience4j's starter targets Boot 3 and risked the "boots with one command" requirement. The
  cost is **no circuit breaker** — a provider that is down is still retried each ingest rather than
  being short-circuited. The partial-failure guarantee is met by the structured-concurrency
  deadline; a true breaker (Resilience4j as a thin decorator around `FlightProviderPort`) is the
  documented next step.
- **Spring Boot 4 modularisation** surfaced several moved pieces we adapted to: Jackson support and
  the Flyway/`@WebMvcTest` autoconfigurations now live in dedicated modules, `RestClient.Builder` and
  a default `ObjectMapper` are no longer auto-provided, and MVC serialises with Jackson 3.
- **Global cache invalidation** on re-ingest is simple and correct but coarse; per-route
  invalidation is a refinement, currently covered by the short TTL.
- **Dedup scope** is per ingestion query (the three providers for one route/date); cross-query
  global dedup is handled by the `dedup_key` upsert keeping the cheapest across ingests.
- **Out of scope:** authentication/authorisation, multi-currency comparison (a single currency is
  assumed within a dedup group), and ingestion-throughput load testing. High volume is proven for
  the read path at 100k in CI; the schema/index strategy is unchanged at 1M.

---

## 11. AI tools utilized

Built with **Claude Code (Claude Opus 4.8)** driven by a design-first, permission-gated workflow:
no application code was written without explicit per-change approval, every change followed a set of
custom skills, and each increment shipped with tests and a green build before moving on.

- **Custom skills** under `.claude/skills/` encode the standards the agent followed: `java-modern`
  (JDK 25 idioms, virtual threads, structured concurrency, Spring Boot 4), `clean-architecture-ddd`
  (ports & adapters layering, keeping the domain framework-free), `testing-quality` (the test
  pyramid, Testcontainers, resiliency tests, coverage gates), and `docker-postgres-cache`
  (Dockerisation, schema/indexing, keyset pagination, two-level caching, resilience).
- **`CLAUDE.md`** captures the non-negotiable working rules (Gradle only, skills first, prompt log).
- **The complete, verbatim prompt history is in [`docs/PROMPTS.md`](docs/PROMPTS.md)** — every
  instruction given during the build, in order, with a note on what each produced. That log is the
  authoritative record of the "prompts / agent systems engineered to write the code."

Several bleeding-edge findings during the build (structured concurrency still being preview in 25,
the deadline `Subtask.get()` gotcha, Boot 4's module split) were fed back into the skills so they
stay accurate for future work.
