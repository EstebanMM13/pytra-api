# Achievements stay unlocked

Objective: once an achievement is unlocked it stays unlocked, even if the data that earned it changes later.
Branches: pytra-api `feat/stats-game-rankings` (pushed to origin/master), pytra-web `dev`. Delivery: ask-on-risk. Runners: `./mvnw test` (api; Testcontainers need Docker, not available locally: CI runs them), `npx ng test --watch=false` (web).

## Specs

- S1 "Y que los logros una vez conseguidos ya se queden desbloqueados claro". Context: "tengo 3 partidas a la vez ... no se deberian quedar completados? Quiero decir si lo consigo y en algun momento ya no se vuelve a bloquear?"
- S2 The API stores each unlocked achievement per user with the date it was first unlocked (new table via Flyway V41; user-scoped like every other table). Endpoints under `/api/v1/achievements`: list the user's unlocked achievements (id + unlockedAt) and record newly unlocked ids (idempotent: recording an id again keeps the first date). Ids are the web's achievement ids (e.g. `multitasker`, `years10`); the API validates their shape (non-blank, bounded length, safe characters) since the catalog lives in the web.
- S3 The web keeps computing achievements from the data as today; an achievement is unlocked when it is computed as unlocked OR stored as unlocked. Computed unlocks not yet stored are recorded (once; failures must not break the page and are retried on a later load).
- S4 A stored achievement whose current progress fell below its target still shows as unlocked (progress shown as complete), and counts for the `legend` meta badge and the dashboard's top unlocked badges.
- S5 Assumption: the profile shows the unlock date of stored achievements ("Conseguido el 1 nov 2026"); achievements unlocked before this feature get the date of the first load after deploy.
- S6 Account deletion removes the user's stored achievements; account export includes them if the export lists per-user data tables.

## Tasks

- [x] T1 S1-S6 API table + endpoints + web merge/record/display — delegated (Writer trigger: parallel with hours-minutes T1, disjoint surfaces; long-session backstop). Risk: item 1 (new migration), item 3 is not hit (new endpoints only).

## Log

- L1 (2026-11-01) User reported Multitarea locked with 3 runs (one in postgame): count fixed in api e47d593. Then: "Y que los logros una vez conseguidos ya se queden desbloqueados claro".
- L2 (2026-11-01) T1 done by a delegated writer: api 8d5b603 (V41 unlocked_achievements, GET/POST /api/v1/achievements, export), web d98d804 (merge computed OR stored, record once, unlock date in the profile) + 01c36d6 (refresh the cached stored list after recording; verifier advisory, RED observed then GREEN). Local: api unit 26/26, web 1165 tests, build clean. CI api 1185/0/0 (Postgres, V41 included). Independent verifier: S1-S6 PASS, no blockers. RDD range 43f6348..8d5b603 escalated high (item 1), granted, approved, acknowledged; informational: concurrent POSTs can slightly exceed the 300 cap; a postgame run counts as completed and in progress (only Multitarea reads that count).
