-- Recreate push_subscription with user_id TEXT (Supabase UUID string),
-- consistent with follow and user_profile tables.
DROP TABLE IF EXISTS push_subscription;

CREATE TABLE push_subscription (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    TEXT        NOT NULL,
    endpoint   TEXT        NOT NULL UNIQUE,
    p256dh     TEXT        NOT NULL,
    auth       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX push_subscription_user_idx ON push_subscription (user_id);
