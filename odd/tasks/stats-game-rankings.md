# Stats game rankings

Objective: a "Juegos" screen under Stats with full game rankings, plus the library renamed to "Biblioteca".
Branches: pytra-api `feat/stats-game-rankings` (from `feat/run-postgame`, which is what production runs), pytra-web `dev`. Delivery: ask-on-risk. Runners: `./mvnw test` (api, needs Docker), `ng test` (web, run in pytra-web).

## Specs

- S1 The main section "Juegos" is renamed: "me gustaria cambiar el nombre de Juegos por Biblioteca". Route `/games` unchanged; counters ("Juegos: 75") and search result groups keep "Juegos".
- S2 New Stats screen "Juegos" (`/stats/games`, hub card titled "Juegos"): "en Estadisticas no hay pantalla de Juegos en sí". It holds full rankings ("rankings completos"), up to 50 rows each (the existing API cap).
- S3 Más jugados: by hours, singleplayer / online switch (existing endpoints with a larger limit).
- S4 Mejor valorados: full top-rated ranking (existing endpoint with a larger limit).
- S5 Más rejugados: games ordered by number of runs. Assumption: PENDIENTE runs do not count, and only games with 2+ counted runs appear.
- S6 Partidas más largas: single runs ordered by their own hours (not the game total). Assumption: PENDIENTE runs and runs with 0 hours are left out.
- S7 Joyas cortas: "Juegos con nota alta y pocas horas". Assumption (thresholds open to change): game rating >= 8 and 0 < total hours <= 15, best rating first, then fewer hours.
- S8 Más días jugados: distinct diary days per game (diary exists since V31, so no earlier history).
- S9 Más tiempo en pendientes: games whose current status (latest run, ExperiencePeriod.RECENCY) is PENDIENTE, oldest waiting first. User chose: "la migración con fecha de creación". V39 adds `experiences.created_at`; existing rows are backfilled with the earliest known date (start date or last update). Ties broken by run id.
- S10 Dashboard "Mejor valorados" and "Más jugados" keep their top 5 and get a "Ver todos" link to `/stats/games`.
- Rankings cover CONFIRMED games only (like the other game-grouped stats); pending imports are not in the library yet.

## Tasks

- [x] T1 S1 rename labels (web) — inline — pytra-web 42605c1
- [ ] T2 S5-S9 API: V39 created_at + `GET /api/v1/stats/game-rankings` — inline (Risk: item 1, migration)
- [ ] T3 S2-S9 web: stats/games screen, hub card, i18n — inline
- [ ] T4 S10 web: dashboard "Ver todos" links — inline

## Log

- L1 (2026-11-01) User: "vale tengo una idea, me gustaria cambiar el nombre de Juegos por Biblioteca (igual le pega un poco mas), además en Estadisticas no hay pantalla de Juegos en sí. Que hay un par de fichas en Inicio pero no hay muchas. Que te parece?"
- L2 Hub card named "Juegos" (Claude's recommendation, user: "vale, dale"). Rankings over collection status (user: "dentro de Estadisticas pues será mejor rankings completos no?"). Extra rankings S6-S9: "las cuatro me gustan, mételas todas". S9 storage: "la migración con fecha de creación".
- L3 T1 done on pytra-web dev (moved from the master worktree, reverted there). Tests not run: they need node_modules.
- L4 Route declaration: all tasks inline. The web units depend on the API contract (not independent), and each is about 100-300 lines, so the Writer trigger does not fire.
