-- V26: Functional index on lower(full_name).
--
-- PlayerService.findPlayerIdByName's exact-match fast path filters on
-- lower(full_name) = ?, but nothing indexed that expression, so it paid a full
-- sequential scan of the player table on every call — including on the request
-- path (GET /players/{id}/sync, via ClickTTSyncService's tournament-opponent
-- resolution). A btree index on the lowered expression lets Postgres use an
-- index scan instead.
CREATE INDEX IF NOT EXISTS idx_player_full_name_lower ON player (lower(full_name));
