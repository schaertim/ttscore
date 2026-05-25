# Implementation Plan â€” Follow & Notifications

## Overview

This phase adds user accounts, personalisation, follows, and push notifications to ttscore. It is the first phase that requires persistent user identity.

All development happens locally (Docker PostgreSQL). Railway deployment is the final step after everything is working end-to-end.

---

## Auth approach

Better Auth runs in the SvelteKit layer (Vercel). Users are stored in Railway PostgreSQL (same DB as app data).

**Sign-in options:**
- Google OAuth
- Email + password (no email verification initially â€” add Resend-based verification later as a follow-up)
- Apple OAuth â€” deferred until an Apple Developer account is available ($99/year)
- Other providers (GitHub etc.) â€” easy to add later, Better Auth supports them all with minimal config

**Ktor integration:** SvelteKit signs a short-lived token (HMAC-SHA256, shared secret) after verifying the Better Auth session. Ktor verifies this token on protected routes. No direct DB access from Ktor for auth.

---

## Steps

### Step 1 â€” Better Auth setup

**What:** Google, Apple, and email+password sign-in working end-to-end in SvelteKit on localhost.

**How:**
- Install `better-auth` in the SvelteKit project
- Configure OAuth apps:
  - Google: Google Cloud Console â†’ OAuth 2.0 credentials
  - Apple: deferred â€” no Apple Developer account yet
  - Additional providers can be added later with minimal config
- Add Better Auth config (`src/lib/auth.ts`) â€” define providers, point at local PostgreSQL
- Run Better Auth's schema migration to create `users`, `sessions`, `accounts` tables
- Add the catch-all server route (`src/routes/api/auth/[...all]/+server.ts`)
- Add `hooks.server.ts` to attach session to `event.locals` on every request
- Build sign-in UI: modal or dedicated `/signin` page with Google button and email+password form
- Build sign-out â€” single button, clears session cookie

**Done when:** You can sign in with Google, sign in with email+password, and sign out. Session persists across page refreshes.

---

### Step 2 â€” Ktor token verification

**What:** Ktor can identify which authenticated user is making a request from SvelteKit.

**How:**
- Generate a shared secret, store in env vars on both SvelteKit (Vercel) and Ktor (Railway)
- In SvelteKit server-side API calls to Ktor, attach a signed header: `X-Auth-User: <user_id>` signed with HMAC-SHA256
- In Ktor, add middleware that verifies the HMAC signature and extracts `user_id` for protected routes
- Unauthenticated routes (all existing endpoints) are unchanged

**Done when:** Ktor can trust the `user_id` in requests from SvelteKit. Protected routes reject requests with missing or invalid tokens.

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

### Step 5 â€” Push notifications

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

**Sending notifications:**
- Ktor background job: after each match result is scraped and saved, query `follows` to find users who follow any participant (player, team, or division)
- Fire notifications immediately â€” no batching needed since match results are always entered fully at once
- For each affected user, look up their `push_subscriptions` and send a Web Push notification
- Notification payload: e.g. "Concordia Basel 6:4 Carouge 1 â€” followed match result"
- Handle expired/invalid subscriptions gracefully (remove from DB on 410 Gone)

**Notification preferences:**
- Per-entity mute (soft delete the specific follow's notification flag)
- "Pause all" toggle on the user account

**Done when:** Following a team and having notifications enabled results in a push notification on your phone when their match result is scraped.

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
- Run Flyway migrations + Better Auth schema migrations against Railway PostgreSQL
- Deploy Ktor backend to Railway, wire env vars (DB connection, shared auth secret, VAPID private key)
- Update SvelteKit env vars on Vercel (API base URL, Better Auth config pointing at Railway DB, VAPID public key)
- Configure OAuth redirect URIs in Google/Apple consoles to point at production domain
- Smoke test all flows on production: sign in, set home player, follow, enable notifications, trigger a notification

**Done when:** Everything works on the production domain. Push notifications fire on real match results.

---

## DB schema additions (summary)

```sql
-- Better Auth managed
users (id, email, name, created_at, ...)
sessions (id, user_id, expires_at, ...)
accounts (id, user_id, provider, provider_account_id, ...)

-- App managed
ALTER TABLE users ADD COLUMN home_player_id INT REFERENCES players(id);

CREATE TABLE follows (
  id SERIAL PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id),
  entity_type TEXT NOT NULL CHECK (entity_type IN ('player', 'team', 'division')),
  entity_id INT NOT NULL,
  notify BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (user_id, entity_type, entity_id)
);

CREATE TABLE push_subscriptions (
  id SERIAL PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id),
  endpoint TEXT NOT NULL UNIQUE,
  p256dh TEXT NOT NULL,
  auth TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## Deferred / follow-up items

- **Apple Sign In** â€” add once an Apple Developer account is available
- **Email verification** â€” add via Resend once the basic flow is stable
- **Additional OAuth providers** â€” GitHub etc. trivial to add via Better Auth when needed
