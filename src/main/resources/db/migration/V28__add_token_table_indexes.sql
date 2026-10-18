-- Token lookups (verify email, reset password, exchange code) filter by token_hash, which had no index.
-- Unique: the hash is the SHA-256 of 32 SecureRandom bytes (TokenGenerator), so values never repeat, and
-- findByTokenHash expects at most one row anyway. steam_link_states.token_hash is already UNIQUE (V10).
CREATE UNIQUE INDEX IF NOT EXISTS ux_email_verification_tokens_token_hash ON email_verification_tokens (token_hash);
CREATE UNIQUE INDEX IF NOT EXISTS ux_password_reset_tokens_token_hash ON password_reset_tokens (token_hash);
CREATE UNIQUE INDEX IF NOT EXISTS ux_exchange_code_tokens_token_hash ON exchange_code_tokens (token_hash);

-- user_id: the resend cooldown and token invalidation query by user, and deleting a user cascades by user_id.
CREATE INDEX IF NOT EXISTS ix_email_verification_tokens_user_id ON email_verification_tokens (user_id);
CREATE INDEX IF NOT EXISTS ix_password_reset_tokens_user_id ON password_reset_tokens (user_id);
CREATE INDEX IF NOT EXISTS ix_exchange_code_tokens_user_id ON exchange_code_tokens (user_id);
CREATE INDEX IF NOT EXISTS ix_steam_link_states_user_id ON steam_link_states (user_id);
