-- V16: Link a user profile to its Stripe customer.
--
-- Set the first time a user checks out (from the `checkout.session.completed`
-- webhook) and reused afterwards to open the Stripe billing portal and to resolve
-- subsequent subscription webhooks (customer -> user). One customer per user.

ALTER TABLE user_profile
    ADD COLUMN stripe_customer_id TEXT NULL;

CREATE UNIQUE INDEX user_profile_stripe_customer_id_key
    ON user_profile (stripe_customer_id);
