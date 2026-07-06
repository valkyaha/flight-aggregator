# CLAUDE.md — Flight Aggregation & Cache Optimization System

Project guidance for Claude Code. Read this before doing anything in this repo.

## What this is
A take-home technical assessment: a **Flight Aggregation & Cache Optimization System** for an Online Travel Agency (OTA). It ingests, deduplicates, and serves flight itineraries from three mock GDS providers (Alpha=JSON, Beta=XML, Gamma=CSV), applies a 5% OTA markup, and exposes a filtered, paginated search API plus a frontend dashboard.

Full requirements: see `docs/ASSESSMENT.md`. Prompt history: `docs/PROMPTS.md`.

## Non-negotiable working rules (from the assessment owner, Miguel Ángel)
1. **Do NOT write or modify any application code without explicit permission.** Setup docs, skills, and this file are pre-authorized. Everything else requires a green light per change.
2. **Build tool is Gradle** (Kotlin DSL, via the wrapper). Never Maven.
3. **Every prompt the user gives must be appended verbatim to `docs/PROMPTS.md`** — it feeds the required SOLUTION.md "AI tools utilized" section.
4. **Skills come first.** The `.claude/skills/*` files encode the standards; consult and follow them.
5. **No AI attribution in git — ever.** Never add Claude/Anthropic as a commit co-author and never mention Claude/AI in commit messages, PR bodies, or code comments. Commits read as authored solely by the user. (The `docs/PROMPTS.md` prompt log is a required deliverable and is exempt.) This rule persists across all sessions.

## Toolchain (verified July 2026)
- Java **25 LTS**, Spring Boot **4.1.x** (Spring Framework 7), Gradle wrapper, Docker Compose V2.
- Postgres + Redis in the stack. Must boot via `docker compose up --build`.

## Skills — load the relevant one before working
- `java-modern` — JDK 25 idioms (records, sealed, pattern matching, virtual threads, structured concurrency) + Spring Boot 4 conventions.
- `clean-architecture-ddd` — Ports & Adapters layering, DDD modeling, keeping the domain framework-free.
- `testing-quality` — JUnit 5, Testcontainers, the test pyramid, resiliency tests, coverage gates.
- `docker-postgres-cache` — Dockerfile/compose, schema + indexing for 1M rows, keyset pagination, Redis/Caffeine caching, Resilience4j.

## Architecture in one line
Clean/Hexagonal: `domain` (pure) ← `application` (orchestration) ← `adapter` (web / provider / persistence / cache). Dependencies point inward; the domain has zero framework imports.

## Deliverables to keep in mind
- Complete source + comprehensive unit & integration tests.
- `SOLUTION.md`: architectural decisions, AI tools/prompts used, data-structure & pagination justification (keyset vs offset up to 1M rows), limitations & trade-offs.
- Everything boots with one command. Repo due 24h before the interview.

## Definition of done for any change
Follows the relevant skill, keeps the domain pure, has tests, and doesn't break `docker compose up --build`. Verify before claiming done.
