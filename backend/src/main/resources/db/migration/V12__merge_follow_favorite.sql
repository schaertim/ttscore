-- V12: Merge `favorite` (star) and `follow` (bell) into a single `follow` table.
--
-- The surviving `follow` table means "the user follows this entity" (drives the
-- home feed + search). The former bell subscription becomes a `notify` flag on
-- that same row, defaulting to OFF. This enforces the invariant notify ⊆ follow:
-- you can only be notified about something you follow.
--
-- Migration strategy: promote `favorite` to be the unified `follow` table, fold
-- the old bell rows in as notify = true, and preserve any bell-only rows.

-- 1. Move the old bell table aside so we can reuse the `follow` name.
ALTER TABLE follow RENAME TO follow_bell_old;

-- 2. Promote favorite (star) to be the unified follow table.
ALTER TABLE favorite RENAME TO follow;

-- 3. Add the notify flag (former bell), off by default.
ALTER TABLE follow ADD COLUMN notify BOOLEAN NOT NULL DEFAULT false;

-- 4. Turn notify on where the user already had a bell subscription for a target
--    they also starred.
UPDATE follow f
SET notify = true
FROM follow_bell_old b
WHERE b.user_id = f.user_id
  AND b.target_type = f.target_type
  AND b.target_id = f.target_id;

-- 5. Preserve bell subscriptions that had no matching star: they become follows
--    with notify = true (keeps existing subscribers subscribed).
INSERT INTO follow (id, user_id, target_type, target_id, notify, created_at)
SELECT b.id, b.user_id, b.target_type, b.target_id, true, b.created_at
FROM follow_bell_old b
WHERE NOT EXISTS (
    SELECT 1 FROM follow f
    WHERE f.user_id = b.user_id
      AND f.target_type = b.target_type
      AND f.target_id = b.target_id
);

-- 6. Drop the old bell table (frees the idx_follow_* index names).
DROP TABLE follow_bell_old;

-- 7. Rename the inherited favorite indexes to match the follow table.
ALTER INDEX idx_favorite_user   RENAME TO idx_follow_user;
ALTER INDEX idx_favorite_target RENAME TO idx_follow_target;

-- Notify lookups (used by the push job) hit (target_type, target_id) filtered by
-- notify, already covered by idx_follow_target.
