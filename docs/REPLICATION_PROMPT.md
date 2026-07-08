# Master Prompt — Replicate the Flight Aggregation & Cache Optimization System

Paste this into a capable coding agent to regenerate the project as a working skeleton. It fixes the
tech stack, architecture, data model, design patterns, decisions, and hardening. Build incrementally
in the given order; keep every step compiling and tested before moving on.

---

## 0. Role & operating rules

You are a senior/lead JVM engineer. Build a **Flight Aggregation & Cache Optimization System** for an
Online Travel Agency: ingest flight itineraries from three mock GDS providers in different legacy
formats, deduplicate identical itineraries across providers, keep the cheapest fare, apply a 5% OTA
markup, filter dead legs / invalid connections, and serve a filtered, keyset-paginated search API
plus a React dashboard. The whole stack must boot with `docker compose up --build`.

Rules:
- **Gradle only** (Kotlin DSL, via the wrapper). Never Maven.
- **Clean/Hexagonal architecture**: the `domain` package has **zero** framework imports (no Spring,
  JPA, Jackson). Dependencies point inward.
- **Modern Java idioms**: records for all immutable data, sealed interfaces + pattern-matching
  switches for closed hierarchies, virtual threads + structured concurrency for I/O fan-out,
  `RestClient` (not `RestTemplate`), `@ConfigurationProperties` records.
- **No comments in code** (self-documenting via naming). Business rules (markup %, connection limits)
  come from configuration, never hardcoded in the rule.
- Every step ships with tests; keep the build green.

---

## 1. Tech stack (pinned)

- **Java 25 LTS** (structured concurrency is preview → `--enable-preview` on `compileJava`, `test`,
  `bootRun`, and the Docker runtime `ENTRYPOINT`).
- **Spring Boot 4.1.x** / **Spring Framework 7** (Jackson 3 for MVC; native `@Retryable`).
- **PostgreSQL 17**, **Redis 7**, **Flyway** (via `spring-boot-starter-flyway` +
  `flyway-database-postgresql`), **Caffeine** (L1 cache).
- **React 19 + Vite + TypeScript** frontend, served by **nginx** (reverse-proxies `/api`).
- **JUnit 5, Testcontainers, Mockito, AssertJ, JaCoCo**; **Vitest + React Testing Library** frontend.
- Jackson XML (`jackson-dataformat-xml`) for the Beta parser; `jackson-datatype-jsr310` for cache
  JSON serialisation.

Boot 4 module notes to expect: `@WebMvcTest`/`@AutoConfigureMockMvc` live under
`org.springframework.boot.webmvc.test.autoconfigure`; there is no auto `ObjectMapper` or
`RestClient.Builder` bean by default; Flyway/Jackson autoconfig are in their own starter modules.

---

## 2. Architecture & package layout

```
com.ota.flightagg
├── domain/                         PURE JAVA — no framework imports
│   ├── model/                      value objects + sealed result type
│   ├── service/                    pure business rules
│   └── port/{in,out}/              use-case + driven-port interfaces (+ their value types)
├── application/                    orchestration (Spring OK): ingest fan-out, search cache-aside
├── adapter/
│   ├── in/web/                     REST controllers, DTOs, exception handler
│   ├── in/schedule/                scheduled/startup ingestion
│   ├── out/provider/              Alpha/Beta/Gamma adapters + parser strategies
│   ├── out/persistence/           JDBC repository (keyset)
│   └── out/cache/                  Caffeine → Redis two-level cache
└── config/                         Spring wiring, @ConfigurationProperties, bean factories
```

Dependency rule: `adapter → application → domain`. Domain owns the port interfaces; adapters
implement the out-ports and call the in-ports.

---

## 3. Domain model (records + invariants)

All records validate in their compact constructor and throw `IllegalArgumentException`/NPE on
violation.

**Value objects (`domain/model`):**
- `Airport(String iata)` — trimmed, upper-cased; must match `^[A-Z]{3}$`.
- `Carrier(String iata)` — trimmed, upper-cased; `^[A-Z0-9]{2}$`.
- `FlightNumber(Carrier carrier, String number)` — `number` `^\d{1,4}$`; `designator()` → e.g. `IB3170`.
- `Money(BigDecimal amount, Currency currency)` — amount normalised to scale 2, `HALF_UP`, never
  negative; static `of(String amount, String currencyCode)`.
