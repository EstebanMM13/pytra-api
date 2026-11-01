-- When each run was added, for the "longest pending" ranking. Existing runs get the earliest date known
-- about them: the start date when it is before the last update, otherwise the last update.
ALTER TABLE experiences ADD COLUMN created_at TIMESTAMP;

UPDATE experiences SET created_at = LEAST(updated_at, COALESCE(start_date::timestamp, updated_at));

ALTER TABLE experiences ALTER COLUMN created_at SET DEFAULT now();
ALTER TABLE experiences ALTER COLUMN created_at SET NOT NULL;
