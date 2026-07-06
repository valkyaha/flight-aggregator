---
name: java-modern
description: Modern Java (JDK 25 LTS) idioms and Spring Boot 4.x conventions. Use when writing or reviewing any Java code in this project — records, sealed types, pattern matching, virtual threads, structured concurrency, Scoped Values, and Spring Boot 4 / Spring Framework 7 usage.
---

# Modern Java (JDK 25 LTS) + Spring Boot 4.x

Toolchain for this project: **Java 25 LTS**, **Spring Boot 4.1.x** (Spring Framework 7), **Gradle (Kotlin DSL) via the wrapper**. Verified current as of July 2026.

## Language idioms — prefer these

- **Records** for all immutable data carriers: DTOs, value objects, API request/response, provider payloads, keyset cursors. No Lombok needed for data classes.
- **Sealed interfaces + records + switch pattern matching** to model closed domain hierarchies (e.g. the three GDS provider results, or a `ParseResult` = `Success | DeadLeg | Invalid`). The compiler enforces exhaustiveness — no `default` branch needed, so a new variant becomes a compile error until handled.
- **Pattern matching for switch** with record deconstruction and `when` guard clauses instead of `instanceof` ladders.
- **Text blocks** for embedded XML/JSON test fixtures and SQL.
- **`var`** for local variables where the RHS type is obvious; keep explicit types on fields and public API.
- **Streams + `toList()`**, `Optional` at boundaries (never as a field or parameter).

## Concurrency — the resiliency backbone of this assessment

- **Virtual threads** (JEP 491 pinning fix is final in 25 — `synchronized` no longer pins). Enable in Spring Boot with `spring.threads.virtual.enabled=true`. Ideal for the fan-out to 3 blocking provider APIs.
- **Structured concurrency** (`StructuredTaskScope`, final in 25) for the parallel provider fan-out: launch all three provider calls, join, and treat one provider's failure/timeout as a *partial* result rather than failing the whole aggregation. Use a scope policy that collects successful subtasks and lets slow/failed ones be abandoned by a deadline.
- **Scoped Values** replace `ThreadLocal` for request-scoped context in virtual-thread-heavy code.
- Never hand-manage thread pools for I/O fan-out; let virtual threads + structured concurrency handle it.

## Spring Boot 4.x notes

- `compose.yml` is auto-detected by `spring-boot-docker-compose` for local dev; keep it out of the production image.
- Prefer constructor injection (no `@Autowired` needed on a single constructor). Keep Spring annotations out of the domain layer entirely — see the `clean-architecture-ddd` skill.
- Use `RestClient` (not the deprecated `RestTemplate`) for the provider HTTP adapters.
- Configuration via `@ConfigurationProperties` records with validation, not scattered `@Value`.
- Actuator for health/metrics; expose `/actuator/health` for Docker healthchecks.

## Anti-patterns to reject
- Mutable POJOs with getters/setters where a record fits.
- `instanceof` + cast chains where a sealed switch fits.
- Field/setter injection; static singletons holding state.
- Framework annotations (`@Entity`, `@Component`, Jackson) leaking into domain types.
- Manual `ExecutorService` for provider fan-out instead of structured concurrency.
