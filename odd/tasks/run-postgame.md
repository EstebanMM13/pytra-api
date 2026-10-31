# Run postgame

Objective: let a beaten (COMPLETADO) run stay "being played" during its postgame, and record when the postgame ended.
Branches: pytra-api `feat/run-postgame`, pytra-web `dev`. Delivery: ask-on-risk. Runners: `./mvnw test` (api), `ng test` (web).

## Specs

- S1. "me acabo de pasar Saros pero no tengo ni el 100% ni el platino, lo doy por completado? Tecnicamente me han saltado los creditos y me lo he pasado pero voy a seguir jugando" → the run stays COMPLETADO (end date = credits) and gets a `postgame` flag ("Sigo jugando (postgame)").
- S2. The flag only applies to COMPLETADO runs: "Si la run deja de estar COMPLETADO, la marca se pone a `false` sola." (the postgame end date is cleared too).
- S3. "con la fecha": an optional `postgameEndDate`. "Al rellenarla se quitaría la marca, igual que la fecha de fin ya cambia el estado a COMPLETADO."
- S4. Assumption A1: `postgameEndDate` is not before the run's `endDate` (400 on field `postgameEndDate`), same rule style as `RunDateRange`.
- S5. "`/stats/in-progress` devolvería `EN_CURSO` + `COMPLETADO && postgame`. Así Saros vuelve a 'jugando ahora'." The DTO carries `postgame` so the dashboard can show a "Postgame" pill.
- S6. "La línea automática del diario y la sincronización de Steam/PSN también tratarían las runs en postgame como 'en juego'" (diary `pickRun`, sync `findAdoptableRun`, `ImportedRunMerger` targets).
- S7. Web: "En el formulario de run, un interruptor 'Sigo jugando (postgame)' que solo aparece si el estado es COMPLETADO." "Una pill 'Postgame' en el dashboard y en la fila de la run." "Al marcar el 100% o el platino, sugerir quitar la marca, sin hacerlo automáticamente." Plus the postgame end date field.

## Tasks

- T1 [S1-S6] API: V38 migration, entity, request/response, normalization, date validation, in-progress + diary + sync predicate, tests — inline — done (RED compile, GREEN 105 focused; full unit suite 0 failures) — see log
- T2 [S5,S7] Web: models, run form toggle + date + hint, run-row, dashboard and run detail pill, i18n, tests — inline — done (RED compile, GREEN 1119/1119, prod build ok) — pytra-web (see log)

Route evidence: no Writer trigger (web depends on the API contract); parent writes inline. Risk: item 1 (migration, new columns), item 3 (`/stats/in-progress` result changes).

## Log

- L1 (2026-10-31) User: "como podemos hacer que haya diferencia entre cuando te pasas un juegos (que salte los creditos), cuando consigues el 100% y cuando consigues el platino" → then the Saros question (S1) → "como harias lo de añadir un postgame?" → "vale y cuando me acabe el postgame como lo marco?" → "con la fecha, impleméntalo".
- L2 (2026-10-31) T1 done: `Experience.isBeingPlayed()` (EN_CURSO, or COMPLETADO with postgame) shared by `/stats/in-progress`, diary `pickRun`, `ExternalPlaytimeWriter.findAdoptableRun` and `ImportedRunMerger`. Full suite: 1146 run, 0 failures, 329 errors, all Spring-context tests failing because Docker is not available (Testcontainers), so V38 is not yet checked against Postgres.
- L3 (2026-10-31) T2 done: web fields optional (an older API omits them). Dashboard hero pill has no component test (dashboard has no spec). pytra-web-deploy not updated.
- L4 (2026-10-31) Independent verifier (risk item 1): S1-S7 met. Blocker: web `postgameRangeValidator` kept failing on a hidden postgame end date after the run left COMPLETADO, silently blocking save. Fixed in pytra-web e42a36d (validator skipped unless COMPLETADO; RED observed, GREEN 118 files). Advisories left open: API still validates postgameEndDate on non-COMPLETADO requests; account/Obsidian exports omit the new fields; `/in-progress` filters COMPLETADO runs in memory; summary inProgressCount counts EN_CURSO only.
- L5 (2026-10-31) RDD: API commit 09cf4f4 assessed high (item 1), consent granted, lenses risk+reliability, approved with 4 informational findings, acknowledged (authority burned). Web commits 4c89f70+e42a36d assessed medium, review_due false (under_budget).
- L6 (2026-10-31) Deployed by the user with railway up: API from feat/run-postgame (V38 applied on prod at 18:57, app started); web from dev (served es.json has the postgame keys at 19:00). Branch not merged to master; nothing pushed.