- `Leg(FlightNumber flight, Airport from, Airport to, Instant departure, Instant arrival)` — `from`≠`to`,
  `arrival` strictly after `departure`; `duration()`.
- `Itinerary(List<Leg> legs, Money price, Provider source)` — ≥ 1 leg, defensively immutable list;
  `origin()` (first leg from), `destination()` (last leg to), `stops()` (legs−1), `key()`.
- `ItineraryKey(Carrier airline, List<FlightNumber> flightNumbers, List<Airport> connections,
  List<Instant> departures)` — the **deduplication identity**; size invariants
  (`departures.size == flightNumbers.size`, `connections.size == flightNumbers.size − 1`); built by
  `ItineraryKey.of(Itinerary)` which derives airline from the first leg and **truncates departures to
  the minute** (so second-level jitter still dedups). Record equality over list components → usable
  directly as a `HashMap` grouping key.
- `ItineraryView(Airport origin, Airport destination, Carrier carrier, Money price, Instant
  departureTime, Instant arrivalTime, int stops, Provider source)` — read projection for search.
- `Provider` enum: `ALPHA, BETA, GAMMA`.
- `ParseResult` **sealed interface** = `Valid(Itinerary) | DeadLeg(RejectionReason, String detail) |
  Invalid(RejectionReason, String detail)` — exhaustive switch, no default branch.
- `RejectionReason` enum: `MALFORMED_SOURCE, DISCONNECTED_LEGS, LAYOVER_TOO_SHORT, LAYOVER_TOO_LONG,
  DUPLICATE_FLIGHT_NUMBER, DEAD_LEG, UNKNOWN`.

**Domain services (`domain/service`, pure, deterministic):**
- `MarkupPolicy(BigDecimal percentage)` — `applyTo(Money)` = `amount × (1 + p/100)`, re-normalised to
  scale 2; rejects negative; `ofPercentage(...)`.
- `ConnectionRules(Duration minConnection, Duration maxConnection)` — min ≥ 0, max ≥ min.
- `ConnectionValidator(ConnectionRules)` — `validate(Itinerary) → ParseResult`: duplicate flight
  number → `Invalid(DUPLICATE_FLIGHT_NUMBER)`; leg[n].to ≠ leg[n+1].from → `Invalid(DISCONNECTED_LEGS)`;
  next departs before current arrives → `DeadLeg(DEAD_LEG)`; layover < min → `Invalid(LAYOVER_TOO_SHORT)`;
  layover > max → `Invalid(LAYOVER_TOO_LONG)`; else `Valid`.
- `AggregationService(MarkupPolicy)` — `aggregate(Collection<Itinerary>) → List<Itinerary>`: group by
  `ItineraryKey` via `LinkedHashMap.merge`, keep the cheapest (tie-break **deterministically by
  `Provider.ordinal()`**), then apply markup, preserving the winning provider.

---

## 4. Ports

**In (`domain/port/in`):**
- `IngestFlightsUseCase` → `IngestionResult ingest(ProviderQuery)`.
- `SearchFlightsUseCase` → `SearchPage search(SearchCriteria)`.
- `IngestionResult(int providersQueried, int providersSucceeded, int received, int rejected, int aggregated)`.

**Out (`domain/port/out`):**
- `FlightProviderPort` → `Provider provider()`, `List<ParseResult> fetch(ProviderQuery)` (throws on
  total transport failure so the fan-out can treat it as a dropped provider).
- `FlightRepositoryPort` → `void saveAll(Collection<Itinerary>)`, `SearchPage search(SearchCriteria)`.
- `SearchCachePort` → `Optional<SearchPage> get(SearchCriteria)`, `void put(...)`, `void invalidateAll()`.
- `ProviderQuery(Airport origin, Airport destination, LocalDate departureDate)` — origin ≠ destination.
- `SearchCriteria(Airport origin, Airport destination, BigDecimal maxPrice, Carrier carrier, Cursor
  cursor, int limit)` — nullable filters; `limit > 0`.
- `SearchPage(List<ItineraryView> items, String nextCursor)`.
- `Cursor(BigDecimal price, long id)` — `encode()`/`decode()` an opaque URL-safe base64 of `"price:id"`;
  `decode` rejects malformed tokens.

---

## 5. Application layer

