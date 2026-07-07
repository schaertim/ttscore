# Analytics Integration — PostHog

> **Status:** Spec agreed, not yet implemented. Goal: understand **which features are
> used, by how many users, how regularly** — feature-level product analytics, not just
> pageview counts.

## Decisions (locked)

| Question | Choice | Rationale |
|---|---|---|
| Tool | **PostHog** | Event-based product analytics; free tier ample at this scale |
| Hosting | **PostHog EU Cloud** | Data stays in EU; zero infra to run |
| Identity | **Identify logged-in users** | See per-user journeys, segment Pro vs free |
| Consent banner | **Deferred (pre-launch)** | revFADP doesn't mandate a banner; Swiss audience; no live users yet. **Pre-launch checklist item before broad/EU-facing launch.** |
| Session replay | **No (events only)** | Lighter client payload + smaller privacy surface; revisit later |
| Autocapture | **Off** | Explicit events only → cleaner data, less captured PII |

---

## Architecture — where it plugs in

Client-side only (`posthog-js`). The frontend already has all the auth state we need,
and feature usage is a browser concern. No backend (`posthog-node`/Kotlin) in this phase.

```
Browser ──▶ posthog-js  ──▶  PostHog EU Cloud (eu.i.posthog.com)
   │
   ├─ init once (browser only) in root layout
   ├─ identify(user.id) on sign-in / reset() on sign-out
   ├─ capture('$pageview') on afterNavigate
   └─ capture('<event>', props) from a typed wrapper (lib/analytics.ts)
```

**One PostHog project per environment** (staging + prod), mirroring the Supabase-per-env
isolation already in place. Keeps staging test noise out of prod dashboards. The project
API key is a `PUBLIC_` var, so each Railway env bakes its own at build time — correct by
construction (same mechanism as `PUBLIC_SUPABASE_URL`).

---

## Environment variables (frontend service)

| Var | staging | production |
|---|---|---|
| `PUBLIC_POSTHOG_KEY` | staging project key (`phc_…`) | prod project key (`phc_…`) |
| `PUBLIC_POSTHOG_HOST` | `https://eu.i.posthog.com` | `https://eu.i.posthog.com` |

> `PUBLIC_*` → baked at build time; changing the key requires a **rebuild**, not a restart
> (same caveat as `PUBLIC_API_URL`). The PostHog "project API key" is safe to expose in the
> browser by design — it's write-only ingestion, not an admin key.

---

## Implementation

### 1. Package
Add `posthog-js` as a **runtime dependency** (`pnpm add posthog-js` in `frontend/`).

### 2. Initialization — browser only
New module `frontend/src/lib/analytics.ts` exporting an `initAnalytics()` plus typed event
helpers. Init is called once from `+layout.svelte` `onMount` (browser-guaranteed), never
during SSR.

Init config (privacy-conscious defaults):
```ts
posthog.init(PUBLIC_POSTHOG_KEY, {
  api_host: PUBLIC_POSTHOG_HOST,
  person_profiles: 'identified_only', // no profile for anonymous visitors → less data, lower cost
  autocapture: false,                 // explicit events only
  capture_pageview: false,            // we fire $pageview manually on afterNavigate (SvelteKit SPA nav)
  capture_pageleave: true,            // needed for accurate session/bounce/time-on-page
  disable_session_recording: true     // no replay this phase
});
```

