# Year: played vs credits vs achievements

Objective: every year-scoped stat distinguishes "played in the year" (any day of the run, postgame included), "finished in the year" (credits) and "achievement year" (platinum / 100%).
Branches: pytra-api and pytra-web `feat/year-played-vs-credits`. Delivery: ask-on-risk. Runners: `./mvnw test` (api), `ng test` (web).

## Specs

- S1. "me pasé Silksong en 2025 los creditos pero el 100% lo hice en 2026, y solo sale en la pantalla de años de 2025". Years › "Partidas del año" lists every run played in the year (hours in the year > 0, or credits year), each with its role in that year ("Créditos" / "Abandonada" / "Postgame" / "En curso") and its hours in that year.
- S2. Years › "Partidas" and by-year `experienceCount` count the runs played in the year.
- S3. Completed, abandoned, average rating and the highlights (GOTY, top rated, surprises, disappointments, sagas, genres) keep the credits year.
- S4. "usa el fin dle postgame y ya": platinum and 100% count in the year of `postgameEndDate`, else the run's credits year (`ExperiencePeriod.yearOf`). The year's table shows the platinum / 100% marks only in that year. Years shows a "100%" card next to "Platinos".
- S5. The "No hay partidas registradas" empty state shows only when the year has no runs and no hours.
- S6. Library year filter matches the credits year or any year the run was played (`hoursByYear`).
- S7. Library "last played" takes the postgame end into account.
- S8. Not changed: completion days (start → credits), ratings scope, activity, rankings.

## Tasks

- T1 [S1-S4,S7] API: year summary rows/counts, by-year count, achievement year, lastPlayedAt + tests — inline — pending
- T2 [S1,S4,S5,S6] Web: year table role + year hours, 100% card, empty state, library filter + tests — inline — pending

## Log

- L1 (2026-11-15) User: "vale ahora quiero que revises todas las fichas de estadisticas que utilizan fechas, la de años, etc etc. Porque ahora que se ha añadido la fecha del postgame queda todo un poco cojo. O por lo menos hacer la distincion, por ejemplo me pasé Silksong en 2025 los creditos pero el 100% lo hice en 2026, y solo sale en la pantalla de años de 2025 entiendes? Hay que echarle un ojo a todas esas tarjetas para ver que se puede hacer" → audit + proposal table → "usa el fin dle postgame y ya".
