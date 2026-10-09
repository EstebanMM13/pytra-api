-- Free-text yearly review (summary + highlights) shown in the year summary. One row per user and year.
CREATE TABLE year_notes(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    year INTEGER NOT NULL,
    summary TEXT,
    highlights TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_year_notes_user_year UNIQUE (user_id, year)
);
