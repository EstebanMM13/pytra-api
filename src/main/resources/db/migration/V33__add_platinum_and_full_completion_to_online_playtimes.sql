-- Platinum and "100%" flags of a game's online playtime, for online-only games (no runs) and the online
-- side of hybrid ones. Set by the user only (never by the platform sync); independent of each other.
ALTER TABLE online_playtimes ADD COLUMN platinum BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE online_playtimes ADD COLUMN full_completion BOOLEAN NOT NULL DEFAULT FALSE;
