-- V21: Index the team-scoped lookup columns behind the team detail page.
--
-- match(home_team_id | away_team_id): every team query filters `home_team_id = ? OR away_team_id = ?`
-- (match list, last-5 form, previous meeting) but neither was indexed, so each did a seq scan of the
-- ~180k-row match table. Two btree indexes let the OR resolve via a bitmap-index scan.
CREATE INDEX IF NOT EXISTS idx_match_home_team ON match (home_team_id);
CREATE INDEX IF NOT EXISTS idx_match_away_team ON match (away_team_id);

-- player_season(team_id): the team roster looks players up by team_id, but the only index is
-- (player_id, season_id) — useless for a team_id filter — so it seq-scanned all ~80k rows.
CREATE INDEX IF NOT EXISTS idx_player_season_team ON player_season (team_id);
