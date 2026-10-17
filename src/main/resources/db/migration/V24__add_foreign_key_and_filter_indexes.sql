-- Indexes for the per-user filters and foreign keys that had none. PostgreSQL does not index a
-- foreign key column automatically, so ON DELETE CASCADE / SET NULL from the parent table and
-- the user-scoped stats queries were scanning the child tables.
-- Already covered (not repeated here): games(user_id, LOWER(name)), sagas(user_id, LOWER(name)),
-- online_playtimes(user_id, game_id), game_genres(game_id, genre_id), game_platform_links(game_id, platform),
-- online_playtime_platforms PK + (platform_id), experiences(platform_id), year_notes(user_id, year).
-- Tables are small, so a plain (non-concurrent) CREATE INDEX inside the Flyway transaction is fine.

-- Runs: every stats/library query filters by user_id; the game detail also filters by game_id.
CREATE INDEX IF NOT EXISTS ix_experiences_user_id_game_id ON experiences (user_id, game_id);
-- Runs: deleting a game cascades by game_id alone.
CREATE INDEX IF NOT EXISTS ix_experiences_game_id ON experiences (game_id);

-- Online playtimes: deleting a game cascades by game_id alone (the unique key leads with user_id).
CREATE INDEX IF NOT EXISTS ix_online_playtimes_game_id ON online_playtimes (game_id);

-- Games: deleting a saga sets saga_id to NULL on its games.
CREATE INDEX IF NOT EXISTS ix_games_saga_id ON games (saga_id);

-- Game genres: deleting a genre cascades by genre_id (the primary key leads with game_id).
CREATE INDEX IF NOT EXISTS ix_game_genres_genre_id ON game_genres (genre_id);

-- Platform links: the Steam sync looks links up by user and platform.
CREATE INDEX IF NOT EXISTS ix_game_platform_links_user_id_platform ON game_platform_links (user_id, platform);
-- Platform links: deleting a run sets experience_id to NULL.
CREATE INDEX IF NOT EXISTS ix_game_platform_links_experience_id ON game_platform_links (experience_id);