- `IngestFlightsService implements IngestFlightsUseCase` (plain class, wired via a `@Bean`): fan out to
  all `FlightProviderPort`s inside a `StructuredTaskScope` (`Joiner.awaitAll()` +
  `Configuration.withTimeout(deadline)`) on virtual threads; each subtask publishes its result into a
  `ConcurrentLinkedQueue` as it completes (so partial results survive a `TimeoutException` — note the
  gotcha that `Subtask.get()` is unavailable after a scope timeout). Then: keep parser-`Valid` →
  `ConnectionValidator` → keep `Valid` → `AggregationService` → `repository.saveAll` →
  `cache.invalidateAll()`. Log a `warn` naming providers that dropped out. Return counts.
- `SearchFlightsService implements SearchFlightsUseCase` (`@Service`): cache-aside —
  `cache.get(criteria).orElseGet(() -> { page = repository.search(criteria); cache.put(...); page })`.

---

## 6. Adapters

**Providers (`adapter/out/provider`)** — Adapter + Strategy:
- `ItineraryParser` interface (`Provider provider()`, `List<ParseResult> parse(String rawPayload)`),
  implemented by pure `JsonItineraryParser` (Alpha), `XmlItineraryParser` (Beta, Jackson-XML with
  nested wrapper records + `useWrapping=false`), `CsvItineraryParser` (Gamma). Each catches its own
  parse failures and emits `Invalid(MALFORMED_SOURCE)` per bad record rather than throwing.
- `AlphaProviderAdapter/BetaProviderAdapter/GammaProviderAdapter implements FlightProviderPort`: build
  a `RestClient` from a prototype builder (with connect/read timeouts), GET their endpoint with
  `origin/destination/date` query params, and delegate the body to their parser. Annotate `fetch` with
  Spring 7 native `@Retryable(includes = {HttpServerErrorException, ResourceAccessException},
  maxRetries=3, delay=200, multiplier=2, maxDelay=2000, timeout=4000, MILLISECONDS)` — retry only
  transient 5xx/I/O, never 4xx.

**Persistence (`adapter/out/persistence`)** — `ItineraryRepositoryAdapter implements
FlightRepositoryPort` using `NamedParameterJdbcTemplate`:
- Batch upsert `INSERT … ON CONFLICT (dedup_key) DO UPDATE … WHERE EXCLUDED.price < itinerary.price`.
- Keyset search: `WHERE (CAST(:origin AS text) IS NULL OR origin = :origin) AND … AND (CAST(:cursorPrice
  AS numeric) IS NULL OR (price, id) > (CAST(:cursorPrice AS numeric), CAST(:cursorId AS bigint)))
  ORDER BY price, id LIMIT :limit`; `nextCursor` when `rows == limit`.

**Cache (`adapter/out/cache`)** — `TwoLevelSearchCache implements SearchCachePort`: Caffeine L1 (bean,
`recordStats()` + Micrometer) → Redis L2 (`StringRedisTemplate`, JSON via a JSR-310 Jackson mapper),
key = `flightsearch::origin|destination|maxPrice|carrier|cursor|limit`. Every Redis call wrapped in
try/catch (outage = logged miss, never a thrown request); `invalidateAll` deletes by prefix via
**`SCAN`** (not `KEYS`).

**Web (`adapter/in/web`)**:
- `FlightSearchController` `GET /api/flights` (nullable origin/destination/maxPrice/carrier, opaque
  `cursor`, `size` clamped to [1,100]) → `FlightSearchResponse`.
- `IngestController` `POST /api/ingest` (`@Valid @RequestBody IngestionRequest{@NotBlank origin,
  @NotBlank destination, @NotNull date}`) → 202 + `IngestionResponse`.
- `ApiExceptionHandler` (`@RestControllerAdvice`): `IllegalArgumentException`,
  `MethodArgumentNotValidException`, `MethodArgumentTypeMismatchException`,
  `HttpMessageNotReadableException` → clean **400**; catch-all `RuntimeException` → clean **500**
  (logged, no leak). Scope the catch-all to `RuntimeException` (not `Exception`) so framework HTTP
  exceptions keep their status (405/415). DTOs are plain records; never return domain/JPA objects.

**Schedule (`adapter/in/schedule`)** — `ScheduledIngestion` (`@ConditionalOnProperty
ingestion.startup.enabled`, `@Scheduled` initial + fixed delay) ingests a set of routes on boot and
periodically; catches per-route failures so one bad route never stops the rest.

---

## 7. Persistence schema (Flyway `V1__create_itinerary.sql`)

