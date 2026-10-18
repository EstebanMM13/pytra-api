-- Background IGDB enrichment of the pending games Steam/PSN syncs create.

-- When IGDB was last asked about this pending game (match or not). NULL = never: the next sync of the
-- user queues it. A failed IGDB call leaves it NULL, so the game is retried on a later sync.
ALTER TABLE games ADD COLUMN igdb_lookup_at TIMESTAMP;

-- Name and cover exactly as the import stored them. While the game still carries them the user has not
-- edited them, so a confident IGDB match may replace them with the canonical name / portrait cover.
-- NULL for games created by hand (nothing to replace).
ALTER TABLE games ADD COLUMN imported_name VARCHAR(255);
ALTER TABLE games ADD COLUMN imported_cover_url VARCHAR(500);

-- Pending games imported before this migration: their cover is still the import's when it is a platform
-- image (Steam portrait capsule, PSN artwork). Their name cannot be told apart from a user edit (pending
-- games can be edited without confirming them), so it is never replaced.
UPDATE games
SET imported_cover_url = cover_image_url
WHERE review_status = 'PENDING_REVIEW'
  AND (cover_image_url LIKE 'https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/%/library_600x900.jpg'
       OR cover_image_url LIKE 'https://image.api.playstation.com/%');
