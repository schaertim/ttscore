-- How each player's click-tt link was established. Lets low-confidence links (name near-matches
-- with no club confirmation) be filtered/flagged rather than trusted blindly. NULL = the player is
-- not click-tt-linked (knob-only). See PlayerService.MatchMethod for the value set.
ALTER TABLE player ADD COLUMN match_method VARCHAR(20);
