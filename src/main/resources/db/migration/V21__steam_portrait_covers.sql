-- Covers are portrait box art (2:3) now. Steam covers stored as the landscape store header
-- (header.jpg, header_alt_assets_N_<lang>.jpg, any ?t= cache buster, any Steam CDN host) become the
-- portrait library capsule of the same app. Every other URL (IGDB, user-provided) is left untouched.
-- Idempotent: URLs that already are the portrait capsule are skipped.
UPDATE games
SET cover_image_url = regexp_replace(
        cover_image_url,
        '^https?://([a-z0-9-]+\.)*(steamstatic\.com|steamcdn-a\.akamaihd\.net)/(store_item_assets/)?steam/apps/([0-9]+)/.*$',
        'https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/\4/library_600x900.jpg',
        'i')
WHERE cover_image_url ~* '^https?://([a-z0-9-]+\.)*(steamstatic\.com|steamcdn-a\.akamaihd\.net)/(store_item_assets/)?steam/apps/([0-9]+)/.*$'
  AND cover_image_url !~ '^https://shared\.akamai\.steamstatic\.com/store_item_assets/steam/apps/[0-9]+/library_600x900\.jpg$';
