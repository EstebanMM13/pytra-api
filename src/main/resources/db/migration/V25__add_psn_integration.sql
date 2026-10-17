-- PlayStation Network sync. Users do not sign in to PSN: they type their online ID and Pytra reads the
-- public game list with one Pytra-owned service account (PSN_NPSSO). Synced titles reuse the shared
-- tables (games in PENDING_REVIEW + game_platform_links with platform PSN_PS4 / PSN_PS5).

-- One PSN account per Pytra user. account_id is NOT unique across users on purpose: ownership of the
-- online ID is never proven (no PSN login), so a uniqueness rule would let anyone squat someone else's ID.
CREATE TABLE psn_links(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    account_id VARCHAR(32) NOT NULL,
    online_id VARCHAR(32) NOT NULL,
    linked_at TIMESTAMP NOT NULL,
    last_sync_at TIMESTAMP
);

-- PSN titles the user chose to ignore: the sync never recreates them as pending games.
-- Kept apart from steam_ignored_apps so the Steam table, its constraint and its queries stay untouched.
CREATE TABLE psn_ignored_titles(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title_id VARCHAR(32) NOT NULL,
    platform VARCHAR(20) NOT NULL,
    name VARCHAR(255) NOT NULL,
    ignored_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_psn_ignored_titles_user_title UNIQUE (user_id, title_id)
);
