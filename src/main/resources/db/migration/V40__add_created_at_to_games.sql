-- When each game was added, for the "longest pending" ranking (games without runs wait since then).
-- Existing games get the earliest date known about them: their last update, or the creation date of
-- their oldest run when that is earlier (a game exists before its runs).
ALTER TABLE games ADD COLUMN created_at TIMESTAMP;

UPDATE games g SET created_at = LEAST(
        g.updated_at,
        COALESCE((SELECT MIN(e.created_at) FROM experiences e WHERE e.game_id = g.id), g.updated_at));

ALTER TABLE games ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE games ALTER COLUMN created_at SET NOT NULL;
