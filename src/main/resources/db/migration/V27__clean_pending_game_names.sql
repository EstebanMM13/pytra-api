-- Pending games imported before the importers stored ImportedGameNames.cleanName(): their name still carries
-- the store noise ("Marvel's Spider-Man™", "Grand Theft Auto V (PlayStation®5)"). Rewrites it the way a new
-- import stores it, step by step as in ImportedGameNames.cleanName():
--   1. a mark followed by whitespace, ASCII punctuation (Java \p{Punct}), a typographic quote or the end is
--      dropped ("Tsushima™: Director's Cut" -> "Tsushima: Director's Cut")
--   2. any other mark becomes a space ("PlayStation®5" -> "PlayStation 5")
--   3. NFKC
--   4. typographic apostrophes as ', typographic double quotes as "
--   5. whitespace runs collapsed to one space, trimmed
--   6. trailing console suffix dropped: " (PlayStation 5)", " (PS4)", " (PS4 & PS5)", " PS4 & PS5", trimmed
--   7. cut to games.name length (ImportedGameNames.truncate)
-- Known differences with Java, harmless for real names: Postgres \s also matches Unicode spaces (Java \s is
-- ASCII only; NFKC already turns most of them into a plain space) and btrim only trims plain spaces (Java
-- strip() trims any whitespace; after step 5 only plain spaces are left at the ends).
--
-- A game is renamed only when the clean name is not empty, differs from the current one and is not taken
-- (case-insensitively, as the unique index on (user_id, LOWER(name)) sees it) by another game of the user.
-- When several pending games of a user clean to the same name only the oldest is renamed; the others keep
-- their name. Confirmed and ignored games are never touched. Re-running it changes nothing.
--
-- Where imported_name is NULL (imported before V26) or still equals the old name, it becomes the clean name
-- and igdb_lookup_at is cleared: the next sync asks IGDB again, with the clean name, and a confident match
-- may then replace the name (that replacement requires name = imported_name).
WITH cleaned AS (
    SELECT g.id, g.user_id, g.name, left(s6.value, 255) AS clean_name
    FROM games g
    CROSS JOIN LATERAL (SELECT regexp_replace(g.name,
            '[™®©]+(?=[[:space:]!-/:-@[-`{-~‘’“”]|$)', '', 'g') AS value) s1
    CROSS JOIN LATERAL (SELECT normalize(regexp_replace(s1.value, '[™®©]', ' ', 'g'), NFKC) AS value) s3
    CROSS JOIN LATERAL (SELECT regexp_replace(regexp_replace(s3.value,
            '[‘’‚‛′´`]', '''', 'g'), '[“”„‟″]', '"', 'g') AS value) s4
    CROSS JOIN LATERAL (SELECT btrim(regexp_replace(s4.value, '[[:space:]]+', ' ', 'g')) AS value) s5
    CROSS JOIN LATERAL (SELECT btrim(regexp_replace(s5.value,
            '[[:space:]]*(?:\((?:PlayStation[[:space:]]*[45]|PS[45](?:[[:space:]]*(?:&|and|/|\+)[[:space:]]*PS[45])?)\)'
                || '|[[:space:]]-?[[:space:]]*PS4[[:space:]]*(?:&|and|/|\+)[[:space:]]*PS5)[[:space:]]*$',
            '', 'i')) AS value) s6
    WHERE g.review_status = 'PENDING_REVIEW'
),
renamed AS (
    SELECT id, user_id, clean_name,
           row_number() OVER (PARTITION BY user_id, LOWER(clean_name) ORDER BY id) AS position
    FROM cleaned
    WHERE clean_name <> '' AND clean_name <> name
)
UPDATE games g
SET name           = r.clean_name,
    imported_name  = CASE WHEN g.imported_name IS NULL OR g.imported_name = g.name
                          THEN r.clean_name ELSE g.imported_name END,
    igdb_lookup_at = CASE WHEN g.imported_name IS NULL OR g.imported_name = g.name
                          THEN NULL ELSE g.igdb_lookup_at END,
    updated_at     = now()
FROM renamed r
WHERE g.id = r.id
  AND r.position = 1
  AND NOT EXISTS (SELECT 1
                  FROM games other
                  WHERE other.user_id = r.user_id
                    AND other.id <> r.id
                    AND LOWER(other.name) = LOWER(r.clean_name));
