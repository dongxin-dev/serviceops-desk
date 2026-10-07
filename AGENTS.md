# AGENTS.md

Repository-level instructions for AI coding agents (and humans) on
**ServiceOps Desk**. Local-only additions live under `.qoder/`
(git-ignored) and reinforce, not replace, this file.

## Project Purpose

ServiceOps Desk is a service-desk / ticketing platform. Today the
repo contains a Spring Boot backend (Java 21, Flyway + PostgreSQL),
placeholders for a Vue 3 + TypeScript frontend, and a **frozen**
Domain Model, ER Diagram, and Ticket State Machine. Those documents
under `docs/**` are authoritative for business behavior. Do not
invent domain rules.

## Repository Structure

```
backend/                Spring Boot app (Java 21, Maven Wrapper)
  src/main/resources/application.yml         base config, no secrets
  src/main/resources/db/migration/           Flyway SQL, immutable once committed
  src/test/java/                              JUnit tests
  mvnw / mvnw.cmd                             always use the Wrapper
  pom.xml                                     no new dependency without approval
frontend/               Vue 3 + TypeScript (placeholder in V1)
config/application-local.yml   local Spring overrides (git-ignored)
docs/architecture/      Domain Model, Ticket State Machine, ADRs
docs/database/          ER Diagram
scripts/                developer + quality-gate entry points
  verify.ps1            unified quality gate
.env.example            only committed dotenv file (placeholders)
docker-compose.yml      local runtime orchestration
```

No personal names, hostnames, or absolute local paths in this file
or in commits.

## Migrations

- Path: `backend/src/main/resources/db/migration/`.
- Committed migrations are **immutable**: no edit, rename, or delete
  on any file that already exists on `origin/main` (or the base
  branch of your work).
- Schema / data changes go into a **new** `V{n}__....sql` with `n`
  strictly greater than any existing version.
- Do not add tables, columns, constraints, or enum values that the
  Domain Model / ER Diagram does not already authorize. If one is
  genuinely needed, get human confirmation before writing it.

## Local Secrets Must Never Be Committed

Never commit:

- `.env` / `.env.*` (only `.env.example` with placeholders is allowed)
- `application-local.yml` / `.yaml` / `.properties` (any directory)
- `*.pem`, `*.key`, `*.p12`, `*.pfx`
- Maven `settings.xml` / `settings-*.xml`; only
  `backend/.mvn/settings-public.xml` is sanctioned and stays
  credential-free
- Real tokens, passwords, API keys, or session secrets in source,
  tests, samples, docs, or logs

`.gitignore` already enforces this; do not weaken it. If you suspect
a secret was committed, stop and report.

## Domain Design Is Authoritative

Read before touching tickets / assignments / escalations / SLA:

- `docs/architecture/domain-model.md`
- `docs/architecture/ticket-state-machine.*`
- `docs/database/serviceops-er-diagram.*`

The Ticket State Machine is frozen. Never add states, add
transitions, or bypass it via a raw `PATCH` on the status field.

## Change Discipline

- Small, scoped changes only. No opportunistic refactor / format /
  rename. Do not modify `pom.xml`, `docker-compose.yml`, existing
  `application.yml`, existing READMEs, or committed migrations
  unless the task explicitly authorizes it.
- Discover an unrelated problem? Report it, do not silently widen
  the task.

## Testing

- Core business rules **must** have automated tests.
- State-machine transitions require tests for both legal and illegal
  paths.
- Bug fixes come with a regression test.
- Never delete or disable a failing test to obtain green. Never
  weaken a gate to make it pass.

## Quality Gate

Before declaring any work complete, run:

```powershell
.\scripts\verify.ps1
```

Order: Secrets Guard -> Migration Immutability Guard -> Backend
Build & Test (`mvnw clean verify`). Prints
`ServiceOps Desk QUALITY GATE: PASS` or `FAIL` and returns a
matching exit code. Any failing step halts the run. A task is not
done until the gate passes or the reason it cannot pass is reported.

## Stop and Report

If a requirement conflicts with the Domain Model, ER Diagram,
Ticket State Machine, a committed migration, or a governance rule,
**stop and surface the conflict** to the human. Do not pick a
resolution unilaterally. Do not invent domain behavior to appear
productive. Report assumptions explicitly.

## Completion Report

End every non-trivial change with:

- Files added / modified / deleted, one line each.
- Tests executed + results.
- `.\scripts\verify.ps1` outcome.
- Database / API / business-rule impact.
- Outstanding issues and assumptions.

See `.qoder/skills/feature-development/SKILL.md` (local) for the
step-by-step workflow that produces this report.
