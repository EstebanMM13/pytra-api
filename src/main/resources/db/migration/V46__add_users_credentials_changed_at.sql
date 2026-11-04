-- Revocation cut-off for JWTs: a token whose iat is before this instant no longer authenticates.
-- Set on password reset (and when Google sign-in drops the password of an unverified account). NULL = never revoked.
ALTER TABLE users ADD COLUMN credentials_changed_at TIMESTAMP WITH TIME ZONE;
