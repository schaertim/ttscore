-- Adds registration metadata populated from the click-tt club members page
ALTER TABLE player
    ADD COLUMN IF NOT EXISTS sex         VARCHAR(6),
    ADD COLUMN IF NOT EXISTS serie       VARCHAR(20),
    ADD COLUMN IF NOT EXISTS nationality VARCHAR(3);
