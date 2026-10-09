ALTER TABLE game_platform_links ADD COLUMN experience_id BIGINT REFERENCES experiences(id) ON DELETE SET NULL;
