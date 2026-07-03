# Implementation Plan â€” Follow & Notifications

## Overview

This phase adds user accounts, personalisation, follows, and push notifications to ttscore. It is the first phase that requires persistent user identity.

All development happens locally (Docker PostgreSQL). Railway deployment is the final step after everything is working end-to-end.

---

> **Status:** Steps 1–6 are implemented. The original plan called for Better Auth; the
> project shipped on **Supabase Auth** instead, so the auth details below have been
> corrected. Step 7 (Railway deployment) is the remaining work. Notification preferences
> (per-entity mute / pause-all) are still deferred — see the end of this doc.

## Auth approach

**Implemented with Supabase Auth** (the "Better Auth" references in earlier drafts are obsolete). Supabase hosts the auth users and issues JWTs. The SvelteKit layer holds the session and forwards the Supabase access token to Ktor as a `Bearer` token.

**Sign-in options:**
- Google OAuth
- Email + password (no email verification initially — add verification later as a follow-up)
- Apple OAuth — deferred until an Apple Developer account is available ($99/year)
- Other providers (GitHub etc.) — easy to add later via Supabase Auth providers

**Ktor integration:** Ktor verifies the Supabase JWT directly on protected routes (the `auth-jwt` realm) — ES256 / EC P-256, issuer `<supabase-url>/auth/v1`, with the `sub` claim used as the user id. No shared HMAC secret and no direct DB access from Ktor for auth. See `plugins/Authentication.kt`.

---

## Steps

### Step 1 — Supabase Auth setup ✅ done

**What:** Google and email+password sign-in working end-to-end in SvelteKit.

**How (as built):**
- Supabase project provides auth + JWT signing
- Configure OAuth apps:
  - Google: Google Cloud Console → OAuth 2.0 credentials, wired into Supabase
  - Apple: deferred — no Apple Developer account yet
  - Additional providers can be added later via the Supabase dashboard
- Supabase client + session helpers in SvelteKit (`locals.safeGetSession()` / `supabase:auth` invalidation)
- Sign-in page at `/signin` (with `redirectTo`) and sign-out via `supabase.auth.signOut()`
- Build sign-in UI: modal or dedicated `/signin` page with Google button and email+password form
- Build sign-out â€” single button, clears session cookie

**Done when:** You can sign in with Google, sign in with email+password, and sign out. Session persists across page refreshes.

---

### Step 2 — Ktor JWT verification ✅ done

**What:** Ktor can identify which authenticated user is making a request.

**How (as built):**
- SvelteKit forwards the Supabase access token to Ktor as `Authorization: Bearer <jwt>` (see `lib/server/ktor.ts`)
- Ktor's `auth-jwt` realm verifies the token: ES256 / EC P-256, issuer `<supabase-url>/auth/v1`, `sub` claim required (`plugins/Authentication.kt`). The public key coordinates come from Supabase → Project Settings → API → JWT Keys.
- `call.userId()` extracts `sub` for protected routes; unauthenticated routes (all existing endpoints) are unchanged

**Done when:** Ktor trusts the `user_id` (`sub`) in the JWT. Protected routes reject requests with missing or invalid tokens.

---

### Step 3 â€” Home player

**What:** Users can designate any player as "their player", stored on their account.

**How:**
- Add `home_player_id` column to the `users` table (nullable, FK to `players`)
- Ktor endpoint: `PUT /users/me/home-player` (authenticated)
- "Set as my player" button on every player profile page
  - If not signed in: clicking opens the sign-in modal, then completes the action after auth
  - If signed in: fires the API call immediately
- After setting, the home route (`/`) redirects to the home player's profile
- "Remove home player" option in account settings

**Done when:** A signed-in user can set a home player from any profile page and `/` takes them there.

---

### Step 4 â€” Follow system

**What:** Users can follow players, teams, and divisions to receive notifications when results land.

**How:**
- DB table: `follows (id, user_id, entity_type ENUM('player','team','division'), entity_id, created_at)`
- Ktor endpoints:
  - `POST /follows` â€” body: `{ entityType, entityId }`
  - `DELETE /follows/{id}`
  - `GET /follows` â€” returns the current user's follows
- Follow/unfollow toggle buttons on player, team, and division pages
  - Signed-out users see the button with a "sign in to follow" nudge on click
- Follows listed and manageable from the personal dashboard

**Done when:** A signed-in user can follow and unfollow any player, team, or division. The list persists across devices.

---

### Step 5 — Push notifications ✅ core done (preferences deferred)

**What:** Users receive a push notification when a result lands for any entity they follow.

**How:**

**Infrastructure:**
- Generate VAPID key pair, store public key in SvelteKit env, private key in Ktor env
- Update the service worker to handle `push` events â€” show a notification with title + body

**Subscription registration:**
- SvelteKit page: "Enable notifications" button
  - Requests browser notification permission
  - Registers a push subscription via the Push API
  - POSTs subscription object (`endpoint`, `p256dh`, `auth`) to Ktor
- DB table: `push_subscriptions (id, user_id, endpoint, p256dh, auth, created_at)`
- PWA install prompt shown alongside the notification enable prompt (required on iOS Safari â€” notifications only work from installed PWA on iOS)

