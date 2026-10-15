-- Catalog platforms an online playtime record was played on (a game can be played online on several).
-- Optional: an online record without platforms is still valid (existing rows, Steam-synced rows).
CREATE TABLE online_playtime_platforms (
    online_playtime_id BIGINT NOT NULL REFERENCES online_playtimes(id) ON DELETE CASCADE,
    platform_id BIGINT NOT NULL REFERENCES platforms(id),
    PRIMARY KEY (online_playtime_id, platform_id)
);

CREATE INDEX ix_online_playtime_platforms_platform_id ON online_playtime_platforms (platform_id);
