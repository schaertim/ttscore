# Deferred Features

Items that were consciously scoped out during planning — not forgotten, not rejected. Each entry notes what's missing before it can be built.

---

## Feed: Team position change (T5)

**What:** When a favorited team moves up or down the standings, show a feed item like "Moved to 3rd place" with a trending icon.

**Why deferred:** The `Standings` table is a current-state snapshot with no time dimension. Detecting a position change requires knowing what the position *was* before the latest sync.

**What's needed:**
- A new `StandingSnapshot` table (or `previous_position` column on `Standings`) populated whenever standings are scraped/updated
- A service method that compares current vs. previous position per team
- Add `position_change` as a feed item type in `FeedItemCard.svelte`

---

## Feed: Division group leader change (G3)

**What:** When the leader of a favorited division changes, show a feed item like "TV Malters takes the lead".

**Why deferred:** Same root cause as T5 — no historical standings data available.

**What's needed:**
- Same `StandingSnapshot` infrastructure as T5
- Compare `position = 1` team across snapshots for the group
- Add `group_leader_change` as a feed item type in `FeedItemCard.svelte`

---

## Feed: Upcoming match reminder (P6 / T4)

**What:** Surface "Plays Friday vs. TV Emmen" for a favorited player or team before a scheduled match.

**Why deferred:** Deprioritised by the user during feed type selection (May 2026). The data already exists (`Matches` with `status = SCHEDULED`) — this is purely a product decision to revisit.

**What's needed:** Nothing on the backend. Just add the feed item type and resolution logic in `FavoritesFeed.svelte`.

---

## Standing history infrastructure (prerequisite for T5 + G3)

**What:** A mechanism to record standings at a point in time so changes can be detected.

**Options:**
1. `StandingSnapshot` table — copy of `Standing` rows with a `recorded_at` timestamp, written on each sync run
2. `previous_position` column on `Standings` — simpler, updated atomically during each sync

Option 2 is lower effort and sufficient for T5/G3 as currently scoped.
