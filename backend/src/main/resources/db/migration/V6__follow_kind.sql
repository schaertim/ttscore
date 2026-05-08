-- V6: Add kind column to follow table to distinguish favorites (star) from
--     notification subscriptions (bell). These are independent: a user can
--     star a player without subscribing to notifications and vice-versa.

ALTER TABLE follow
    ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'notification';

-- The old unique constraint only covered (user_id, target_type, target_id).
-- Now that kind is a dimension, update it so a user can have both a favorite
-- and a notification entry for the same target.
ALTER TABLE follow
    DROP CONSTRAINT IF EXISTS follow_user_id_target_type_target_id_key;

ALTER TABLE follow
    ADD CONSTRAINT follow_user_kind_target_unique
    UNIQUE (user_id, kind, target_type, target_id);

CREATE INDEX idx_follow_user_kind ON follow(user_id, kind);
