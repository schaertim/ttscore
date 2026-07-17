-- V24: Drop club.knob_id — reverts V23.
--
-- V23 added knob_id as a globally-unique club key on the assumption that knob's `clubid` URL param
-- was unique per club nationwide. It isn't: `clubid` is numbered per federation/region, so the same
-- number is a different club in another region (e.g. clubid 112 = Düdingen in MTTV and Le Locle
-- elsewhere). STT's national leagues mix regions, so a global unique index merged unrelated clubs
-- into one row. Club resolution is back to name-based matching, which never merges distinct clubs.

DROP INDEX IF EXISTS club_knob_id_key;

ALTER TABLE club
    DROP COLUMN IF EXISTS knob_id;
