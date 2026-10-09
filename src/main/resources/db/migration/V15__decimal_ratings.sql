-- Ratings accept up to 2 decimals (e.g. 9.25). Existing integer values are preserved (9 -> 9.00).
-- The 0..10 CHECK constraints are recreated so they compare against the new numeric type.
ALTER TABLE experiences DROP CONSTRAINT chk_experiences_rating;
ALTER TABLE experiences ALTER COLUMN rating TYPE NUMERIC(4, 2) USING rating::NUMERIC(4, 2);
ALTER TABLE experiences
    ADD CONSTRAINT chk_experiences_rating CHECK (rating IS NULL OR rating BETWEEN 0 AND 10);

ALTER TABLE online_playtimes DROP CONSTRAINT chk_online_playtimes_rating;
ALTER TABLE online_playtimes ALTER COLUMN general_rating TYPE NUMERIC(4, 2) USING general_rating::NUMERIC(4, 2);
ALTER TABLE online_playtimes
    ADD CONSTRAINT chk_online_playtimes_rating CHECK (general_rating IS NULL OR general_rating BETWEEN 0 AND 10);
