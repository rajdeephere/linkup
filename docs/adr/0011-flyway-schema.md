# ADR-0011 — Flyway-owned schema; Hibernate `ddl-auto=validate`

**Status:** Accepted (Phase 0, implemented Day 1) · **Date:** 2026-06-24

## Context
Schema must be deterministic, reviewable, and identical across environments. `hibernate.ddl-auto=update`
lets Hibernate infer and apply changes at startup — convenient but unversioned, unreviewed, and
divergent between environments; it can silently lock or rewrite tables in prod.

## Decision
The schema is **versioned SQL** (`V1__…sql`, `V2__…`) owned by **Flyway**. Hibernate runs
**`ddl-auto=validate`**: it asserts entity mappings match the real schema at boot and **never mutates**
it. Migrations are applied in order, the same everywhere.

## Consequences
- Schema changes are PR-reviewable, ordered, and deterministic; data migrations/rollbacks are possible.
- Mapping drift is caught as a **startup failure**, not a runtime surprise.
- Slightly more ceremony (write the migration) — the right trade for production safety.

## Alternatives
- **`ddl-auto=update`:** convenient in dev, dangerous and non-deterministic; rejected.
- **`ddl-auto=none` + manual SQL:** loses the validate safety net and the ordered migration history.

## Revisit if
Never for the core stance. Tooling could change (Liquibase) but the principle — migrations own the
schema, ORM validates — stays.
