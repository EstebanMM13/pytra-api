-- Steam-linked games without a cover get the Steam store header image (external_id holds the appid).
-- User-provided covers are never overwritten.
UPDATE games g
SET cover_image_url = 'https://cdn.cloudflare.steamstatic.com/steam/apps/' || l.external_id || '/header.jpg'
FROM game_platform_links l
WHERE l.game_id = g.id
  AND l.platform = 'STEAM'
  AND (g.cover_image_url IS NULL OR BTRIM(g.cover_image_url) = '');
