-- Android usage of the days before a link is now imported (user's choice): the first sync brings the ~7 days
-- the phone keeps. Under the previous rule those days only set the baseline in android_usage_days and never
-- added hours; dropping those rows lets the next sync import them.
-- Only rows dated before 2026-11-02 are dropped: Android links went live that day (V43), so no link (current
-- or since unlinked) ever had an earlier day, and those rows can only be baselines that added nothing. Rows
-- from that day on may hold minutes really applied (also under an earlier link of a relinked package) and stay.
DELETE FROM android_usage_days WHERE usage_date < DATE '2026-11-02';
