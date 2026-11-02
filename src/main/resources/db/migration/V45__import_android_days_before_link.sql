-- Android usage of the days before a link is now imported (user's choice): the first sync brings the ~7 days
-- the phone keeps. Under the previous rule those days only set the baseline in android_usage_days and never
-- added hours; dropping those rows lets the next sync import them. Only days before the link's day (users'
-- zone, Europe/Madrid; linked_at is server time, UTC on Railway) are dropped: from the link's day on, the
-- baseline holds minutes that were really applied and must stay.
DELETE FROM android_usage_days d
USING game_platform_links l
WHERE l.platform = 'ANDROID'
  AND l.user_id = d.user_id
  AND l.external_id = d.package_name
  AND l.linked_at IS NOT NULL
  AND d.usage_date < ((l.linked_at AT TIME ZONE 'UTC') AT TIME ZONE 'Europe/Madrid')::date;
