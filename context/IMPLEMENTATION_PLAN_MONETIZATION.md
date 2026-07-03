# Implementation Plan — Freemium (Pro) Monetization

> **Status:** Proposed — awaiting approval. Implements the split defined in
> [`MONETIZATION.md`](./MONETIZATION.md). Gentle freemium; app is pre-launch (no live
> users), so no feature is being clawed back.

## What this adds

A single **Pro** entitlement (paid, yearly) that unlocks:

- **H2H** — the full "Compare with me" drawer + all head-to-head records
- **Career tab** — the career-arc stats tab (season-by-season, milestones, rivalries)
- **Notifications beyond your own player** — bell on followed teams/divisions/other players
- **Unlimited follows** — beyond the free cap of own player + 3

Everything else stays free (all browsing, set scores, Overview + Stats tabs, ELO
history back to 1989, search, favorites, dashboard).

## What already exists (leaned on, not rebuilt)

- Follows carry a per-follow **`notify` (bell)** flag distinct from the **star**;
  `PushService.sendToFollowers` already filters on `notify = true`, skips globally
  paused users, and prunes dead 404/410 subscriptions.
- `user_profile` already has `home_player_id` + `notifications_paused`.
- `call.userId()` extracts the Supabase `sub`; JWT auth realm `auth-jwt` is in place.
- Player profile already has **Overview / Stats / Career** tabs; Career is a
  "coming soon" placeholder. H2H drawer driven by `frontend/src/lib/h2h.svelte.ts`.

---

## Phase 1 — Entitlement foundation

Make the app *aware* of Pro. No gates enforced yet; grant Pro by hand for testing.

- **Migration** `V14__add_pro_until.sql`: add `pro_until TIMESTAMPTZ NULL` to
  `user_profile`. `NULL` or past = not Pro; future = Pro.
- **Backend:**
  - `UserProfileService`: read/write `pro_until`; `isPro(userId): Boolean`
    (`pro_until != null && pro_until > now()`).
  - `util/Authorization.kt`: `suspend fun ApplicationCall.isPro(): Boolean` helper.
  - Add `isPro: Boolean` (and optionally `proUntil`) to `UserProfileResponse` so the
    frontend can render gates.
- **Frontend:** surface `isPro` in the session/layout data (alongside `user`,
  `hasHomePlayer`) so any page/component can check it.
- **Test hook:** grant Pro via a SQL update (`UPDATE user_profile SET pro_until = ...`).

**Done when:** a hand-set `pro_until` flips `isPro` everywhere, frontend included.

---

## Phase 2 — Enforce the gates

The core freemium mechanics. Enforce server-side (source of truth) + client-side
(UX/upsell).

### 2a. Follow cap (own player + 3)
- **Backend** `POST /follows`: if not Pro and the user already has **4** follows,
  return `403` with a `{ reason: "follow_limit" }` body. *(Default: 4 total incl. home
  player — see Decision 1.)*
- **Frontend** `FollowButton.svelte`: on `403 follow_limit`, show the upsell
  (modal/toast → `/pro`) instead of a generic error.

### 2b. Notification gating (own player only, unless Pro)
- **Write-time** — `PATCH /follows/{id}` (`setNotify`): allow `notify = true` for a
  non-Pro user **only** when the follow's target is a player and
  `targetId == user_profile.home_player_id`. Otherwise `403 { reason: "notify_pro" }`.
- **Send-time (authoritative)** — in `PushService.sendToFollowers`, after resolving
  follower `userId`s, drop non-Pro users unless the target is their own home player.
  This makes lapsed-Pro degrade correctly without mutating rows. *(Requires joining
  the candidate userIds to `user_profile` for `pro_until` + `home_player_id`.)*
- **Frontend** `NotifyButton.svelte`: for non-Pro on a non-home-player entity, render
  the bell in a "Pro" state that opens the upsell rather than toggling.

### 2c. H2H paywall
- **Backend** `GET /players/{id}/h2h/{opponentId}`: require Pro → `403` for free users
  (defense in depth).
- **Frontend**: the "Compare with me" button + H2H drawer (`h2h.svelte.ts`) show a
  **teaser** for free users — blurred preview + "Unlock with Pro" CTA — instead of the
  data.

### 2d. Career tab paywall
- **Frontend** `players/[id]/+page.svelte`: the `career` tab renders a teaser +
  "Unlock with Pro" for free users. (Career content itself is built later / as Pro —
  see Decision 2.) Any future `/career` endpoint requires Pro.

### 2e. Shared paywall UI
- `PaywallTeaser.svelte` (blur + lock + CTA overlay) and an upsell dialog/`/pro`
  route link. Reused by H2H, Career, follow cap, and notify gating.

**Done when:** a free account is capped at 4 follows, can only bell its own player,
and sees teasers on H2H + Career; a Pro account has everything unlocked.

---

## Phase 3 — Payments (Stripe)

- **`/pro` pricing page**: explain Free vs Pro, yearly price, checkout button.
- **Checkout**: Stripe Checkout Session (or Payment Link to start). TWINT + cards.
- **Webhook** (`POST /stripe/webhook`, signature-verified): on
  `checkout.session.completed` / subscription events, set `pro_until` on the user's
  profile. Map Stripe customer ↔ Supabase `user_id` (store `stripe_customer_id` on
  `user_profile`).
- **Account page**: show Pro status + renewal date; link to Stripe billing portal to
  manage/cancel.

**Done when:** a real payment sets `pro_until` and unlocks Pro end-to-end.

---

## Phase 4 — Reverse trial + docs

- **Reverse trial**: new users get `pro_until = signup + N weeks` (or season-aligned)
  so they experience Pro, then drop to free (loss-aversion conversion). No card
  required. Length/timing = Decision 3.
- **Docs**: soften the "no paywall, ever" language in `PROJECT_DESCRIPTION.md` and
  `ROADMAP.md` to reflect the free-core + Pro model honestly. Flip `MONETIZATION.md`
  status to "live."

---

## Decisions to confirm before building

1. **Does the home player count toward the 4-follow cap?** Plan assumes **yes** (cap =
   4 total, simplest). Alternative: home-player follow is exempt (own player + 3
   others = up to 4 *additional*). Small change either way.
2. **Career tab**: it's currently unbuilt. Ship Phase 2 with a "Career — coming soon
   for Pro" teaser and **build the career feature as the first true Pro deliverable**?
   Or build career content first, then gate it?
3. **Reverse trial** length/timing (e.g. 30 days, or "first month of the season").
4. **Price** confirmation (working assumption CHF ~20–29/yr).

## Suggested sequencing

Phase 1 → 2 is the meaningful freemium build and can be tested with hand-granted Pro.
Phase 3 (Stripe) only needs doing when you're ready to actually charge — realistically
after the app is deployed (the notifications plan's Railway step). Phase 4 last.
