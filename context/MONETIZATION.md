# Monetization — Free / Pro Split

> **Status:** Model decided, not yet built. Gentle freemium. The app is pre-launch
> (no live users), so this split is designed from product logic — nothing is being
> "clawed back" from anyone.

## Goal & context

Solo side project for the Swiss TT community (~5,000 addressable players + interested
family). Goal is **pocket money that at least covers hosting**, not revenue
maximisation. Primary competitor is **TT Stats**, which paywalls core data (standings
past top 3, ELO, stats). Our differentiation — and our word-of-mouth engine — is that
**everything TT Stats charges for, we give away free**. Pro must never break that.

**Break-even:** hosting is ~CHF 20–40/mo. Break-even ≈ 10–20 paying users/year at
CHF ~25. Very achievable; the constraint is protecting the free brand, not hitting a
number.

## The rule

> **Free = everything you can see about _anyone_, plus the retention basics.
> Pro = tools that give _you_ an edge, go deep on a career, or save you effort.**

Browsing and looking up any player/match/table is never gated — that is the anti-TT-Stats
promise and the share funnel that feeds paying users.

---

## FREE

Everything below stays free forever.

**Browsing**
- Seasons, federations, divisions, **full standings tables** (nothing truncated), match result lists

**Matches**
- Match detail + **individual set scores** (the unique hook)

**Player profile**
- **Overview tab:** current ELO, classification, club, **full ELO graph** (recent seasons — ELO is not available historically) with class thresholds, recent + full game history with ELO deltas, next match, league context
- **Stats tab:** all current-season analytics — win/loss + win rate, radar, monthly form, set-score distribution, opponent breakdown, win-rate splits
- **Career tab:** season-by-season career breakdown, milestones, clubs, rivalry detection across seasons

**Personalisation & discovery**
- Search, favorites/bookmarks, recently-viewed, personal dashboard, "set as my player"
- **Follows:** own player **+ up to 3 other entities** (players/teams/divisions)
- **Notifications:** **own player only** (covers the family "notify me when my kid plays" case)
- Basic discovery: leaderboards / most-improved / all-time records (top views)

---

## PRO

**Built / partially built today**
- **Head-to-head (H2H)** — the full "Compare with me" drawer and all H2H records across seasons. *Fully Pro.*
- **Match previews** — opponent scouting ahead of a scheduled match (team and player level). *Fully Pro.*

**Limits lifted by Pro**
- **Notifications for followed entities** — beyond your own player (teams, divisions, other players)
- **Unlimited follows** — beyond own player + 3

**Later (decide when built)**
- CSV/data export, deep leaderboard/record filtering, ad-free. Not committed yet —
  revisit per feature during development.

---

## Free-tier limits (exact numbers)

| Lever | Free | Pro |
|---|---|---|
| Follows | Own player + 3 other entities (home player is exempt from the cap) | Unlimited |
| Push notifications | Own player only | Any followed entity |
| H2H drawer / records | Locked (teaser) | Full |
| Match previews | Locked (teaser) | Full |

---

## UX principle

Free profiles never feel empty: Overview + Stats + Career tabs are rich and free. The
Pro surfaces (H2H drawer, match previews) are **visible but teaser-paywalled** — show a
blurred/preview state with an in-context "Unlock with Pro" prompt at the moment of
intent, rather than hiding them. Same for the follow/notification caps: let the user
hit the limit, then upsell in place.

## Trial approach (decided)

**No trial** (no card-required trial, no reverse trial). Instead: a **low monthly
buy-in** lowers the risk of trying Pro, backed by **permanent teaser paywalls** that
upsell at the moment of intent (H2H drawer, match previews, follow cap, notify bell).

## Pricing (decided)

**CHF 3/month or CHF 25/year** — both offered. The cheap monthly option is the
deliberate low buy-in that stands in for a trial: a player can try Pro for one month at
minimal risk. Yearly (~2/mo effective) rewards commitment and matches the season rhythm.
Stripe (TWINT + cards, no monthly fee); webhook → `pro_until` on `user_profile`.

---

## Implementation touchpoints (for the eventual spec)

- **Entitlement:** `pro_until TIMESTAMPTZ` on `user_profile`; `call.isPro()` helper in Ktor.
- **Notifications:** `MatchPollJob` / `PushService` currently notify followers of home team,
  away team, division group, and every participating player. Gate so that **non-Pro users
  only receive their own-player notification**; team/division/other-player notifications
  require Pro.
- **Follows:** enforce the 3-other-entity cap server-side on `POST /follows` for non-Pro.
- **Frontend gates:** teaser-paywall the H2H drawer and match previews; cap/upsell in the
  follow + notify buttons.
- **Docs:** `PROJECT_DESCRIPTION.md` and `ROADMAP.md` still say "no paywall ever" — soften
  that language honestly once this ships.
