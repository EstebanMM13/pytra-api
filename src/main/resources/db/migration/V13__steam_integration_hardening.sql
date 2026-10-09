-- One Steam account per Pytra user. Earlier versions did not enforce it, so keep only the
-- oldest link for any duplicated steam_id before adding the constraint.
DELETE FROM steam_links a
    USING steam_links b
    WHERE a.steam_id = b.steam_id
      AND a.id > b.id;

ALTER TABLE steam_links ADD CONSTRAINT uq_steam_links_steam_id UNIQUE (steam_id);

ALTER TABLE steam_links ADD COLUMN last_sync_at TIMESTAMP;

-- Steam apps the user chose to ignore: the sync never recreates them as pending games.
CREATE TABLE steam_ignored_apps(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    app_id VARCHAR(32) NOT NULL,
    name VARCHAR(255) NOT NULL,
    ignored_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_steam_ignored_apps_user_app UNIQUE (user_id, app_id)
);

CREATE INDEX ix_steam_link_states_expires_at ON steam_link_states (expires_at);
