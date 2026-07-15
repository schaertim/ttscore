-- V20: Index the game table's player columns.
--
-- Every player-scoped query filters `home_player1_id = ? OR away_player1_id = ?`, but neither
-- column was indexed (Postgres does not auto-index foreign keys). On a 1.3M-row game table that
-- meant a full seq scan per lookup — the player sync's per-game ELO-delta matching ran ~48 of them
-- and dominated its runtime, and getMatchHistory / season-stats paid the same cost. Two btree
-- indexes let the OR resolve via a bitmap-index scan (~95x faster in practice).
CREATE INDEX IF NOT EXISTS idx_game_home_player1 ON game (home_player1_id);
CREATE INDEX IF NOT EXISTS idx_game_away_player1 ON game (away_player1_id);
