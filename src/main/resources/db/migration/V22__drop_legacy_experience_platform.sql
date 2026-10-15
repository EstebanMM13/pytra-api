-- The legacy string column replaced by experiences.platform_id in V20 (kept one release for rollback).
-- Nothing reads or writes it anymore. It had no index or constraint of its own.
-- platforms.legacy_code stays: the API still maps the old enum value through it for older clients.
ALTER TABLE experiences DROP COLUMN IF EXISTS platform;