### 3. User identification
Piggyback on the existing `onAuthStateChange` handler in
[+layout.svelte:44](../frontend/src/routes/+layout.svelte#L44):
- On sign-in (session present): `posthog.identify(user.id, { is_pro: data.isPro, has_home_player: data.hasHomePlayer })`.
  Use the **Supabase user id** as the distinct id.
- On sign-out: `posthog.reset()` so the next anonymous visitor isn't merged into the prior user.

> Keep person properties minimal and non-PII: user id, `is_pro`, `has_home_player`. **Do not
> send email or display name** — not needed for the questions we're answering, and it keeps
> the identified dataset lean.

### 4. Pageview tracking
SvelteKit uses client-side routing, so the browser's `history` API changes the URL without a
full load. Fire pageviews manually:
```ts
import { afterNavigate } from '$app/navigation';
afterNavigate(() => posthog.capture('$pageview'));
```
This gives per-route traffic (home, divisions, players, player profile, match, feed, pro,
account) for free — so we do **not** need redundant `*_viewed` custom events for routes.

### 5. Custom events — the taxonomy
Custom events cover **interactions that a pageview can't see**. Naming: `snake_case`,
`object_action`. Each fired through a typed wrapper so there are no scattered magic strings.

| Event | Fired from | Key properties |
|---|---|---|
| `player_searched` | [players/+page.svelte](../frontend/src/routes/players/+page.svelte) (on submit) | `query_length`, `result_count` |
| `player_followed` / `player_unfollowed` | [FollowButton.svelte](../frontend/src/lib/components/FollowButton.svelte) | `player_id` |
| `notifications_enabled` / `notifications_disabled` | [NotifyButton.svelte](../frontend/src/lib/components/NotifyButton.svelte) | `follow_id` |
| `home_player_set` | [players/[id]/+page.svelte](../frontend/src/routes/players/[id]/+page.svelte) ("is this you") | `player_id` |
| `h2h_opened` | H2HDrawer trigger ([h2h.svelte store](../frontend/src/lib/h2h.svelte.ts)) | `opponent_id`, `is_pro` |
| `pro_checkout_started` | [pro/+page.svelte](../frontend/src/routes/pro/+page.svelte) (before redirect to Stripe) | `plan` (monthly/yearly) |
| `signed_in` / `signed_up` | [login-form.svelte](../frontend/src/lib/components/login-form.svelte) | `method` (google/password) |

> **Completed purchase** is intentionally *not* a client event — it's only trustworthy once
> Stripe confirms via webhook. If we later want a reliable `pro_subscribed` event, add it
> **server-side** from the Stripe webhook handler (Phase 2, needs `posthog-node`-equivalent
> in Ktor). For now `pro_checkout_started` + PostHog's funnel to the pro page is enough.

`signup_prompted`/`pro_prompted` events (fired separately, with `source`) provide the
conversion attribution for these two — see
[Conversion attribution](#conversion-attribution--the-high-value-layer) below. `source` is
**not** repeated on the completion events themselves; PostHog's funnel breakdown correlates
them by person instead.

### 6. Typed wrapper (lib/analytics.ts)
```ts
export const analytics = {
  playerFollowed: (playerId: string) => posthog?.capture('player_followed', { player_id: playerId }),
  proCheckoutStarted: (plan: 'monthly' | 'yearly') => posthog?.capture('pro_checkout_started', { plan }),
  // …one function per event above
};
```
Components import `analytics` and call typed methods — no raw `capture('…')` strings in
components. Guard every call so it's a no-op when PostHog isn't initialised (SSR, or key
unset in local dev).

---

## Conversion attribution — the high-value layer

The most valuable question isn't *how many* users convert, but *which entry point* drove
each conversion. Two hurdles get this treatment — **sign up** and **subscribe to Pro** — since
both are reachable from several places and both gate real value (using the app at all;
paying). Setting a home player is a single, low-friction in-app action, not a hurdle with
competing entry points worth comparing — it stays a plain custom event with no `source`
breakdown (see [taxonomy](#5-custom-events--the-taxonomy) above).

We attach a **`source`** property to each hurdle's funnel so PostHog can show conversion rate
per entry point (e.g. "the follow-button prompt converts at 40%, the navbar at 5%").

**Pattern:** at the moment a CTA is clicked, fire a `*_prompted` event carrying `source`.
Fire a plain completion event (`signed_in`, `pro_checkout_started`) with **no** need to
re-attach `source` in code.

### Why the OAuth/Stripe redirect isn't actually a problem
Two of the three hurdles redirect the browser fully away and back — Google OAuth
(→ Google → `/auth/callback`) and Stripe Checkout. That doesn't lose anything: PostHog's
anonymous visitor id lives in `localStorage` on `ttscore.ch`, not in JS memory, so it
survives the round trip untouched. Once `identify()` runs after sign-in, PostHog retroactively
merges that visitor's pre-login anonymous events onto the identified person.

**Attribution happens in the PostHog UI, not in code:** build a Funnel with Step 1 =
`signup_prompted`, Step 2 = `signed_in`, and turn on "Breakdown by" the `source` property from
Step 1. PostHog computes conversion rate per source automatically, correlating both steps to
the same person. No `register()`/super-properties/localStorage-threading needed — that would
have been reimplementing a feature PostHog already ships. Same pattern for the Pro hurdle.

### Source enums (grounded in existing entry points)

**Hurdle 1 — Sign up** · `signup_prompted { source }` → `signed_in` / `signed_up { source }`

| `source` | Entry point |
|---|---|
| `onboarding_modal` | [OnboardingModal.svelte:60](../frontend/src/lib/components/OnboardingModal.svelte#L60) — currently `goto('/signin')` with no `redirectTo`; align it |
| `follow_button` | [FollowButton.svelte:31](../frontend/src/lib/components/FollowButton.svelte#L31) |
| `notify_button` | [NotifyButton.svelte:22](../frontend/src/lib/components/NotifyButton.svelte#L22) |
| `set_player_prompt` | [players/[id]/+page.svelte:198](../frontend/src/routes/players/[id]/+page.svelte#L198) ("is this you") |
| `home_signin_banner` | [SignInBanner.svelte:9](../frontend/src/lib/components/home/SignInBanner.svelte#L9) |
| `navbar` | [+layout.svelte:57](../frontend/src/routes/+layout.svelte#L57) |
| `account_gate` / `pro_gate` | **server** 303 redirects ([account/+page.server.ts:12](../frontend/src/routes/account/+page.server.ts#L12), [pro/+page.server.ts:15](../frontend/src/routes/pro/+page.server.ts#L15)) — no client click; infer `source` on the signin page from `redirectTo` |

**Hurdle 2 — Subscribe to Pro** · `pro_prompted { source }` → `pro_checkout_started { source }`

| `source` | Entry point |
|---|---|
| `pro_page` | direct nav to [pro/+page.svelte](../frontend/src/routes/pro/+page.svelte) |
| `h2h_paywall` | locked H2H drawer (non-Pro user) |
| `account_upgrade` | upgrade CTA on the account page |

> **This phase stops at `pro_checkout_started`** — it already tells you which prompts drive
> checkout attempts, with zero backend changes. The true webhook-confirmed `pro_subscribed`
> (via Stripe Checkout session `metadata`, echoed back on `checkout.session.completed`) is
> **explicitly deferred** to whenever the already-deferred Stripe live-mode work happens —
> not worth building in isolation now.

### Scope note
We capture the **click/intent** (`*_prompted`), not the **impression** (CTA shown). Impression
tracking would enable "of everyone who *saw* the follow prompt, X% clicked" but adds event
volume and complexity — defer unless a specific funnel needs it.

---

## Explicitly out of scope (this phase)

- **Consent banner** — deferred; pre-launch checklist item.
- **Session replay** — off.
- **Autocapture** — off.
- **Server-side events** (Kotlin) — future, for webhook-confirmed events like `pro_subscribed`.
- **Feature flags / A/B testing / surveys / CDP** — not needed at current scale.

---

## Rollout checklist

**Phase 0 — PostHog setup (dashboard)**
- [ ] Create PostHog org (EU region).
- [ ] Create two projects: `ttscore-staging`, `ttscore-prod`. Grab each project API key.

**Phase 1 — code (one feature branch, no direct staging/main commits)**
- [ ] `pnpm add posthog-js` in `frontend/`.
- [ ] `lib/analytics.ts`: `initAnalytics()`, `identify`/`reset`, typed event helpers.
- [ ] Wire init + `afterNavigate` pageview + identify into `+layout.svelte`.
- [ ] Add the interaction event calls to their components.

**Phase 1b — conversion attribution (`source` layer)**
- [ ] Add `source` enums to `lib/analytics.ts`; typed `*_prompted` helpers.
- [ ] `signup_prompted { source }` at each sign-up entry point (table above); align `OnboardingModal` to carry it.
- [ ] `pro_prompted { source }` + `pro_checkout_started { source }`.
- [ ] In PostHog: build the 2 funnels (`*_prompted` → completion event), breakdown by `source`.

**Phase 2 — deploy & verify**
- [ ] Set `PUBLIC_POSTHOG_KEY` / `PUBLIC_POSTHOG_HOST` on the **staging** frontend service; rebuild.
- [ ] Smoke-test on staging: confirm pageviews + a follow + a checkout-start land in the staging project's "Activity/Live events" view.
- [ ] Verify attribution survives a **Google OAuth** round-trip (sign out, click follow-button prompt, sign in with Google, confirm the funnel breakdown attributes the resulting `signed_in` to `source: follow_button`).
- [ ] Set prod vars; rebuild prod frontend.

**Pre-launch (before broad/EU-facing launch)**
- [ ] Consent banner gating analytics init.
- [ ] Privacy policy mentions analytics + legal basis.

---

## Open decisions (defaults chosen)

- **Two PostHog projects** (per env) vs one project tagged with an `environment` property —
  defaulting to two, consistent with Supabase-per-env. Override if you'd rather one project.
- **Identify by Supabase user id** (chosen) vs anonymous-only until consent — accepted as a
  pre-launch trade-off, same spirit as deferring Stripe/hardening.
