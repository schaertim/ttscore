-- V13: Global "pause all" notifications switch on the user profile.
--
-- When true, the push job skips this user entirely, regardless of the per-follow
-- `notify` flags — the follows and their bells are preserved, just silenced.
-- Off by default.

ALTER TABLE user_profile
    ADD COLUMN notifications_paused BOOLEAN NOT NULL DEFAULT false;
