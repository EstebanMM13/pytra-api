ALTER TABLE games ALTER COLUMN category DROP NOT NULL;

CREATE TABLE game_platform_links(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    game_id BIGINT NOT NULL REFERENCES games(id) ON DELETE CASCADE,
    platform VARCHAR(20) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    last_synced_playtime_minutes BIGINT NOT NULL,
    last_synced_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_game_platform_links_game_platform UNIQUE (game_id, platform)
);

CREATE TABLE steam_links(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    steam_id VARCHAR(32) NOT NULL,
    persona_name VARCHAR(255),
    linked_at TIMESTAMP NOT NULL
);

CREATE TABLE steam_link_states(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP
);
