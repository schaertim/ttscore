-- V22: Let a player be rostered on more than one team in the same season.
--
-- player_season had UNIQUE (player_id, season_id), so a player could only ever be linked to a
-- single team per season. In reality players sub between a club's teams (e.g. a first-team
-- player filling in for the reserves), and the click-tt scraper's upsertPlayer() inserts a
-- player_season row for whichever team a game belongs to. With the old constraint, the row for
-- the second team silently no-opped (insertIgnore hit the (player_id, season_id) conflict), so
-- that team's roster permanently missed players who had already been registered to another team
-- that season — see team fd4e0447-fa97-40f4-b3f6-7c14fd68512a ("Port II") missing two players
-- who were rostered under "Port" instead.
ALTER TABLE player_season DROP CONSTRAINT player_season_player_id_season_id_key;
ALTER TABLE player_season ADD CONSTRAINT player_season_player_id_team_id_season_id_key UNIQUE (player_id, team_id, season_id);

-- Backfill: the constraint above was silently swallowing player_season rows for years, so derive
-- the missing (player, team, season) links directly from games already on record — every game row
-- names the team (via match.home/away_team_id) and player(s) on each side, and the season follows
-- from match.group_id -> division_group.season_id. ON CONFLICT DO NOTHING makes this idempotent.
INSERT INTO player_season (player_id, team_id, season_id)
SELECT DISTINCT player_id, team_id, season_id FROM (
    SELECT g.home_player1_id AS player_id, m.home_team_id AS team_id, dg.season_id
    FROM game g JOIN match m ON m.id = g.match_id JOIN division_group dg ON dg.id = m.group_id
    WHERE g.home_player1_id IS NOT NULL
    UNION ALL
    SELECT g.home_player2_id, m.home_team_id, dg.season_id
    FROM game g JOIN match m ON m.id = g.match_id JOIN division_group dg ON dg.id = m.group_id
    WHERE g.home_player2_id IS NOT NULL
    UNION ALL
    SELECT g.away_player1_id, m.away_team_id, dg.season_id
    FROM game g JOIN match m ON m.id = g.match_id JOIN division_group dg ON dg.id = m.group_id
    WHERE g.away_player1_id IS NOT NULL
    UNION ALL
    SELECT g.away_player2_id, m.away_team_id, dg.season_id
    FROM game g JOIN match m ON m.id = g.match_id JOIN division_group dg ON dg.id = m.group_id
    WHERE g.away_player2_id IS NOT NULL
) missing
ON CONFLICT (player_id, team_id, season_id) DO NOTHING;
