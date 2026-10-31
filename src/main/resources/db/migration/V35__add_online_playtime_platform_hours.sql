-- Online hours the user assigned to each platform of an online playtime record (a breakdown of
-- online_playtimes.total_hours, which stays the source of truth: diary and sync keep adding to it).
-- Optional and only meaningful with two or more platforms; the hours not assigned to any platform are
-- the record's "unassigned" share in the platform stats.
CREATE TABLE online_playtime_platform_hours (
    online_playtime_id BIGINT NOT NULL REFERENCES online_playtimes(id) ON DELETE CASCADE,
    platform_id BIGINT NOT NULL REFERENCES platforms(id) ON DELETE CASCADE,
    hours DOUBLE PRECISION NOT NULL CHECK (hours >= 0),
    PRIMARY KEY (online_playtime_id, platform_id)
);

CREATE INDEX ix_online_playtime_platform_hours_platform_id ON online_playtime_platform_hours (platform_id);
