-- Drop Better Auth tables that were created outside Flyway.
-- These are no longer needed now that authentication is handled by Supabase Auth.
DROP TABLE IF EXISTS verification;
DROP TABLE IF EXISTS account;
DROP TABLE IF EXISTS session;
DROP TABLE IF EXISTS "user";
