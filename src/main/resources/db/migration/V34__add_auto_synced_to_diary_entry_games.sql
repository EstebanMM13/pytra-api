-- Diary lines written by the Steam/PSN sync (one per game and day, merged across syncs). They only record
-- when the user played: the sync already added those hours to the run or online playtime, so an auto line
-- never counts its hours again.
ALTER TABLE diary_entry_games
    ADD COLUMN auto_synced BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE diary_entry_games
    ADD CONSTRAINT chk_diary_entry_games_auto_record_only CHECK (NOT (auto_synced AND counts_hours));
