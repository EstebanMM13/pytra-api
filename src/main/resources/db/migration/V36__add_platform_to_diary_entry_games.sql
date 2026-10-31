-- Platform a diary line was played on, for lines counted into online playtime when the game is played online
-- on two or more platforms: the hours also go to that platform's share (online_playtime_platform_hours).
-- Null for every other line (runs, sync-written lines, games with fewer than two online platforms).
ALTER TABLE diary_entry_games ADD COLUMN platform_id BIGINT REFERENCES platforms(id) ON DELETE SET NULL;

CREATE INDEX ix_diary_entry_games_platform_id ON diary_entry_games (platform_id);
