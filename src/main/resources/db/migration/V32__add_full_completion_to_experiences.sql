-- "100%" flag of a run: every trophy/achievement of the game obtained. Set by the user only (never by the
-- platform sync) and independent of platinum, so both, either or none can be true.
ALTER TABLE experiences ADD COLUMN full_completion BOOLEAN NOT NULL DEFAULT FALSE;
