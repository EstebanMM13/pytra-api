-- Play diary: one entry per user and day (optional note) with the games played that day.
CREATE TABLE diary_entries (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    entry_date DATE        NOT NULL,
    note       VARCHAR(1000),
    created_at TIMESTAMP   NOT NULL,
    updated_at TIMESTAMP   NOT NULL,
    CONSTRAINT uq_diary_entries_user_date UNIQUE (user_id, entry_date)
);

-- A game played on a diary day. counts_hours tells whether the hours were added to the game's data:
-- to the run in experience_id, or to the game's online playtime when experience_id is null. Lines of
-- platform-synced games keep counts_hours = false (the sync already adds the real playtime).
-- Deleting a game deletes its lines (its runs and online playtime go with it). Deleting a run through the
-- API detaches its lines first (experience_id = null, counts_hours = false); ON DELETE SET NULL is the
-- database-level fallback.
CREATE TABLE diary_entry_games (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entry_id      BIGINT        NOT NULL REFERENCES diary_entries(id) ON DELETE CASCADE,
    game_id       BIGINT        NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    experience_id BIGINT        REFERENCES experiences(id) ON DELETE SET NULL,
    hours         NUMERIC(5, 2) NOT NULL,
    note          VARCHAR(500),
    counts_hours  BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_diary_entry_games_entry_game UNIQUE (entry_id, game_id),
    CONSTRAINT chk_diary_entry_games_hours CHECK (hours > 0 AND hours <= 24)
);

CREATE INDEX ix_diary_entry_games_game_id ON diary_entry_games (game_id);
CREATE INDEX ix_diary_entry_games_experience_id ON diary_entry_games (experience_id);
