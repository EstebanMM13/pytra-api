CREATE TABLE games(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    developer VARCHAR(255),
    publisher VARCHAR(255),
    release_date DATE,
    category VARCHAR(20) NOT NULL,
    saga_id BIGINT REFERENCES sagas(id) ON DELETE SET NULL,
    cover_image_url VARCHAR(500),
    review_status VARCHAR(20) NOT NULL
);

CREATE UNIQUE INDEX ux_games_user_id_name_lower ON games (user_id, LOWER(name));

CREATE TABLE game_genres(
    game_id BIGINT NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    genre_id BIGINT NOT NULL REFERENCES genres(id) ON DELETE CASCADE,
    PRIMARY KEY (game_id, genre_id)
);
