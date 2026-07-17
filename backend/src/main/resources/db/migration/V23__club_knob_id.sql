-- V23: Give clubs a stable knob identity.
--
-- knob exposes a `clubid` URL param on every team link that is stable for a club
-- across seasons AND federations (nationwide clubs meet in STT's interregional
-- leagues, so the id space cannot be federation-scoped). Until now the scraper
-- keyed clubs purely on the (suffix-stripped) team name, so whenever knob changed
-- a club's spelling between seasons (e.g. "Pinguin ZH" -> "Pinguin Zürich") it
-- created a second club row. Persisting the knob id lets the scraper recognise the
-- same club and just refresh its name instead of duplicating it.

ALTER TABLE club
    ADD COLUMN knob_id INTEGER NULL;

CREATE UNIQUE INDEX club_knob_id_key
    ON club (knob_id);
