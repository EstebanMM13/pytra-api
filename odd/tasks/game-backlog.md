# Game backlog ("Pendiente" on the game)

Objective: "Pendiente" becomes a flag of the game (backlog), not a run status; existing PENDIENTE runs are converted and deleted.
Branches: pytra-api `feat/game-backlog` (from feat/stats-game-rankings = origin/master), pytra-web `dev`. Delivery: ask-on-risk. Runners: `./mvnw test` (api; Spring tests need Docker, CI runs them), `ng test` (web).

## Specs
- S1. "lo de pendiente deberia irse al juego no a la experiencia. Porque un juego lo puedes tener pendiente sin haberlo empezado o algo asi" → games get a `backlog` boolean ("Pendiente").
- S2. Migration, user choice "Convertir y borrar": "Cada juego con una partida Pendiente se marca como pendiente y esa partida se borra, porque solo era un hueco vacío. Pendiente desaparece del formulario de partida. Es irreversible: antes hago una copia de seguridad." Diary lines of those runs are detached like `detachRun` (experience_id NULL, counts_hours false).
- S3. `PENDIENTE` leaves `ExperienceStatus` (API rejects it on runs) and the web run form.
- S4. Library "Pendientes" tab = games with `backlog`, plus (as before) games without runs that are not online-only.
- S5. Stats "longest pending" ranking uses the game flag (pending since the game was added); run rankings stop filtering PENDIENTE.
- S6. Web game dialog gets a "Pendiente" toggle; `GameRequestDto.backlog` null keeps the current value on update (older clients), false on create.
- S7 (assumption A1). Creating a run for a backlog game clears the flag: the game has been started.

## Tasks
- T1 [S1-S3,S5-S7] API: V42 migration, Game.backlog, DTOs, service, stats, tests. Route: inline. Risk: items 1 (deletes runs) and 3 (enum value removed).
- T2 [S3,S4,S6] Web: model, game dialog toggle, library tab, run form, stats, i18n, tests. Route: inline (depends on T1's contract).
- T3 Backup before deploy (user), deploy API then web, APK.

## Log
- L1 (2026-11-01) User: "y otra cosa, lo de pendiente deberia irse al juego no a la experiencia. Porque un juego lo puedes tener pendiente sin haberlo empezado o algo asi" → chose "Convertir y borrar (Recomendado)".
- L2 (2026-11-01) Explorer map: experiences.status VARCHAR(20) without CHECK; only diary_entry_games and game_platform_links reference experiences (ON DELETE SET NULL); API reads PENDIENTE only in StatsInsightsServiceImpl (mostReplayed, longestRuns, longestPending). Next migration V42.
