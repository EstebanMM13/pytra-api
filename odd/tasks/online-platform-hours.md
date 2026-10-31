# Online hours per platform

Objective: let the user split a game's online hours across its online platforms, and use that split in the platform stats.
Branches: pytra-api `feat/online-platform-hours`, pytra-web `dev`. Delivery: ask-on-risk. Runners: `./mvnw test` (api), `ng test` (web).

## Specs

- S1. "en la sección Online de un juego si hay mas de una plataforma que el usuario ponga las horas para cada plataforma y entonces ya lo otro no tiene que calcular tanto"
- S2. Model chosen by the user: "Desglose del total" — the total stays the source of truth; diary and sync keep adding to the total; in the Online form, with more than one platform, the user splits hours across them; whatever is left unsplit shows in Stats as "sin asignar".
- S3. Platform stats use the split instead of the even split: "si yo tengo 2000h en gta online y he jugado en pc, ps4 y ps5 me coge esas 2000h para cada plataforma) hay que separarlo".
- Assumption A1: games with several platforms and no split entered keep the current even split (existing data is not turned into "sin asignar").
- Assumption A2: the split cannot exceed the total (400 `PLATFORM_HOURS_EXCEED_TOTAL`); if a later diary/sync change lowers the total below the split, stats scale the split down proportionally.
- Assumption A3: year-scoped stats apply the same proportions to that year's diary online hours.

## Tasks

- T1 [S1,S2] API: migration + entity map + request/response field + validation + tests — inline — done (RED compile, GREEN 15/15) — f6eb5f8
- T2 [S3] API: platform stats use the split (all-time and per year) + tests — inline — done (RED 2 failures, GREEN 13/13) — f6eb5f8
- T3 [S1,S2] Web: per-platform hour inputs in the Online card, model, request, i18n, tests — inline — done (RED compile, GREEN 1096/1096) — pytra-web c85b55d

Route evidence: no Writer trigger (units depend on each other: web depends on the API contract); parent writes inline. Risk: item 1 (new table via migration).

## Log

- L1 (2026-10-31) User: "veo varios problemas de diseño, en Inicio me pone que horas de un jugador tengo 2866 pero en la cabecera de Estadisticas son 2854 porque? Ademas he visto que en las estadisictas de Plataforma en las horas para cada plataforma coge las horas totales de un juego (por ejemplo si yo tengo 2000h en gta online y he jugado en pc, ps4 y ps5 me coge esas 2000h para cada plataforma) hay que separarlo"
- L2 (2026-10-31) Finding: `splitOnline` already splits evenly across the online platforms; the even split is what gets replaced.
- L3 (2026-10-31) User: "lo que hay que hacer es en la sección Online de un juego si hay mas de una plataforma que el usuario ponga las horas para cada plataforma y entonces ya lo otro no tiene que calcular tanto". Decision: "Desglose del total (Recomendado)".
- L4 (2026-10-31) Verification: API unit tests pass; 325 integration tests error because Docker is not running (Testcontainers), so V35 and the ddl-auto validate of the new map are not yet checked against Postgres. Web suite 118 files / 1096 tests pass. Next: start Docker and run `./mvnw test` in pytra-api; pytra-web-deploy not updated.
