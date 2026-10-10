-- Last time the user played the app on the external platform (Steam: rtime_last_played).
-- Nullable: unknown for existing links until their next sync, and Steam reports 0 for never played.
ALTER TABLE game_platform_links ADD COLUMN last_played_at TIMESTAMPTZ;
