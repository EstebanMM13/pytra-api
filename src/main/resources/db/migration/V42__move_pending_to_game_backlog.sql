-- "Pendiente" moves from runs to games: a game can be in the backlog without ever being started.
-- Every game with a PENDIENTE run joins the backlog and those runs are deleted (they were placeholders).
-- See odd/tasks/game-backlog.md. Idempotent: safe to run twice.
ALTER TABLE games ADD COLUMN IF NOT EXISTS backlog BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE games SET backlog = TRUE
WHERE id IN (SELECT game_id FROM experiences WHERE status = 'PENDIENTE');

-- Like deleting a run through the API (DiaryEntryGameRepository#detachRun): the diary line stays, without
-- the run and without its hours counting as online playtime.
UPDATE diary_entry_games SET experience_id = NULL, counts_hours = FALSE
WHERE experience_id IN (SELECT id FROM experiences WHERE status = 'PENDIENTE');

-- game_platform_links.experience_id is ON DELETE SET NULL.
DELETE FROM experiences WHERE status = 'PENDIENTE';
