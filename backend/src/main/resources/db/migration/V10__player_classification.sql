-- Moves official classification off player_season (which is team-bound and one-per-team) into a
-- dedicated, team-independent table. A season straddles the Jan-1 reclassification, so every
-- season has two classes: first half = Jul-Dec, second half = Jan-Jun.
CREATE TABLE player_classification (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id         UUID NOT NULL REFERENCES player(id),
    season_id         UUID NOT NULL REFERENCES season(id),
    first_half_class  VARCHAR(5),
    second_half_class VARCHAR(5),
    UNIQUE (player_id, season_id)
);

-- Classification no longer lives on player_season.
ALTER TABLE player_season
    DROP COLUMN IF EXISTS klass;
