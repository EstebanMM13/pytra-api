-- Achievements the user has unlocked, kept even when the data that earned them changes later.
-- The catalog lives in the web: achievement_id is the web's id (e.g. 'multitasker'). One row per user
-- and achievement; unlocked_at is the first time it was recorded.
CREATE TABLE unlocked_achievements(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    achievement_id VARCHAR(64) NOT NULL,
    unlocked_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_unlocked_achievements_user_achievement UNIQUE (user_id, achievement_id)
);
