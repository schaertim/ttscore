-- Per-game ELO history: make season_id optional, add provisional flag, reindex on date.
ALTER TABLE player_elo ALTER COLUMN season_id DROP NOT NULL;
ALTER TABLE player_elo ADD COLUMN is_provisional BOOLEAN NOT NULL DEFAULT FALSE;
DROP INDEX IF EXISTS idx_elo_player;
CREATE INDEX idx_elo_player ON player_elo(player_id, recorded_at DESC);
