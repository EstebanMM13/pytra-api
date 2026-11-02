-- Android play time sync: an installed app (package name) is linked to a game through game_platform_links
-- with platform 'ANDROID' and external_id = package name, like Steam app ids and PSN title ids.
-- linked_at: when the link was created. Android only reports the last days of usage; days before the link
-- only set the baseline (never imported), as Steam/PSN never import the history before a link.
ALTER TABLE game_platform_links ADD COLUMN linked_at TIMESTAMP;

-- One game per package and user (one package per game already holds: UNIQUE (game_id, platform)).
CREATE UNIQUE INDEX uq_game_platform_links_android_package
    ON game_platform_links (user_id, external_id)
    WHERE platform = 'ANDROID';
