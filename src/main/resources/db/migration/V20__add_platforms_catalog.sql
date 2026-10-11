-- Editable platform catalog (global, like genres) replacing the fixed Platform enum stored on experiences.
--
-- legacy_code keeps the old enum value for the six platforms that existed before, so clients built
-- against the enum (an installed APK bundles the old web) can still send and read it.
-- The legacy XBOX value maps to a generic "Xbox" entry: the enum never said which generation it was,
-- so the data is kept as-is instead of guessing; users can move a run to Xbox Series/One/360 later.
CREATE TABLE platforms (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(40) NOT NULL,
    color VARCHAR(7),
    family VARCHAR(20),
    legacy_code VARCHAR(20) UNIQUE,
    sort_order INTEGER NOT NULL DEFAULT 1000,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_platforms_color CHECK (color IS NULL OR color ~ '^#[0-9A-Fa-f]{6}$')
);

-- Case-insensitive uniqueness: "ps5" and "PS5" are the same platform.
CREATE UNIQUE INDEX ux_platforms_name_lower ON platforms (LOWER(name));

INSERT INTO platforms (name, color, family, legacy_code, sort_order) VALUES
    ('PC',               '#8A857C', 'PC',          'PC',     10),
    ('PS5',              '#4F7BE8', 'PLAYSTATION', 'PS5',    20),
    ('PS4',              '#4F7BE8', 'PLAYSTATION', 'PS4',    30),
    ('PS3',              '#4F7BE8', 'PLAYSTATION', NULL,     40),
    ('PS2',              '#4F7BE8', 'PLAYSTATION', NULL,     50),
    ('PS1',              '#4F7BE8', 'PLAYSTATION', NULL,     60),
    ('PSP',              '#4F7BE8', 'PLAYSTATION', NULL,     70),
    ('PS Vita',          '#4F7BE8', 'PLAYSTATION', NULL,     80),
    ('Xbox Series',      '#4CA64C', 'XBOX',        NULL,    100),
    ('Xbox One',         '#4CA64C', 'XBOX',        NULL,    110),
    ('Xbox 360',         '#4CA64C', 'XBOX',        NULL,    120),
    ('Xbox',             '#4CA64C', 'XBOX',        'XBOX',  130),
    ('Switch 2',         '#E0524C', 'NINTENDO',    NULL,    200),
    ('Switch',           '#E0524C', 'NINTENDO',    'SWITCH', 210),
    ('Wii U',            '#E0524C', 'NINTENDO',    NULL,    220),
    ('Wii',              '#E0524C', 'NINTENDO',    NULL,    230),
    ('GameCube',         '#E0524C', 'NINTENDO',    NULL,    240),
    ('N64',              '#E0524C', 'NINTENDO',    NULL,    250),
    ('3DS',              '#E0524C', 'NINTENDO',    NULL,    260),
    ('DS',               '#E0524C', 'NINTENDO',    NULL,    270),
    ('Game Boy Advance', '#E0524C', 'NINTENDO',    NULL,    280),
    ('Game Boy',         '#E0524C', 'NINTENDO',    NULL,    290),
    ('Móvil',            '#C2873A', 'MOBILE',      'MOBILE', 300),
    ('Retro / Emulador', '#9B7BD4', 'OTHER',       NULL,    400);

ALTER TABLE experiences ADD COLUMN platform_id BIGINT REFERENCES platforms(id);

UPDATE experiences e SET platform_id = p.id
FROM platforms p
WHERE p.legacy_code = e.platform;

-- Defensive: a value outside the old enum (should not exist) becomes its own catalog entry instead of
-- failing the migration or being silently remapped.
INSERT INTO platforms (name, family, sort_order)
SELECT DISTINCT LEFT(e.platform, 40), 'OTHER', 1000
FROM experiences e
WHERE e.platform_id IS NULL AND e.platform IS NOT NULL
ON CONFLICT DO NOTHING;

UPDATE experiences e SET platform_id = p.id
FROM platforms p
WHERE e.platform_id IS NULL AND LOWER(p.name) = LOWER(LEFT(e.platform, 40));

ALTER TABLE experiences ALTER COLUMN platform_id SET NOT NULL;
CREATE INDEX ix_experiences_platform_id ON experiences (platform_id);

-- The old string column is kept (nullable, no longer written) for one release so a rollback of the
-- API still finds it; a later migration drops it.
ALTER TABLE experiences ALTER COLUMN platform DROP NOT NULL;
