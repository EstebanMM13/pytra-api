-- Long-lived, read-only tokens a user creates for external tools (the Obsidian sync plugin). Only the
-- SHA-256 hex of the raw token is stored; the plaintext is shown once at creation. Revoking deletes the row.
CREATE TABLE personal_access_tokens (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name         VARCHAR(50)  NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL,
    prefix       VARCHAR(12)  NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    last_used_at TIMESTAMP
);

CREATE UNIQUE INDEX ux_personal_access_tokens_token_hash ON personal_access_tokens (token_hash);
CREATE INDEX ix_personal_access_tokens_user_id ON personal_access_tokens (user_id);
