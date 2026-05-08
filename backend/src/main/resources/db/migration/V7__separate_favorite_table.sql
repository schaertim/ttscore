-- V7: Split follow into two purpose-built tables.
--
-- follow   → notification subscriptions (bell)
-- favorite → UI bookmarks / starred items (star)
--
-- V6 added a `kind` column to follow. Remove it and restore the original
-- unique constraint, then create a dedicated favorite table.

DROP INDEX IF EXISTS idx_follow_user_kind;

ALTER TABLE follow DROP CONSTRAINT IF EXISTS follow_user_kind_target_unique;
ALTER TABLE follow DROP COLUMN IF EXISTS kind;

ALTER TABLE follow
    ADD CONSTRAINT follow_user_target_unique
    UNIQUE (user_id, target_type, target_id);

-- Dedicated favorite table — same shape, separate domain.
CREATE TABLE favorite (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     TEXT NOT NULL,
    target_type follow_target_type NOT NULL,
    target_id   UUID NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, target_type, target_id)
);

CREATE INDEX idx_favorite_user   ON favorite(user_id);
CREATE INDEX idx_favorite_target ON favorite(target_type, target_id);