```
itinerary(
  id BIGINT GENERATED BY DEFAULT AS IDENTITY PK,
  dedup_key TEXT NOT NULL, origin VARCHAR(3), destination VARCHAR(3), carrier VARCHAR(2),
  price NUMERIC(12,2), currency VARCHAR(3), departure_time TIMESTAMPTZ, arrival_time TIMESTAMPTZ,
  stops INT, source VARCHAR(16), legs JSONB, ingested_at TIMESTAMPTZ DEFAULT now())
UNIQUE INDEX (dedup_key)
INDEX (origin, destination, price, id)     -- filter + keyset sort
INDEX (carrier, price, id)
INDEX (price, id)                          -- unfiltered keyset
```

---

## 8. Configuration (`application.yml` + `@ConfigurationProperties` records)

- `spring.threads.virtual.enabled: true`; `spring.jpa.hibernate.ddl-auto: validate` (schema owned by
  Flyway); actuator health probes enabled.
- `ota.markup-percentage` (5), `ota.connection.min-minutes` (30), `ota.connection.max-minutes` (1440).
- `providers.deadline` (6s), `providers.connect-timeout` (2s), `providers.read-timeout` (5s),
  `providers.{alpha,beta,gamma}.{base-url,path}`.
- `cache.search.ttl` (30s), `cache.search.max-size` (10000).
- `ingestion.startup.enabled` (false by default; true in compose), `ingestion.startup.initial-delay`
  (5s), `ingestion.refresh-interval` (5m).

---

## 9. Mock providers

Standalone Spring Boot app exposing `/alpha/flights` (JSON), `/beta/flights` (nested XML),
`/gamma/flights` (CSV) matching the parser contracts, plus `/health`. A deterministic
`FlightGenerator` seeded by `(origin, destination, date)` emits the **same flights across all three
providers at different prices** (so dedup is demonstrable), including some 1-stop itineraries. A
`HandlerInterceptor` injects configurable latency (100–2000ms) and a configurable 500 error rate
(~10%) on the provider paths. Config: `mock.latency.{min,max}-ms`, `mock.error-rate`,
`mock.itineraries-per-query`.

---

## 10. Frontend (`frontend/`, React 19 + Vite)

- `api.ts` — `GET /api/flights` (filters + keyset `cursor` + `size`), `POST /api/ingest`.
- `SearchForm` (origin/destination/carrier/maxPrice) with **debounced real-time filtering** (~300ms,
  guarded against partial IATA codes; parent search handler memoised to avoid effect loops).
- `IngestPanel` (trigger ingest, show summary), `ResultsTable` (keyset "Load more").
- nginx serves the SPA and reverse-proxies `/api` → backend (no CORS).
- Vitest + Testing Library tests (jsdom, `@testing-library/jest-dom/vitest` + explicit `cleanup`;
  exclude test files from the production `tsc` build).

---

## 11. Dockerisation (one command)

`compose.yaml`: `postgres`, `redis`, `mock-providers`, `backend`, `frontend`, wired with healthchecks
and `depends_on: condition: service_healthy` in dependency order. JVM runtime images are Ubuntu-based
`eclipse-temurin:25-jre` — **install `curl`** for the healthchecks (no `wget`/`curl` by default). The
backend image must copy `mock-providers/build.gradle.kts` and build root-scoped `:bootJar` (the
multi-module settings otherwise fail configuration). Backend runtime `ENTRYPOINT` includes
`--enable-preview`.

---

## 12. Testing strategy & quality gate

Real pyramid: pure-domain unit tests (dedup, markup rounding, connection rules, parsers with
malformed input, cross-provider key consistency, cursor round-trip, every value-object invariant);
`@WebMvcTest` slices (param mapping, ISO time serialisation, 400/500 paths); Testcontainers ITs
(keyset correctness + upsert-keeps-cheapest, Redis two-level + degradation, provider resiliency via a
fault-injecting JDK `HttpServer`, 100k-row high-volume keyset, full-stack controller→DB); frontend
Vitest. **JaCoCo** gate on `domain` (≥90% instruction / ≥80% branch), wired into `check`.

---

## 13. Key decisions & rationale 

- **Keyset over offset pagination.** Offset is O(m) per page (scans+discards skipped rows) and
  unstable under concurrent inserts; keyset seeks via the index and stays ~flat with depth. Requires a
  total/unique sort → append `id` tiebreaker; cursor is the `(price,id)` tuple as an opaque token.
  Trade-off: no jump-to-page / total count (fine for infinite scroll).
