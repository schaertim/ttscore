-- V17: Ledger of one-time scraper backfills.
--
-- Each expensive, run-once backfill (full knob history, click-tt player id linking,
-- a click-tt season seed) records its key here on success. The startup routine checks
-- this table before running a keyed backfill, so restarts never re-trigger them.
-- Recurring jobs (5-min poll, nightly season sync, weekly id refresh) are NOT ledgered.

CREATE TABLE scraper_backfill (
    key          TEXT PRIMARY KEY,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
