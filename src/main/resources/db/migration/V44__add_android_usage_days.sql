-- Minutes of each Android package already recorded per local day (app time zone). The phone sends the day's
-- whole total on every sync; only the part above minutes_recorded is new play. Kept after unlinking, so a
-- relinked package never imports the same day twice.
CREATE TABLE android_usage_days(
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    package_name VARCHAR(255) NOT NULL,
    usage_date DATE NOT NULL,
    minutes_recorded INT NOT NULL,
    PRIMARY KEY (user_id, package_name, usage_date),
    CONSTRAINT ck_android_usage_days_minutes CHECK (minutes_recorded BETWEEN 0 AND 1440)
);
