-- V5: User profile table for app-level user data (home player, future prefs)
--
-- Supabase manages the auth.users table in its own schema — we cannot FK against it.
-- user_id is the Supabase user UUID stored as TEXT, matching the JWT `sub` claim.

CREATE TABLE user_profile (
    user_id         TEXT PRIMARY KEY,
    home_player_id  UUID REFERENCES player(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
