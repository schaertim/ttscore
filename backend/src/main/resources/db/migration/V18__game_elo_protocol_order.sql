-- V18: Stable per-player ordering for same-day games.
--
-- Games played on the same day share an identical played_at, so ordering history by
-- played_at alone leaves same-day ties to the database's physical row order — which
-- shifts whenever a sync UPDATEs those rows, making the game list reshuffle on reload.
--
-- The click-tt Elo-Protokoll lists a player's rated games in a definite order; we record
-- each game's position there (0 = topmost/most-recent row) as a deterministic tiebreaker.
-- Stored per side, mirroring *_elo_delta, because the same game row belongs to two players
-- and each has its own protocol order — one player's sync must not clobber the other's.
ALTER TABLE game ADD COLUMN home_player1_elo_order INTEGER;
ALTER TABLE game ADD COLUMN away_player1_elo_order INTEGER;
