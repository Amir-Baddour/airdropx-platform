# AirdropX

![CI](https://github.com/Amir-Baddour/airdropx-platform/actions/workflows/ci.yml/badge.svg)

A Web2 SaaS platform for managing token/points airdrop campaigns — two portals (company/client and
platform admin), a real async job pipeline, and a Postgres schema built around multi-tenancy, an audit
trail, and idempotent mutations.

**This is a portfolio project**, built to demonstrate backend architecture and engineering judgment
(Spring Boot, Postgres, Redis-backed async processing, JWT auth) for backend/software engineering roles.
It is not a production system, and it does not move real assets — see [Mocked Distribution](#mocked-distribution-read-this-first)
below before you judge (or demo) it as anything else.

## Table of contents

- [Mocked distribution — read this first](#mocked-distribution-read-this-first)
- [Architecture](#architecture)
- [Tech stack](#tech-stack)
- [Project structure](#project-structure)
- [Adaptations from the original diagrams](#adaptations-from-the-original-diagrams)
- [Getting started](#getting-started)
- [Try it — curl walkthrough](#try-it--curl-walkthrough)
- [What's real vs. mocked vs. not built](#whats-real-vs-mocked-vs-not-built)
- [Why Spring Boot 3.3, not 4.1](#why-spring-boot-33-not-41)
- [Roadmap](#roadmap)
- [Continuing this in Claude Code](#continuing-this-in-claude-code)

## Mocked distribution — read this first

AirdropX does not hold, sign for, or move real crypto, tokens, or funds of any kind. There is no wallet
integration and no blockchain connection. When an airdrop is "launched," a background worker simulates
processing each recipient (including a small random failure rate, so partial-failure states are real and
demoable, not just theoretical) and writes the result to the database. `asset_type` is a free-text label,
not a real token contract or chain reference.

Everything **around** that simulated step is real: the state machine, the queue, the worker, crash
recovery, idempotency, the audit trail, and the multi-tenant data model. That's deliberate — those are
the parts that demonstrate backend engineering. Actually moving funds would mean picking a chain, a
custody model (custodial vs. non-custodial vs. delegated to a provider), and taking on real compliance
and security exposure — out of scope for a portfolio build. See [Roadmap](#roadmap).

## Architecture

```
                          ┌─────────────────┐
                          │   Next.js UI     │
                          │  (client portal) │
                          └────────┬─────────┘
                                   │ REST + JWT
                                   ▼
                  ┌────────────────────────────────┐
                  │      Spring Boot backend        │
                  │  /api/v1/auth    (shared)       │
                  │  /api/v1/user    (company-scoped)│
                  │  /api/v1/admin   (platform-wide) │
                  └───────┬──────────────┬──────────┘
                          │              │
                          ▼              ▼
                  ┌──────────────┐  ┌──────────┐
                  │  PostgreSQL  │  │  Redis   │
                  │  (Flyway-    │  │  (job    │
                  │   migrated)  │  │   queue) │
                  └──────────────┘  └────┬─────┘
                                          │ BRPOP
                                          ▼
                                  ┌───────────────┐
                                  │ AirdropWorker  │
                                  │ (daemon thread,│
                                  │ crash-recovery)│
                                  └───────────────┘
```

**The airdrop lifecycle** (see `common/enums/AirdropStatus.java` for the enforced transitions):

```
DRAFT → VALIDATING → READY → QUEUED → RUNNING → COMPLETED | PARTIALLY_COMPLETED | FAILED
  │         │           │        │
  └─────────┴───────────┴────────┴──→ CANCELLED (allowed any time before RUNNING)
```

A launch pushes a job id onto a Redis list; `AirdropWorker` blocks on it, hands the job to
`AirdropJobProcessor`, which processes recipients in small batches — each batch its own committed
transaction, so a crash mid-job loses at most one in-flight batch, not the whole run. On restart, the
worker finds any job orphaned in `RUNNING` status and requeues it (up to a retry limit) before it
touches the live queue. This is the "worker crash/retry" edge case from the original design, made real —
kill the backend mid-airdrop and watch it resume from where it left off.

## Tech stack

| Layer | Choice |
|---|---|
| Frontend | Next.js 16 (App Router), React 19, TypeScript, Tailwind v4 |
| Backend | Spring Boot 3.3.5, Java 21, Spring Security (JWT), Spring Data JPA |
| Database | PostgreSQL 16, Flyway migrations |
| Queue | Redis (a plain list + `BRPOP`-style blocking pop — no broker beyond Redis itself) |
| Docs | springdoc-openapi → Swagger UI at `/swagger-ui.html` |
| Infra | Docker Compose (Postgres, Redis, backend, frontend) |

## Project structure

```
airdropx/
├── backend/
│   └── src/main/
│       ├── java/com/airdropx/
│       │   ├── auth/            # shared login/register/refresh — used by both portals
│       │   ├── user/            # company-scoped: company, airdrop, dashboard
│       │   ├── admin/           # platform-wide reads, ROLE_PLATFORM_ADMIN only
│       │   ├── worker/          # AirdropWorker (orchestrator) + AirdropJobProcessor (transactional units)
│       │   ├── security/        # JWT issuance/validation, Spring Security wiring
│       │   ├── model/           # JPA entities — one per migration
│       │   ├── repository/      # Spring Data JPA interfaces (shared across user/admin)
│       │   └── common/          # enums, exceptions, audit log, idempotency service, PageResponse
│       └── resources/db/migration/   # V1–V10, tested against a live Postgres instance
├── frontend/
│   ├── app/(app)/                # authenticated shell: dashboard, airdrops list/new/detail
│   ├── app/login, app/register/  # public auth pages
│   └── lib/                      # typed API client (auto refresh-on-401), token storage
├── docker-compose.yml
└── .env.example
```

## Adaptations from the original diagrams

A few deliberate departures from the original ERD/architecture sketches, each for a concrete reason:

- **Repositories and JPA entities are shared, not duplicated per portal.** The original diagram showed
  `repositories/` under both `admin/v1/` and `user/v1/`. A `User` row or an `Airdrop` row isn't naturally
  "admin's" or "the company's" — duplicating the repository layer would just be two interfaces querying
  the same table. Controllers and services *do* stay separate (`user/` vs `admin/`), since that's where
  the actual behavioral difference lives (company-scoped vs. cross-tenant).
- **Auth is its own top-level module, not duplicated under `/user/auth` and `/admin/auth`.** Login is
  the same operation regardless of who's logging in — the JWT encodes the role, and `SecurityConfig`
  enforces `/api/v1/admin/**` requiring `ROLE_PLATFORM_ADMIN` at the filter-chain level. A second,
  near-identical auth controller would just be duplicated code with no real behavioral difference.
- **The worker is one in-process daemon thread, not separate Docker worker containers.** For the
  workload a demo project generates, one thread reading off a Redis list does everything the diagram's
  "Worker 1 / Worker 2 / Worker N" containers would, without the operational overhead of running and
  scaling separate containers for it. The `AirdropWorker` / `AirdropJobProcessor` split (see below)
  is what would let this become real horizontally-scaled workers later without touching the processing
  logic itself — you'd extract `AirdropJobProcessor` into its own deployable, point several instances
  at the same Redis list, and Redis's `BRPOP` semantics already guarantee each job goes to exactly one
  consumer.
- **`AirdropWorker` (orchestrator) and `AirdropJobProcessor` (transactional work) are two separate Spring
  beans, not one class.** Spring's `@Transactional` is implemented as a proxy around the *bean*, so a
  method calling another `@Transactional` method **on itself** silently skips the proxy — the transaction
  boundary you asked for doesn't happen. Splitting the loop (untransactional, lives in `AirdropWorker`)
  from the actual DB work (three separate `@Transactional` methods, lives in `AirdropJobProcessor`) is
  what makes each batch its own real commit, which is what makes crash recovery real instead of
  theoretical.
- **CSV upload via Firebase Storage isn't wired up.** Recipients are added as a JSON array in the request
  body. The `file_metadata` table and its migration exist and are correct — nothing writes to it yet. See
  [Roadmap](#roadmap).
- **No separate `/validate` step is exposed as async.** The original pipeline diagram shows validate as
  its own queued phase; in v1 it's a synchronous endpoint (`POST /airdrops/{id}/validate`) since the
  actual validation (recipient count > 0, total > 0) is cheap enough not to need a worker. The
  `VALIDATING` status value exists in the schema and is set momentarily during the call.

## Getting started

### Option A — Docker Compose (full stack, least setup)

```bash
git clone <this-repo>
cd airdropx
cp .env.example .env      # defaults work for local use — change JWT_SECRET for anything shared
docker compose up --build
```

- Frontend: http://localhost:3000
- Backend API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html

Flyway runs the migrations automatically on backend startup — nothing to run by hand.

### Option B — Run locally without Docker

You'll need Postgres 16 and Redis running locally (or point at any instance via env vars).

```bash
# Backend
cd backend
export DB_HOST=localhost DB_NAME=airdropx DB_USER=airdropx DB_PASSWORD=airdropx
export REDIS_HOST=localhost JWT_SECRET=dev-only-change-me-this-is-not-a-real-secret-32chars
mvn spring-boot:run

# Frontend (separate terminal)
cd frontend
npm install
npm run dev
```

## Try it — curl walkthrough

This exercises the full pipeline end to end, including the idempotency protection and the
cancel-while-running edge case.

```bash
BASE=http://localhost:8080/api/v1

# 1. Register (creates a company + you as its owner) — save the accessToken from the response
curl -s -X POST $BASE/auth/register -H "Content-Type: application/json" -d '{
  "companyName": "Acme Inc", "companyEmail": "ops@acme.io",
  "email": "ada@acme.io", "password": "correct-horse-battery",
  "firstName": "Ada", "lastName": "Lovelace"
}' | tee /tmp/auth.json

TOKEN=$(jq -r .accessToken /tmp/auth.json)
AUTH="Authorization: Bearer $TOKEN"

# 2. Create a draft airdrop
AIRDROP_ID=$(curl -s -X POST $BASE/user/airdrops -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"name":"Q4 Community Drop","description":"test","assetType":"USDT"}' | jq -r .id)

# 3. Add recipients
curl -s -X POST $BASE/user/airdrops/$AIRDROP_ID/recipients -H "$AUTH" -H "Content-Type: application/json" -d '{
  "recipients": [
    {"recipientAddress": "0xabc111", "amount": 100.5},
    {"recipientAddress": "0xdef222", "amount": 250}
  ]
}'

# 4. Validate (DRAFT -> READY)
curl -s -X POST $BASE/user/airdrops/$AIRDROP_ID/validate -H "$AUTH"

# 5. Launch (READY -> QUEUED, 202 Accepted) — Idempotency-Key is required
curl -s -X POST $BASE/user/airdrops/$AIRDROP_ID/launch -H "$AUTH" -H "Idempotency-Key: demo-key-1"

# 5b. Replay the exact same request — same result is returned, the job is NOT launched twice
curl -s -X POST $BASE/user/airdrops/$AIRDROP_ID/launch -H "$AUTH" -H "Idempotency-Key: demo-key-1"

# 6. Watch it process (poll every couple of seconds — the frontend does this automatically)
watch -n2 "curl -s $BASE/user/airdrops/$AIRDROP_ID -H '$AUTH' | jq '{status, recipientCount}'"

# 7. See the full event timeline
curl -s $BASE/user/airdrops/$AIRDROP_ID/events -H "$AUTH" | jq

# --- Separately: the cancel-while-running edge case ---
# Launch a second airdrop and try to cancel it once RUNNING — this returns 409 CANNOT_CANCEL.
# Cancel it *before* that (while DRAFT/READY/QUEUED) and it succeeds immediately.
```

For the admin API, register a user with `role = PLATFORM_ADMIN` directly in the database (there's no
public admin-signup endpoint, deliberately — see `chk_company_scope` in `V2__create_users.sql`), log in
normally, then hit `GET /api/v1/admin/dashboard`, `/companies`, `/users`, `/airdrops`, `/audit-logs`.

## What's real vs. mocked vs. not built

| | |
|---|---|
| **Fully real** | Multi-tenant schema + isolation, JWT auth with refresh rotation, the airdrop state machine, the Redis-backed worker with per-batch transactions and crash recovery, idempotency-key protection on launch, the audit log, the admin cross-tenant read API |
| **Deliberately mocked** | Recipient "delivery" (simulated with a random failure rate — see `AirdropJobProcessor`) |
| **Schema exists, not wired up** | `file_metadata` / CSV upload via Firebase Storage — recipients are JSON for now |
| **Not built** | Billing/subscriptions, email sending (password reset, notifications), an admin frontend UI (the admin *API* is fully functional — see Swagger) |

## Recipient claims (manual review)

Besides uploading a recipient list, a company can let people **claim** an airdrop:

1. The company adds **tasks** to a DRAFT airdrop (e.g. "Follow us on X", proof required or not), sets the amount
   each approved claimant receives, and opens claiming.
2. Anyone with the public link (`/api/v1/public/airdrops/{id}`, no login) sees the tasks, submits their address
   plus proof for each task, and can check their result later by address.
3. The company reviews each claim in a queue (`/claims?status=PENDING`) and **approves or rejects** it.
   Approval turns the claimant into a normal recipient, so validate → launch → worker → progress is the
   existing, tested pipeline. Claim review decides only *who gets in*.
4. `validate` freezes the list and closes claiming; approvals after that return 409.

**UI:** companies manage this at `/airdrops/{id}/claims` (settings, tasks, shareable link, review queue). Claimants
use the public page `/claim/{id}` (no account): submit address and proofs, or check status by address.

Why manual review: tasks like "like a post on Facebook" can't be verified automatically without the
platform's API and the user's consent, so the honest design is evidence + human review.

Abuse protection: one claim per address (case-insensitive, enforced by a DB unique constraint), proof
length limits, closed/unknown airdrops all return the same 404, and the public endpoints are rate limited per
IP (Redis, `CLAIMS_RATE_LIMIT_PER_MINUTE`, default 10). Not built: bot/captcha protection, email or wallet
ownership verification — a real deployment would add those.

## Testing and CI

The backend has **integration tests that run the whole application against real Postgres and Redis**
(Testcontainers) — real Flyway migrations, real JPA, real queue, real worker thread. Nothing in the data
path is mocked, because the bugs this project actually hit lived in the seams between components.

```bash
cd backend
mvn verify          # needs Docker running (Docker Desktop on Windows)
```

| Test class | What it proves |
|---|---|
| `AuthIntegrationTest` | register/login/me, duplicate email 409, validation errors, 401 (not 403) when unauthenticated, refresh-token rotation, logout revocation |
| `ProfileIntegrationTest` | personal profile (name/phone/address — exercises migration V11) and company profile persist |
| `TenantIsolationIntegrationTest` | company B can't read/modify/launch company A's airdrop (404); company users blocked from `/admin`; admin blocked from `/user` |
| `AirdropLifecycleIntegrationTest` | full create → recipients → validate → launch → worker → COMPLETED; **Idempotency-Key replay creates one job**; key reuse 409; missing key 400; double launch 409; cancel rules; totals; dashboard; audit log |
| `ClaimsIntegrationTest` | public claim → review → approve → launch → paid; one claim per address; proof validation; reject with reason; closed claims invisible; approvals blocked after validate; tenant isolation; per-IP rate limiting (429) |
| `WorkerRecoveryIntegrationTest` | crash recovery resumes a RUNNING job without re-paying finished recipients; gives up after max attempts; ignores stale queue messages for cancelled airdrops |

The test profile (`src/test/resources/application-test.yml`) turns the simulated failure rate to 0 and
shortens batch delays so runs are deterministic and fast.

**CI** (`.github/workflows/ci.yml`) runs on every push/PR: backend `mvn verify` (tests included), frontend
`lint` + `tsc` + `build`, then both Docker images are built.

Two bugs found while writing these tests are fixed in the code: crash recovery used to requeue the job but
then skip it (the airdrop was still `RUNNING`), and a missing `Idempotency-Key` header returned 500 instead of 400.

## Why Spring Boot 3.3, not 4.1

Spring Boot 4.1 is current as of writing, but it carries real breaking changes from the 3.x line this
was scaffolded against — Jackson 3's package namespace move, and Spring Security 7 requiring fully
explicit configuration where 6.x had usable secure-by-default behavior. Those are exactly the kind of
change that can silently alter runtime behavior without a compile error, which made 4.1 the wrong target
for code being written without the ability to run `mvn compile` while writing it. Everything here is
pinned to 3.3.5, a version with a long, well-documented track record.

**Upgrading to Spring Boot 4.1 is a genuinely good first task for Claude Code** — it's a real, current
migration (see Spring's own migration guide), it's mechanical enough to verify with the actual build/test
loop, and "migrated a Spring Boot 3 app to 4" is a legitimate, current thing to be able to talk about in
an interview.

## Roadmap

Roughly in the order they'd add the most value:

1. **Spring Boot 3.3 → 4.1 migration** (see above) — good first Claude Code task, low risk, high signal.
2. **Real CSV upload** via Firebase Storage, writing to the already-correct `file_metadata` table.
3. **Billing/subscriptions** — not designed yet at all; would need its own schema.
4. **Separate the worker into its own deployable** — `AirdropJobProcessor` is already isolated enough
   that this is mostly a packaging exercise, not a rewrite (see [Adaptations](#adaptations-from-the-original-diagrams)).
5. **Pick a real distribution model** if this ever needs to move real value: a chain, a custody model
   (custodial vs. non-custodial vs. delegated to a provider like Fireblocks/thirdweb/Crossmint), and — if
   custodial — real legal and security review before anything touches real funds.
6. **Observability** — structured logging is in place (SLF4J throughout); metrics/tracing (Micrometer +
   Prometheus, or an APM) isn't.
7. **Admin frontend UI** — the API is done; a dedicated Next.js admin portal is a clean, self-contained
   follow-up build.

## Continuing this in Claude Code

This project was scaffolded in a sandboxed environment that can write and structurally verify code but
cannot reach Maven Central — so every Java file here is written carefully but has never actually been
compiled. **The first thing to do in Claude Code is `cd backend && mvn clean install`** and fix whatever
that surfaces (there may be nothing; the code was written and cross-checked carefully, but this is the
one part of the stack that was never proven to build). The frontend, by contrast, *has* been built and
verified (`npm run build` passes cleanly) in the environment that produced it.

A reasonable prompt to hand Claude Code to pick this up:

> This is AirdropX, a Spring Boot + Next.js + Postgres portfolio project. Read the README fully first.
> Run `cd backend && mvn clean install` and fix any compile errors — this codebase has been carefully
> written but never actually compiled. Then run `docker compose up --build` and verify the full flow
> in the README's curl walkthrough works end to end. After that, let's work through the Roadmap section
> in order, starting with the Spring Boot 4.1 migration.
