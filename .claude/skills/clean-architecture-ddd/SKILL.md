---
name: clean-architecture-ddd
description: Clean Architecture / Hexagonal (Ports & Adapters) + DDD for this Spring Boot service. Use when creating packages, deciding where a class belongs, defining ports/adapters, or reviewing that the domain stays framework-free. Covers the aggregation/deduplication domain and the multi-provider ingestion boundary.
---

# Clean Architecture + DDD (Ports & Adapters)

Goal from the brief: **the core domain business rules must be isolated entirely from frameworks, network clients, and database components.** Dependencies point *inward*.

## Layering (dependency direction: adapters → application → domain)

```
com.ota.flightagg
├── domain/                 # PURE Java. No Spring, no JPA, no Jackson, no HTTP.
│   ├── model/              #   Itinerary, Leg, Money, Carrier, Price (records + value objects)
│   ├── service/            #   AggregationService, DeduplicationPolicy, MarkupPolicy (5% OTA markup)
│   └── port/
│       ├── in/             #   use-case interfaces (SearchFlightsUseCase, IngestFlightsUseCase)
│       └── out/            #   driven ports (FlightProviderPort, FlightRepositoryPort, CachePort)
├── application/            # Orchestration: implements in-ports, calls out-ports. Spring @Service ok here.
│   └── ...                 #   transaction boundaries, structured-concurrency fan-out
├── adapter/
│   ├── in/web/             #   REST controllers, DTOs, pagination params  → call in-ports
│   ├── out/provider/       #   Alpha(JSON) / Beta(XML) / Gamma(CSV) clients → implement FlightProviderPort
│   ├── out/persistence/    #   JPA entities + repositories → implement FlightRepositoryPort
│   └── out/cache/          #   Redis/Caffeine → implement CachePort
└── config/                 # Spring wiring, beans, properties, resilience config
```

Package-by-feature is fine too, but **each feature must keep the same inward layering**. Start simple; do not over-engineer CRUD.

## DDD rules for this domain

- **Ubiquitous language**: `Itinerary`, `Leg`, `Connection`, `Carrier`, `DeadLeg`, `Markup`, `Provider`, `Cursor`. Name code exactly as the business does.
- **Value objects** (records, immutable): `Money`/`Price` (amount + currency, never a bare `BigDecimal` floating around), `Airport`, `FlightNumber`, `Itinerary` identity key.
- **The deduplication identity** = (airline, ordered flight numbers, connections, departure times). Model this as an explicit value object / key type — it is the heart of the domain, so make it a first-class, unit-testable thing.
- **Domain services** hold the rules that don't belong to one entity: choose-cheapest, apply-5%-markup, filter-dead-legs / invalid-connections. Keep them pure and deterministic → trivially unit-testable with no Spring context.
- **Ports are owned by the domain/application, implemented by adapters.** The domain depends on `FlightProviderPort` (an interface it defines), never on an HTTP client.

## Boundary mapping
- Each provider adapter is responsible for translating its legacy format (raw JSON list / nested XML / CSV stream) into the **same** domain model. The domain never sees provider-specific shapes.
- Controllers map domain results → web DTOs. Never return JPA entities or domain objects directly over HTTP.
- Persistence adapter maps domain ↔ JPA entities. Domain has no `@Entity`.

## Review checklist
- [ ] `domain/` has zero framework imports (grep for `org.springframework`, `jakarta.persistence`, `com.fasterxml` under domain → must be empty).
- [ ] Business rules are in domain services, not controllers or adapters.
- [ ] Every external dependency crosses an out-port interface.
- [ ] Domain is unit-testable with plain JUnit, no `@SpringBootTest`.
