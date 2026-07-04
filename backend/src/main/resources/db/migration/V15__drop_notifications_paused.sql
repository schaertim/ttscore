-- V15: Drop the "pause all notifications" flag.
--
-- The per-user global mute was removed in favour of the single Push Notifications
-- enable/disable toggle (subscribe / unsubscribe). Unsubscribing already stops every
-- notification, so the separate pause flag was redundant.

ALTER TABLE user_profile
    DROP COLUMN notifications_paused;
