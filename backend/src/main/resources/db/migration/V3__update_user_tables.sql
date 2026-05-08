-- V3: Align user tables with Better Auth
--
-- Better Auth manages its own `user` table with TEXT primary keys.
-- The original app_user used UUID PKs — incompatible. We drop it and
-- redesign follow + push_subscription to reference Better Auth user IDs
-- directly as TEXT, with no FK (Better Auth's table is outside Flyway control).

DROP TABLE IF EXISTS push_subscription CASCADE;
DROP TABLE IF EXISTS follow CASCADE;
DROP TABLE IF EXISTS app_user CASCADE;
DROP TYPE IF EXISTS follow_target_type;

CREATE TYPE follow_target_type AS ENUM ('player', 'team', 'division_group');

CREATE TABLE follow (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     TEXT NOT NULL,                          -- Better Auth user ID
    target_type follow_target_type NOT NULL,
    target_id   UUID NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, target_type, target_id)
);

CREATE TABLE push_subscription (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     TEXT NOT NULL,                          -- Better Auth user ID
    endpoint    TEXT NOT NULL UNIQUE,
    p256dh      TEXT NOT NULL,
    auth        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_follow_user        ON follow(user_id);
CREATE INDEX idx_follow_target      ON follow(target_type, target_id);
CREATE INDEX idx_push_sub_user      ON push_subscription(user_id);
