-- PSN "concept": Sony's single id for the versions of one game (regional SKUs, PS4/PS5 editions of the
-- same release). Two titles with the same name but different concepts are different games (e.g. Until
-- Dawn 2015 and its 2024 remake), so the PSN import no longer groups them by name alone. Nullable: Steam
-- links never have one, and PSN links get it on their next sync.
ALTER TABLE game_platform_links ADD COLUMN psn_concept_id BIGINT;
