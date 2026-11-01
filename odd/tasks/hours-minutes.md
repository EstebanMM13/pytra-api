# Hours as hours and minutes

Objective: show and enter play time as hours and minutes instead of decimal hours (web only; the API keeps decimal hours).
Branch: pytra-web `dev`. Delivery: ask-on-risk. Runner: `npx ng test --watch=false` (pytra-web).

## Specs

- S1 Display: "quiero que lo de las horas se ponga en h y min". Every hour count the web shows (the shared `formatHours` / `hours` pipe) reads as hours and minutes: 0.77 -> "46 min", 12 -> "12 h", 12.5 -> "12 h 30 min", 1.99 -> "1 h 59 min". Minutes are the decimal hours times 60, rounded to the nearest minute (59.6 min -> "1 h"). 0 or null -> "0 h".
- S2 Assumption (keeps small cards readable): from 100 h on, whole hours only, rounded ("152 h", "116 h" for 116.4); from 1000 h on, grouped as today ("3.749 h" es / "3,749 h" en).
- S3 Entry: every field where the user types hours (run form hours, diary day lines, online playtime total hours, and any other hours input found) takes hours and minutes (two fields "h" and "min", min 0-59) and stores decimal hours = h + min/60 rounded to 2 decimals, so the API contract is unchanged. Existing values load split back into h and min (0.77 -> 0 h 46 min).
- S4 Diary quick buttons keep their effect and read "+30 min" and "+1 h".
- S5 Help text and docs that describe hours in decimals are updated.

## Tasks

- [ ] T1 S1-S5 hours formatter, hours inputs, specs — delegated (Writer trigger: parallel with achievements-unlocked T1, disjoint surfaces; long-session backstop)

## Log

- L1 (2026-11-01) User: "oye porque suma 0,77 horas? no deberia pasar a 1h cuando llegues a 0,6?" -> explained decimal hours (minutes / 60) -> "vale quiero que lo de las horas se ponga en h y min."
