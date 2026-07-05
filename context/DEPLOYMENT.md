# Deployment & CI/CD

> **Status:** Plan agreed, not yet executed. Pre-launch (no live users). Goal is a
> **simple but powerful** setup: two environments (staging + production), automatic
> deploys from Git, automatic DB migrations, one-click rollback — on as few providers
> as possible.

## Goals

- **Two isolated environments** — `staging` (safe to break) and `production` (real users).
- **Consolidated providers** — the original plan spread across Vercel + Railway + Supabase
  (plus a redundant second Postgres). This collapses to **Supabase + Railway** (+ Stripe,
  which every payment setup needs). See [Why this topology](#why-this-topology).
- **Push-to-deploy CI/CD** — merge a branch, it builds, migrates, and goes live. No manual
  deploy steps, no snowflake servers.
- **Cheap** — target hosting ≈ CHF 20–40/mo (matches the break-even in `MONETIZATION.md`).

---

## Why this topology

The original stack (`PROJECT_DESCRIPTION.md`) used **Vercel** for the frontend, **Railway**
for the backend, and **Railway Postgres** for the database — while **Supabase** *also* runs
its own Postgres internally for Auth. That is three hosting providers and two databases.

Consolidation decisions:

1. **Drop Railway Postgres; use Supabase's Postgres for the app schema too.** Supabase Postgres
   is a normal Postgres — Flyway migrates the `public` schema, Supabase owns the `auth` schema,
   they don't collide. One database instead of two.
2. **Drop Vercel; run the SvelteKit frontend on Railway** as a Node service (`adapter-node`).
   Railway serves long-running Node processes fine; SSR + the service worker work identically.
3. **Result: two providers.** Supabase (Postgres + Auth) and Railway (both app services).
   Stripe stays, but it isn't infra sprawl — it's the payment rail and is env-isolated by
   Stripe's own test/live mode.

Trade-off accepted: we lose Vercel's edge CDN for the frontend. For a Swiss-only audience
hitting a single region this is negligible, and Railway can sit behind Cloudflare later if
edge caching ever matters.

---

## Target topology

```
                          ┌─────────────────────────── Railway project: ttscore ──────────────────────────┐
                          │                                                                                 │
  ttscore.ch ───────────▶│  [production env]   frontend (SvelteKit/adapter-node)  ◀── main branch          │
  api.ttscore.ch ───────▶│                     backend  (Ktor fat-jar / Docker)                             │
                          │                                                                                 │
  *.up.railway.app ──────▶│  [staging env]      frontend                          ◀── staging branch        │
                          │                     backend                                                      │
                          └─────────────────────────────────────────────────────────────────────────────────┘
                                     │ JDBC                                    │ JDBC
                                     ▼                                         ▼
                        Supabase project: ttscore-prod            Supabase project: ttscore-staging
                        (Postgres + Auth + OAuth)                  (Postgres + Auth + OAuth)

  Stripe (live mode)  ──webhook──▶ api.ttscore.ch/api/v1/stripe/webhook
  Stripe (test mode)  ──webhook──▶ <staging-backend>.up.railway.app/api/v1/stripe/webhook
```

---

## Environments

| | **staging** | **production** |
|---|---|---|
| Git branch | `staging` | `main` |
| Railway env | `staging` | `production` |
| Frontend URL | `*.up.railway.app` (auto) | `https://ttscore.ch` |
| Backend URL | `*.up.railway.app` (auto) | `https://api.ttscore.ch` |
| Supabase project | `ttscore-staging` | `ttscore-prod` |
| Stripe mode | Test keys + test webhook | Live keys + live webhook |
| Background jobs | `JOBS_ENABLED=false` (see [Jobs](#background-jobs--backfill)) | `JOBS_ENABLED=true` |
| Data | Disposable, seedable | Real |

---

## Git flow & CI/CD

**Branch-per-environment**, which maps 1:1 onto Railway's branch-watch:

```
feature/* ──PR──▶ staging ──(auto-deploy → staging env)──▶  merge ──▶ main ──(auto-deploy → prod env)
```

1. Work on `feature/*` branches.
2. Open a PR into `staging`. **CI (GitHub Actions)** runs build + type checks (below).
3. Merge → Railway **auto-builds and deploys the staging environment**. Flyway migrates
   staging's DB on boot. Test it live.
4. When staging is good, merge `staging` → `main`. Railway auto-deploys **production**.
   Flyway migrates prod on boot.

**Rollback:** Railway keeps every deploy; one click redeploys a previous build. (Note: this
rolls back *code*, not the DB — see [DB safety](#database--migrations).)

### CI — GitHub Actions (gate, does not deploy)

`.github/workflows/ci.yml`, runs on PRs and pushes to `staging`/`main`:

- **backend** job: `./gradlew build` (compile + tests). JDK 21.
- **frontend** job: `pnpm install --frozen-lockfile`, `pnpm run check` (svelte-check),
  `pnpm run build`. Node 22, pnpm 10 (the project uses pnpm — `pnpm-lock.yaml`).

> **Lint caveat:** per the repo's known-red lint baseline, **do not hard-fail CI on
> prettier/ktlint** yet — they already fail on pre-existing files. Run lint as a
> non-blocking (`continue-on-error`) step, or scope it to changed files, until the
> baseline is cleaned up. Keep the *build* and *type-check* steps blocking.

### CD — Railway branch-watch (no deploy tokens needed)

Railway's native GitHub integration watches each branch and deploys the matching environment.
No `RAILWAY_TOKEN` in GitHub, no deploy job in Actions — keeps the secret surface minimal.
CI gates the merge; the merge triggers the deploy.

---

## Build & runtime

### Backend (Ktor) — Dockerfile, multi-stage

`EngineMain` reads `application.conf`; every value is already env-overridable. Ship a
`backend/Dockerfile`:

- **Stage 1:** `gradle:8-jdk21` → `./gradlew buildFatJar` (Ktor plugin task) → `*-all.jar`.
- **Stage 2:** `eclipse-temurin:21-jre` → copy jar → `java -jar app.jar`. Bind `$PORT`.

Railway sets `PORT`; `application.conf` already reads `${?PORT}`.

> The Ktor Gradle plugin already provides `buildFatJar`. If we prefer not to Docker the
> backend, Railway's Nixpacks can build Gradle too — but a Dockerfile is reproducible and
> portable, so it's the default here.

### Frontend (SvelteKit) — `adapter-node`

- Swap `@sveltejs/adapter-auto` → `@sveltejs/adapter-node` in `svelte.config.js`.
- Railway (Nixpacks auto-detect from `pnpm-lock.yaml`): `pnpm install && pnpm run build`,
  start `node build`.
- `PUBLIC_*` env vars are **baked at build time**. Because each Railway environment builds
  separately with its own vars, staging bakes the staging API/Supabase URLs and prod bakes
  prod's — correct by construction. (Consequence: changing a `PUBLIC_` var requires a
  rebuild, not just a restart.)

---

## Environment variables

Set per Railway environment (never commit real values). `PORT` is injected by Railway.

### Backend service

| Var | staging | production |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://…staging pooler…` | `jdbc:postgresql://…prod pooler…` |
| `DATABASE_USER` / `DATABASE_PASSWORD` | staging DB creds | prod DB creds |
| `SUPABASE_URL` | staging project URL | prod project URL |
| `SUPABASE_JWT_KEY_X` / `SUPABASE_JWT_KEY_Y` | staging JWT pubkey coords | prod JWT pubkey coords |
| `CORS_ALLOWED_ORIGINS` | staging frontend origin | `https://ttscore.ch` |
| `FRONTEND_URL` | staging frontend URL | `https://ttscore.ch` |
| `STRIPE_SECRET_KEY` | `sk_test_…` | `sk_live_…` |
| `STRIPE_WEBHOOK_SECRET` | test `whsec_…` | live `whsec_…` |
| `STRIPE_PRICE_MONTHLY` / `STRIPE_PRICE_YEARLY` | test price IDs | live price IDs |
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` / `VAPID_SUBJECT` | staging pair | **prod pair (generate fresh)** |
| `JOBS_ENABLED` | `false` | `true` |
| `SCRAPER_CURRENT_SEASON` | `2026/2027` | `2026/2027` |

> **VAPID:** the keys in `application.conf` are dev defaults. Generate a **separate prod
> pair** and keep the private key out of Git. Changing the public key later invalidates all
> existing push subscriptions, so set prod's before real users subscribe.

### Frontend service

| Var | staging | production |
|---|---|---|
| `PUBLIC_API_URL` | staging backend URL | `https://api.ttscore.ch` |
| `PUBLIC_SUPABASE_URL` | staging project URL | prod project URL |
| `PUBLIC_SUPABASE_PUBLISHABLE_KEY` | staging publishable key | prod publishable key |

---

## Database & migrations

- **One Postgres per environment**, provided by the matching Supabase project.
- **Flyway runs on backend boot** (`Database.kt`) — deploys are self-migrating. No manual
  migration step in CI/CD.
- **Connection string / IPv4 gotcha:** Supabase's *direct* connection (`db.<ref>…:5432`) is
  IPv6-only on newer projects. Use the **Supavisor session-mode pooler** endpoint (IPv4,
  port `5432` pooler host) for `DATABASE_URL` so Railway can reach it reliably.
- **Connection pooling (hardening):** `Database.connect(url=…)` opens connections without an
  explicit pool. Before real traffic, wire **HikariCP** as the DataSource (bounded pool,
  keeps us within Supabase connection limits). Tracked in the [hardening checklist](#pre-launch-hardening-checklist).

**DB safety / rollback:** code rollback via Railway does **not** revert a migration. Keep
migrations additive/backward-compatible (expand-then-contract) so an old build can still run
against a newer schema. Take a Supabase backup (or point-in-time snapshot) before deploying a
destructive migration to prod.

---

## Auth (Supabase, per environment)

Each Supabase project is configured independently:

- **Site URL + Redirect allow-list:** staging → staging frontend URL; prod → `https://ttscore.ch`.
- **Google OAuth:** one Google Cloud OAuth client can serve both, but each Supabase project's
  callback (`https://<ref>.supabase.co/auth/v1/callback`) must be added to the client's
  Authorized redirect URIs. (Cleaner: one OAuth client per environment.)
- **JWT keys differ per project** → that's why `SUPABASE_JWT_KEY_X/Y` are per-environment.

---

## Stripe (test vs live)

- Staging uses **test-mode** keys + a **test webhook endpoint** pointed at the staging backend.
- Production uses **live-mode** keys + a **live webhook endpoint** pointed at
  `https://api.ttscore.ch/api/v1/stripe/webhook`.
- Each webhook has its **own signing secret** → `STRIPE_WEBHOOK_SECRET` differs per env.
- Billing routes stay `503` until keys are set (existing behaviour in `application.conf`), so
  a half-configured env fails safe.
- Recreate the two Price IDs (CHF 3/mo, CHF 25/yr) in **live** mode; the IDs in
  `application.conf` are test-mode and must be overridden in prod.

---

## Domain & DNS

Registered domain → Railway custom domains (Railway auto-issues TLS):

| Host | Points to |
|---|---|
| `ttscore.ch` (+ `www`) | Railway **production frontend** service |
| `api.ttscore.ch` | Railway **production backend** service |

Add each custom domain in Railway → it shows the exact CNAME/A record to create at the
registrar. Staging keeps the free `*.up.railway.app` URLs — no DNS, no cert management.

---

## Background jobs & backfill

The backend runs in-process schedulers **and** a one-time historical backfill on first boot
(`Application.kt`): the knob 1989→2024/25 scrape is expensive and guarded by `BackfillLedger`
(runs once per database).

- **Production:** `JOBS_ENABLED=true`. First prod boot runs the full backfill (intended) — it
  seeds 35 years of history, then nightly/5-min jobs keep it fresh.
- **Staging:** default `JOBS_ENABLED=false` so staging doesn't burn hours re-scraping the full
  history on every fresh DB, and so scrapers don't hammer knob/click-tt from two environments.
  When you need real data in staging, either flip it on deliberately for one run, or restore a
  snapshot of prod's DB.
- **Single instance only:** the schedulers are in-process. Keep the backend at **1 replica per
  environment** — scaling to 2 would double-run the 5-min poll and nightly sync. Revisit with a
  leader-lock before horizontal scaling.

---

## Observability

- **Logs:** Railway captures stdout/stderr per service (Logback already configured).
- **Health check:** add `GET /health` (returns 200) so Railway's healthcheck can gate a deploy
  as healthy before routing traffic. Tracked in [code changes](#code-changes-required-before-first-deploy).
- Later: Sentry (frontend + backend) and Railway metrics/alerts. Not needed for first deploy.

---

## Cost estimate (rough, CHF/mo)

| Item | Cost |
|---|---|
| Railway (2 envs × 2 small services + egress) | ~20–30 |
| Supabase (2 projects; Free tier may cover staging, Pro ~25 for prod) | 0–25 |
| Domain | ~1–2 (amortised) |
| Stripe | % per transaction, no fixed fee |
| **Total** | **~20–45** |

Aligns with the `MONETIZATION.md` break-even (~10–20 Pro users/yr).

---

## Code changes required before first deploy

These are prerequisites, done in the repo (not on any dashboard):

1. **CORS from env** — `plugins/CORS.kt` hardcodes `localhost:5173`. Read a comma-separated
   `CORS_ALLOWED_ORIGINS` (add to `application.conf` with a localhost default) so each env
   allows its own frontend origin. Keep `allowCredentials = true`.
2. **`adapter-node`** — swap `adapter-auto` in `frontend/svelte.config.js`; add
   `@sveltejs/adapter-node`.
3. **`backend/Dockerfile`** — multi-stage `buildFatJar` → JRE (above).
4. **`GET /health`** — trivial 200 route in `Routing.kt` for Railway healthchecks.
5. **`.github/workflows/ci.yml`** — build + type-check gate (lint non-blocking per baseline).
6. **`.dockerignore` / frontend build config** — exclude `build/`, `node_modules`, etc.

---

## Rollout runbook (guided, in order)

**Phase 0 — repo prep (code, no accounts needed)**
- [ ] CORS env-driven, `adapter-node`, backend `Dockerfile`, `/health`, CI workflow.
- [ ] Create the `staging` branch off `main`.

**Phase 1 — Supabase (2 projects)**
- [ ] Create `ttscore-staging` and `ttscore-prod`.
- [ ] For each: grab the pooler `DATABASE_URL`, JWT key X/Y, project URL, publishable key.
- [ ] Configure Auth Site URL + redirect allow-list + Google OAuth per project.

**Phase 2 — Railway (1 project, 2 envs)**
- [ ] Create project, connect the GitHub repo.
- [ ] `production` env watches `main`; `staging` env watches `staging`.
- [ ] Add `backend` + `frontend` services per env; set env vars from the tables above.

**Phase 3 — First staging deploy**
- [ ] Push `staging` → watch build → Flyway migrates → hit `/health` → smoke-test the app.

**Phase 4 — Stripe**
- [ ] Test webhook → staging backend; live products + live webhook → (pending prod).

**Phase 5 — Production deploy + domain**
- [ ] Merge `staging` → `main` → prod builds.
- [ ] Add `ttscore.ch` + `api.ttscore.ch` custom domains; create DNS records.
- [ ] Live Stripe webhook → `api.ttscore.ch`.

**Phase 6 — Pre-launch hardening** (below).

---

## Pre-launch hardening checklist

- [ ] HikariCP connection pool (bounded) instead of raw `Database.connect`.
- [ ] Fresh **prod** VAPID keypair (not the repo defaults).
- [ ] Secrets rotated out of `application.conf` defaults for prod (Supabase JWT coords are
      public keys so fine to commit; Stripe/VAPID private material must be env-only).
- [ ] Supabase automated backups / PITR confirmed on prod.
- [ ] Sentry (or equivalent) error reporting.
- [ ] Rate-limiting / basic abuse protection on public API.
- [ ] Confirm scraper etiquette (single env scraping prod source).

---

## Open decisions (defaults chosen — override if you disagree)

- **Branch names:** `staging` + `main`. (Alt: `develop`/`main`, or tag-based prod releases.)
- **Staging jobs off by default** — flip per-run when you need fresh staging data.
- **Backend via Dockerfile** (vs Nixpacks) for reproducibility.
- **Supabase Free for staging, Pro for prod** — upgrade staging only if you hit limits.
