-- V14: Pro entitlement on the user profile.
--
-- A single paid tier ("Pro"). `pro_until` is the expiry of the current entitlement:
--   NULL or a past timestamp = free tier
--   a future timestamp        = active Pro
-- Set by the Stripe webhook (later phase); for testing, grant with a manual UPDATE:
--   UPDATE user_profile SET pro_until = now() + interval '1 year' WHERE user_id = '<sub>';

ALTER TABLE user_profile
    ADD COLUMN pro_until TIMESTAMPTZ NULL;
