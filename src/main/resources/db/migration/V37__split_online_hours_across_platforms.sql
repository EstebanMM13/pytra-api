-- Online hours are now entered per platform: records with platforms but no per-platform hours get their total
-- split evenly across their platforms (the split the stats used until now).
INSERT INTO online_playtime_platform_hours (online_playtime_id, platform_id, hours)
SELECT op.id, opp.platform_id, op.total_hours / counts.n
FROM online_playtimes op
JOIN online_playtime_platforms opp ON opp.online_playtime_id = op.id
JOIN (SELECT online_playtime_id, COUNT(*) AS n FROM online_playtime_platforms GROUP BY online_playtime_id) counts
  ON counts.online_playtime_id = op.id
WHERE op.total_hours > 0
  AND NOT EXISTS (SELECT 1 FROM online_playtime_platform_hours h WHERE h.online_playtime_id = op.id);
