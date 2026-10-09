CREATE TABLE online_playtimes(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    game_id BIGINT NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    total_hours DOUBLE PRECISION NOT NULL DEFAULT 0,
    last_session_at TIMESTAMP,
    general_rating INTEGER,
    notes TEXT,
    CONSTRAINT uq_online_playtimes_user_game UNIQUE (user_id, game_id),
    CONSTRAINT chk_online_playtimes_rating CHECK (general_rating IS NULL OR general_rating BETWEEN 0 AND 10)
);
