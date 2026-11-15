# Run hours by period

Objective: spread a run's hours over the days it was played (diary days first, the rest across start .. credits/postgame end) instead of dropping them all into one year/month; show the run's dates as one timeline.
Branches: pytra-api `feat/run-hours-by-period`, pytra-web `feat/run-timeline`. Delivery: ask-on-risk. Runners: `./mvnw test` (api), `ng test` (web).

## Specs

- S1. "Horas por año y por mes: si tienes registros en el Diario, usarlos. Si no, repartir las horas de forma proporcional entre inicio y fin (incluyendo el postgame si lo hay), en lugar de meterlas todas en un mes." Applies to every per-year/per-month hours figure: year list totals, year page total and months chart, platform hours per year, most played of the year, genres of the year (web, via play facts).
  - Diary lines linked to the run with `countsHours` keep their own day; the rest of `run.hours` is spread evenly per day over start .. max(endDate, postgameEndDate), extended to today while the run is being played (in progress, or postgame ongoing).
  - Assumption A1: diary hours above the run's hours are scaled down to the run's hours.
  - Assumption A2: a run with only one date uses that day; a run with no dates keeps the old rule (`year` field, else no year).
- S2. "'Terminados' / GOTY: año de los créditos." Run counts, completed/abandoned, ratings, GOTY and the year page's run table keep `ExperiencePeriod.yearOf` (credits year).
- S3. "Una línea de tiempo en lugar de campos sueltos: Inicio → Créditos → Fin del postgame (opcional). En el detalle de la run, una sola tarjeta con los tres puntos y las duraciones entre ellos." It replaces the "Fechas" and "Duración" cards.
- S4. "Quitar `year` del formulario": already the case (`deriveRunYear` fills it); nothing to change.

## Tasks

- T1 [S1,S2] API: allocator + diary-per-run query + stats consumers + play facts `hoursByYear` + tests — inline — pending
- T2 [S1] Web: genres of the year use `hoursByYear` — inline — pending
- T3 [S3] Web: run detail timeline card — inline — pending

## Log

- L1 (2026-11-15) User: "vale ya que estamos, me gustaria reorganizar todo el tema de las fechas, porque me parece un poco lio, además las estadisticas donde te terminas un juego ahora que deberia coger? Porque esto es un poco abstracto no? Que opinas?" → proposal (timeline, year derived, credits year for "terminados", hours spread / diary) → "si hazlo a ver que tal".
- L2 Mapping: all per-year run hours come from `ExperiencePeriod.yearOf/monthOf` in Java (StatsServiceImpl.getByYear/getYears, YearSummaryBuilder, PlatformInsights, YearHighlightsCalculator.mostPlayed via explicit `year`, play facts RunFact). Diary lines with a run are already inside `run.hours`.
