# Feature Spec — Player Stats, Career Arc & Head-to-Head

Detailed scope for three connected profile/discovery features. All three lean on data we
already capture; the differentiator is historical depth (back to 1989) done well and free.

_Last updated: 2026-06-27_

---

## Data foundation (read first)

These constraints decide what each feature can compute and how it must be scoped.

| Data point | Availability |
|---|---|
| Result (W/L), set score (e.g. 3:1), opponent, date, home/away | **All games** (knob + click-tt, league + tournament) |
| Point-by-point set scores (11:8, 9:11…) | **All games** |
| Opponent classification at time of game | **All games** (derived via `ClassificationService`) |
| Player **classification** history (per half-season) | **Full history back to 1989** via `PlayerClassifications` — the historical rating signal |
| Per-game **ELO delta** | **click-tt matches only** (recent). Missing on all knob / historical games. |
| Official **ELO** value (the time series) | **Recent seasons only** (click-tt era). **Not available historically** — knob gives points + class, never ELO. |
| Tournament games | **Current season only** |

Two scoping rules follow directly and are non-negotiable for these features:

- **Season Stats** → include **all games of the current season** (league + tournament).
- **Career Arc** → include **league games only**, **all-time** — tournament games are excluded
  because we only have them for the current season and they would bias historical records.
  The career arc is built on **classification** (full history back to 1989), **not ELO** —
  ELO exists only for recent (click-tt) seasons, so an all-time ELO curve is impossible.

---

## Profile structure — one page, three tabs

No new page levels. The existing `players/[id]` profile gains tabs:

- **Overview** — current behaviour (header, ELO chart, recent games). Unchanged.
- **Stats** — current-season deep stats (this doc, §1).
- **Career** — all-time arc (this doc, §2).

Head-to-Head (§3) does **not** live on the profile — it lives in player search.

---

## §1 — Stats tab (current season · all games)

Scope: every game the player played in the **current season**, league and tournament combined.
Most stats are universal (W/L + set scores exist everywhere); ELO-delta-based figures are
available for click-tt matches and shown only where present.

### 1.1 Classification progress
A progress bar from current class to the next, with percentage and points-to-next.
- e.g. `B11 → B12`, 77%, "Points to B12: 9".
- Uses live ELO + the classification thresholds already defined on the profile.
- **Value:** the single most motivating number for an active player — "how close am I to moving up."

### 1.2 Win-rate breakdowns
A list of win rates, each shown as percentage + raw `wins/games`:

| Stat | Definition | Data scope |
|---|---|---|
| Overall win rate | wins / games this season | universal |
| vs higher-classified | record vs opponents in a higher class than the player at game time | universal (classification) |
| vs lower-classified | record vs opponents in a lower class | universal |
| After winning 1st set | record in games where the player won set 1 | universal (point-by-point) |
| After losing 1st set | record in games where the player lost set 1 | universal |
| League | record in league games | universal |
| Tournament | record in tournament games | universal |
| Home | record in home games | universal |
| Away | record in away games | universal |

> "vs higher / lower" is computed on **classification** (every game carries the opponent's class
> at the time). ELO-based refinement is possible for click-tt games but classification is the
> robust universal basis — use it as primary.

**Value:** answers the questions players actually ask about themselves — who they beat, whether
they close out leads, whether they fold after dropping the first set.

### 1.3 Set-score distribution (donut)
Count of game outcomes by set margin, over all decided games this season:
- Wins: `3:0`, `3:1`, `3:2` (and `4:x` if best-of-7 tournament games occur — handle variable best-of)
- Losses: `2:3`, `1:3`, `0:3`
- Center label: total games.
- **Value:** identity at a glance — a 3:0 closer vs a 3:2 grinder. Universal data.

### 1.4 Performance by opponent tier
Grouped W/L (small bars or rows) by opponent **classification band** (e.g. D5, C8, B11, B13, A…).
- Shows where the player's results sit — beating the people they "should," and scalps above.
- **Value:** contextualises the overall record by strength of opposition. Universal.

### 1.5 Monthly form
Wins/losses per month across the current season (diverging bar chart, wins up / losses down).
- **Value:** in-season trajectory — hot streaks and slumps. (This is the only time-bucketed chart
  we keep; see "Explicitly excluded".)

### 1.6 Per-competition breakdown
One card per competition/division the player appeared in this season:
- Competition name, games played, win rate (`wins/games`), and **net ELO delta** for that competition.
- ELO delta is summed from per-game deltas (available for click-tt matches).
- **Value:** separates league form from tournament form and from each distinct competition.

