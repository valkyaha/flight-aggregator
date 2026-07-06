---
name: docker-postgres-cache
description: Dockerization, PostgreSQL modeling for 100k–1M rows, keyset pagination, and Redis/Caffeine caching for this project. Use when writing Dockerfile/compose.yml, designing the schema and indexes, implementing pagination, or configuring the cache and resilience.
---

# Docker, PostgreSQL & Cache

Hard requirement: **the whole stack (backend, frontend, DB/cache) must boot with a single `docker compose up --build`.**

## Docker
- **Multi-stage build.** Stage 1: Gradle + JDK 25 builds the layered jar. Stage 2: slim JRE 25 runtime (e.g. `eclipse-temurin:25-jre` or distroless), copy only the layered jar / extracted layers. Small image, small attack surface, non-root user.
- Use **Spring Boot layered jars** (`bootBuildImage` or manual layer extraction) so dependency layers cache between builds.
- File name: **`compose.yml`** (Compose V2). Services: `backend`, `frontend`, `postgres`, `redis` (+ optional `mock-providers`).
- **Healthchecks + `depends_on: condition: service_healthy`** so the backend waits for Postgres and Redis to be truly ready. Backend health = `/actuator/health`.
- Config via **environment variables** (DB URL, credentials, Redis host, markup %), never hardcoded. Provide a `.env` with sane defaults.
- Persist Postgres with a named volume. Keep dev-only `spring-boot-docker-compose` support out of the prod image.

## PostgreSQL: modeling for 100k–1M rows/day
- Store aggregated/deduplicated itineraries in a query-optimized table. Model `Money` as `numeric(12,2)` + currency `char(3)`; times as `timestamptz`.
- **Indexing for the search API** (Origin, Destination, Max Price, Carrier + pagination): composite indexes that match filter + sort order, e.g. `(origin, destination, price, id)` and `(carrier, price, id)`. The trailing sort key (`price, id`) is what makes keyset pagination index-only.
- Consider partitioning / a rolling retention window given daily volume (1M/day grows fast) — mention as a scaling lever in SOLUTION.md.
- Bulk ingest: batch inserts / `COPY`-style loading, not row-by-row; use a staging table then upsert.

## Pagination: Keyset vs Offset (must be justified in SOLUTION.md)
- **Use Keyset (cursor) pagination.** Offset degrades to O(n) at depth — Postgres still scans and discards all skipped rows, and offset can skip/duplicate rows when data shifts under concurrent ingestion. Keyset stays ~O(1) via the index and is stable under inserts. At up to 1M rows with continuous daily ingestion, offset deep pages are a non-starter.
- Cursor = the last row's sort key tuple, e.g. `WHERE (price, id) > (:lastPrice, :lastId) ORDER BY price, id LIMIT :n`. Encode the tuple into an opaque base64 cursor token returned to the client.
- Requires a **stable, unique, total** sort order — always append a unique tiebreaker (`id`) to the sort key.
- Trade-off to document: no random "jump to page N" and no total count by default — acceptable for an infinite-scroll / next-page flight search UI. Offer offset only for shallow admin views if needed.

## Caching (Redis + optional Caffeine)
- **Two-level cache**: Caffeine (in-process, sub-ms, hot searches) → Redis (shared across instances, global invalidation) → DB. Read-through with Spring `@Cacheable`.
- Cache the **search results / aggregated itineraries** keyed by normalized query (origin+dest+filters+cursor). Key prefixes to avoid collisions; TTL tuned to inventory freshness (flight data is time-sensitive — short TTL + explicit evict on re-ingest).
- **Graceful degradation**: if Redis is down, fall back to Caffeine/DB, never fail the request. Wrap cache access so a cache outage is a logged miss, not a 500.
- Monitor hit/miss ratio via Actuator metrics.

## Resilience (provider adapters) — Resilience4j
- Per-provider **TimeLimiter** (deadline below the 5000ms worst case), **CircuitBreaker** (e.g. sliding-window 20, failure-rate 50%, wait-in-open 10s, half-open 3), and **Retry** only for transient 5xx/timeouts — never for 4xx/validation.
- Ordering: Retry is outermost so each attempt is recorded by the breaker; TimeLimiter guards the blocking call. Combine with virtual threads + structured concurrency for the fan-out (see `java-modern`).
- One provider failing must yield **partial aggregation**, never a total failure.
