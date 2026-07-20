-- V19: Rename player.serie → player.category.
--
-- "serie" mirrored the German click-tt column header; "category" is the clearer English name
-- for the STT age/eligibility category ("Aktive", "O50", "U17", …) we surface in the UI.
ALTER TABLE player RENAME COLUMN serie TO category;