### 1.7 Additional season stats (our own additions)
Beyond the TT-Stats-inspired set above, add where they carry signal:

- **Net ELO change this season** — sum of per-game deltas (click-tt matches) — the headline number.
- **Current & longest win streak** (this season) — universal.
- **Set win rate** — sets won / total sets played — universal.
- **Deciding-set record** — record in games that went to the final possible set (the "clutch" cut) — universal.
- **Point economy** — total points won vs lost, and ratio — only possible because we have
  point-by-point everywhere; a stat no free competitor surfaces.
- **Best win** — highest-classified opponent beaten this season — a shareable highlight.
- **Doubles record** _(optional)_ — W/L in doubles; the current match-history query is singles-only,
  so this needs a small separate query if included.

---

## §2 — Career tab (all-time · league games only)

> **Implemented (2026-07-04):** Pro-gated, classification-based (no historical ELO).
> Endpoint `GET /players/{id}/career` (`PlayerService.getCareer`) → `CareerResponse`, loaded
> **server-side via `authedKtor`** (the endpoint is Pro-gated and the public api client sends no
> token). UI: `player/CareerTab.svelte` composing `ClassArc` / `CareerClubs` / `CareerRivals`;
> non-Pro users see `PaywallTeaser`.

Scope: **league singles only**, across the player's entire history. The story this tab tells is
**where a player has been** — clubs, leagues, and the long arc of their **classification** —
which is exactly the data depth competitors lack. (The activity-heatmap idea was dropped as too
busy.)

### 2.1 Classification arc (the hero) — `ClassArc.svelte`
- Stepped line of the player's class as ladder rank (1–22) across the whole timeline, one point per
  half-season (`PlayerClassifications` first/second half). Y-axis ticks render class labels
  (`classLabelForRank`), stroke coloured by the latest class.
- The historical rating signal — **not ELO**, which we lack that far back.

### 2.2 Career at a glance — lifetime totals (C)
- League-singles all-time: matches, W–L, win rate, seasons played, opponents faced, clubs, and the
  active-years span. Computed in `getCareer` from one results query.

### 2.3 Milestones (F — folded into the totals block)
- Peak class (+ season), longest career win streak, **best win = highest-class opponent beaten**
  (class-based, not ELO), best season (most wins), and debut season.

### 2.4 Clubs & leagues (E) — `CareerClubs.svelte`
- Consecutive same-club seasons collapsed into tenures on a timeline rail, each with its year span
  and the leagues played. No automatic promotion/relegation detection (no clean division-level
  field); per-club win records deferred.

### 2.5 Rivalries (G) — `CareerRivals.svelte`
- Most-played opponents all-time (top 6 by meetings) with career W–L and current class; each row
  links to the opponent's profile. (H2H-drawer integration deferred — the drawer is viewer-centric.)

---

## §3 — Head-to-Head

Entry point now: **player search** (a compare mode — pick two players). Future: the same
component powers an **upcoming-match preview** for followed fixtures.

A H2H view has two parts:

### 3.1 The head-to-head itself
- All-time meetings record (W–L) between the two players, and who leads.
- **Set record** across all meetings.
- List of recent meetings: date, competition, result, set score.
- Data: games where the two players are the two singles participants (either home/away orientation).
  Universal.

### 3.2 Side-by-side player comparison (the visual layer)
Not just the H2H — also each player's overall context, presented side by side and visually:
- **ELO progression** for both (ideally overlaid on one chart for direct comparison).
- Current **class & ELO**, peak ELO, career win rate.
- **Value:** even when two players have rarely or never met, the comparison itself is engaging and
  highly shareable. The overlaid ELO curves are the centrepiece.

**Excluded from H2H:** the "common-opponent bridge" idea — cut.

---

## Deferred (confirmed future, not now)
- **Season Wrapped** — end-of-season recap; reuses §1 + §2 data wholesale.
- **Shareable image cards** — one-tap image of a profile/stat/H2H for messaging apps.

## Explicitly excluded
- **Performance by weekday** — noise, not signal (consistent with `ROADMAP.md`).
- **Common-opponent bridge** in H2H.
- Any standalone match-momentum page, matchday hub, goal tracking, team/captain tools.
- All-time public leaderboards, ELO win-probability, club/league map — out of scope.

## Open dependency
- A **national ELO ranking / percentile** and **most-improved / trending** (in player search) are
  still wanted but gated behind one enabler: a periodic job that guarantees ELO **coverage** for all
  active players. Official ELO changes monthly, so a monthly refresh suffices — not nightly. Tracked
  separately from this spec.
