# Assessment Brief (verbatim)

Take-home technical assessment for an Online Travel Agency (OTA). Two parts: take-home + live session.

## 1. Business Context & Objective
As an Online Travel Agency (OTA), our core value proposition is finding the best flights across multiple global distribution networks. You are tasked with building a **Flight Aggregation & Cache Optimization System**. The application must ingest, process, deduplicate, and display flight itineraries sourced from three distinct external Global Distribution Systems (GDS Providers: **Mock-Alpha, Mock-Beta, and Mock-Gamma**).

## 2. Core Functional Requirements
- **Multi-Provider Integration:** Ingestion layer that queries all 3 mock APIs simultaneously. Each provider returns data in different legacy structures (Alpha = raw JSON lists, Beta = deeply nested XML structures, Gamma = comma-separated string streams).
- **Aggregation & Deduplication Logic:** Identify identical itineraries (same airline, flight numbers, connections, and departure times) offered by different providers. Core domain must select the cheapest option, apply a default **5% OTA operational markup**, and filter out dead legs / invalid connections.
- **High-Volume Optimization:** Handle an inventory profile of **100,000 to 1,000,000 flight combinations daily**.
- **Search API & Frontend:** Clean search API with high-performance filtering (Origin, Destination, Max Price, Carrier) using an optimized pagination strategy (**justify Keyset vs Offset** in docs). Frontend dashboard to search, filter, and visualize itineraries in real-time.

## 3. Technical Constraints & Architecture
- **Clean Architecture / DDD:** Isolate core domain business rules entirely from third-party frameworks, network clients, and database components.
- **Resiliency:** Mock provider APIs have artificial latencies (**100ms–5000ms**) and occasional **500 errors**. Aggregation must not block or fail entirely if one provider drops out.
- **Dockerization:** Complete stack (Backend, Frontend, Database/Cache layer) must boot with a single command: `docker compose up --build`.

## 4. Delivery Expectations
Provide a Git repository **24 hours before the interview** containing complete source code, a comprehensive test suite (unit and integration tests), and a **SOLUTION.md** detailing:
- Architectural decisions and AI tools utilized (prompts / agent systems engineered to write the code).
- Justification for data structure modeling and pagination handling up to 1,000,000 rows.
- System limitations and trade-offs made.