**Sending notifications (as built — `jobs/MatchPollJob.kt`):**
- `MatchPollJob` runs every 5 minutes. It scrapes groups with past-due scheduled matches; for each newly completed match it fires notifications in a single pass (`sendMatchPushNotifications`).
- One match-completion pass notifies followers of: the **home team**, the **away team**, the **division group**, and every **player who took part** in the match (participants resolved from the match's games). There is **no separate profile/ELO-update trigger** — player notifications are driven purely by match participation.
- `PushService.sendToFollowers(targetType, targetId, …)` resolves followers → their `push_subscription` rows → sends a Web Push notification each. The payload is JSON `{ title, body, url }`; e.g. team/group: `"<Home> vs <Away>" / "Result: 6:4"` → `/matches/{id}`; player: `"<Player name>" / "New match result available"` → `/players/{id}`.
- Fired immediately — no batching, since results are entered fully at once.

**Notification preferences:**
- ✅ **Per-entity mute is built.** The `follow` and `favorite` tables were merged into a single `follow` table with a `notify` flag (default **off**) — see the DB schema section. Following an entity (⭐) is separate from being notified about it (🔔): you can follow silently, or toggle the bell without unfollowing. `MatchPollJob`/`PushService.sendToFollowers` only push to followers with `notify = true`.
- ✅ **"Pause all" is built.** `user_profile.notifications_paused` (default off, toggled from the account page via `PUT /users/me/notifications-paused`) is a global mute: `PushService.sendToFollowers` skips paused users entirely, leaving their follows and per-entity bells untouched.
- ✅ **Dead-subscription cleanup is built.** `sendToFollowers` inspects the push response status; on `404`/`410 Gone` it prunes the `push_subscription` row by endpoint, so dead endpoints don't accumulate. Transient errors (timeouts, 5xx) are still just logged and left in place.

**Done when:** Following a team and having notifications enabled results in a push notification on your phone when their match result is scraped. ✅

---

### Step 6 â€” Personal dashboard

**What:** Signed-in users get a personalised home experience.

**How:**
- `/` route logic:
  - Not signed in â†’ league browser (current behaviour)
  - Signed in, no home player set â†’ league browser + "Set your player" prompt banner
  - Signed in, home player set â†’ personal dashboard
- Dashboard content:
  - ELO badge + current classification + club
  - Next fixture (date, opponent, division)
  - Recent results (last 5 games with result and ELO delta)
  - Followed entities section â€” recent results for each follow
- "My account" page:
  - Manage home player
  - Manage follows (list with unfollow buttons)
  - Notification settings (pause all, per-entity mute)
  - Sign out

**Done when:** A signed-in user with a home player set lands on a useful, personalised dashboard. Follows and notification settings are manageable from one place.

---

### Step 7 â€” Railway deployment

**What:** Move everything to production. This is the final step.

**How:**
- Provision PostgreSQL service on Railway
- Run Flyway migrations against the production PostgreSQL (auth users live in Supabase, no schema migration needed there)
- Deploy Ktor backend to Railway, wire env vars (DB connection, `SUPABASE_URL` + `SUPABASE_JWT_KEY_X/Y` for JWT verification, `VAPID_PRIVATE_KEY`)
- Update SvelteKit env vars on Vercel (API base URL, Supabase URL + anon key, `PUBLIC_API_URL`; VAPID public key is served by Ktor at `/push/vapid-public-key`)
- Configure OAuth redirect URIs in Google (and Supabase) to point at production domain
- Smoke test all flows on production: sign in, set home player, follow, enable notifications, trigger a notification

**Done when:** Everything works on the production domain. Push notifications fire on real match results.

---

## DB schema additions (summary)

Auth users live in **Supabase** (not app-managed tables). `user_id` throughout is the
Supabase user UUID, stored as `TEXT`. All app IDs are `UUID`, not serial ints.

**Follow/favorite history:** V7 split the original `follow` table into `follow` (🔔) and
`favorite` (⭐). V12 **merged them back** into a single `follow` table with a `notify`
flag: a follow (⭐) drives the feed + search, and `notify` (🔔, default off) is the push
subscription. This enforces the invariant *notify ⊆ follow* — you can only be notified
about something you follow — and makes "mute without unfollowing" a single flag flip.

```sql
-- App-managed profile (home player, global mute), keyed by Supabase user id
user_profile (user_id TEXT PRIMARY KEY, home_player_id UUID REFERENCES players(id),
              notifications_paused BOOLEAN NOT NULL DEFAULT false, ...)

-- follow_target_type ENUM: 'player' | 'team' | 'division_group'

CREATE TABLE follow (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     TEXT NOT NULL,
  target_type follow_target_type NOT NULL,
  target_id   UUID NOT NULL,
  notify      BOOLEAN NOT NULL DEFAULT false,   -- 🔔 the bell; ⭐ = row exists
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, target_type, target_id)
);

CREATE TABLE push_subscription (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id    TEXT NOT NULL,
  endpoint   TEXT NOT NULL UNIQUE,
  p256dh     TEXT NOT NULL,
  auth       TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

## Deferred / follow-up items

- **Apple Sign In** â€” add once an Apple Developer account is available
- **Email verification** â€” add via Resend once the basic flow is stable
- **Additional OAuth providers** — GitHub etc. trivial to add via Supabase Auth when needed