- **Structured concurrency for the fan-out.** Virtual threads + `StructuredTaskScope` with a deadline;
  one provider's failure/timeout yields a *partial* result. It is **preview in JDK 25** →
  `--enable-preview` everywhere (fallback: `newVirtualThreadPerTaskExecutor`).
- **Native `@Retryable` over Resilience4j.** Zero extra deps, Boot-4-native retry + per-attempt
  timeout; trade-off = no circuit breaker (documented; add Resilience4j as a decorator later).
- **Two-level cache with graceful degradation.** Caffeine near-cache + shared Redis; Redis outage is a
  logged miss, never a failed request.
- **`ItineraryKey` as a first-class record** → O(n) dedup via hashing; departures minute-truncated.
- **Denormalised read table + `jsonb` legs**; `Money` = `numeric(12,2)` + currency; times `timestamptz`.
- **Hardening:** request bean-validation, global exception advice (clean 4xx/5xx, no leaks, framework
  statuses preserved), per-provider connect/read timeouts, per-route scheduled-ingestion isolation,
  cache never throws, per-request isolation via virtual threads.

---

## 14. Recommended build order

1. Domain model (records + sealed types) + pure unit tests.
2. Domain services (markup, connection validation, aggregation) + tests.
3. Ports (in/out) + `IngestFlightsService` fan-out + fakes-based tests.
4. Provider adapters + parser strategies + native `@Retryable`.
5. Persistence (Flyway, keyset, batch upsert) + Testcontainers.
6. Two-level cache + graceful-degradation tests.
7. Search service + web controllers + DTOs + exception advice + slice tests.
8. Mock providers subproject.
9. Frontend + Vitest.
10. Compose wiring + one-command boot smoke test.
11. Resiliency IT (HTTP fault injection) + high-volume keyset IT + JaCoCo gate.
12. SOLUTION.md (decisions, keyset justification, limitations, AI tools).

---

## 15. Strategy pattern 

**Already applied — `ItineraryParser` is a textbook Strategy.** One interface, three interchangeable
algorithms (JSON/XML/CSV), selected per provider; the varying part (format decoding) is isolated from
the invariant part (fetch → domain → aggregate). Keep it.

**Highest-value places to introduce Strategy (genuine business-variability axes):**

1. **`FareSelectionStrategy` (recommended).** Today `AggregationService` hardcodes "cheapest wins".
   Extract `interface FareSelectionStrategy { Itinerary select(Itinerary current, Itinerary candidate); }`
   with a `CheapestFare` default; future strategies: `FastestItinerary` (shortest total duration),
   `FewestStops`, `BestValue` (price/duration score). `AggregationService` takes the strategy in its
   constructor and calls it in the `merge`. Clean, testable, matches a real OTA requirement ("sort by
   best/cheapest/fastest").

2. **`MarkupStrategy` (recommended).** `MarkupPolicy` is one flat-percentage rule. Promote to
   `interface MarkupStrategy { Money apply(Money base, Itinerary context); }` with `FlatPercentageMarkup`
   as the default; future: per-carrier, tiered-by-price, or route-based markup. Wire the chosen
   strategy in `DomainConfig`. This is a natural extension point for evolving commercial rules.

3. **`PaginationStrategy` (optional).** Abstract keyset vs offset behind the repository
   (`interface PaginationStrategy { SearchPage page(SearchCriteria, ...); }`) so an admin "jump to page"
   view could use offset while the public API keeps keyset. Only add if that second use case is real —
   otherwise it is speculative.

4. **`ConnectionRule` chain (optional refactor).** `ConnectionValidator` runs a fixed sequence of
   checks. It could become an ordered `List<ConnectionRule>` where each rule is a Strategy returning an
   optional rejection — improves extensibility if new validation rules are expected. Lower priority.

**Guidance:** implement **1 and 2** (`FareSelectionStrategy`, `MarkupStrategy`) — they map to real
OTA variability and keep the domain open for extension without touching the aggregation flow. Treat
3 and 4 as deferred; do **not** over-engineer stable single-implementation policies into strategies
without a second concrete implementation in sight.
```
Provider format decoding   → Strategy  (implemented: ItineraryParser)
Fare selection (cheapest)  → Strategy  (recommended: FareSelectionStrategy)
Markup rule (flat 5%)      → Strategy  (recommended: MarkupStrategy)
Pagination (keyset)        → Strategy  (optional: PaginationStrategy)
Connection validation      → Strategy chain (optional: List<ConnectionRule>)
```
