# UI buttons and type scale

Objective: shared button classes and a fixed type scale in the web, normalizing the near-duplicate sizes (small visible changes accepted).
Branch: pytra-web `refactor/ui-buttons-type` (from `fix/visual-tweaks`). Delivery: ask-on-risk. Runner: `npx ng test --watch=false` (pytra-web).

## Specs

- S1 Buttons. User chose "Normalizar" ("si normaliza a ver"): shared `.btn` classes in `src/styles.css` (`@layer components`, so utilities still override) with variants `btn-primary` and `btn-secondary` and sizes `btn-sm` (7px vertical padding, 12px horizontal) and `btn-md` (9px vertical, 14px horizontal); 13px text, radius 8px, press feedback, disabled at 50% opacity. Primary: brand background, white text, semibold, hover brand-dark. Secondary: border, text-2, medium, hover border-strong. Every rounded-lg 13px primary/secondary button migrates; vertical padding 6-7px becomes sm, 8-10px becomes md. Pills (rounded-full), icon buttons, the mobile + button and the large full-width form/auth buttons stay as they are.
- S2 Type scale. Same decision: named font-size tokens in `@theme` (no line-height, so line heights do not change) and every arbitrary `text-[Npx]` migrates to the nearest step: micro 10, caption 11, label 13, body 15, lead 16 (16, 17), title 22 (19, 21, 22), stat 28 (26, 28), h1 30, hero 36 (34, 36), display 40 (40, 42, 44), jumbo 48. 14 becomes token `ui` (14). Outliers kept on purpose: the 8px tiny-cover initials, the 52px auth wordmark and the 64px years hero.

## Tasks

- [x] T1 S1 button classes + migration — inline — web 5685440
- [x] T2 S2 type tokens + migration — inline — web 9d30319

## Log

- L1 (2026-11-04) User: "me puedes hacer un analisis de toda la app y me dices que se puede mejorar, tanto en la api como visualmente" -> audit -> API security fixed, web-deploy removed -> "si sigue" -> asked normalize vs pixel-identical -> "si normaliza a ver".
- L2 (2026-11-04) T1: 38 buttons migrated by script (navbar fake search box excluded: left-aligned input look). T2: 372 sizes migrated; 8px kept (tiny cover initials would not fit 10px). Built CSS checked: btn classes present, text tokens emit font-size only (line heights unchanged), sm/md/lg variants generated. Web suite 124 files / 1217 tests pass after each task; ng build clean, initial 424.06 kB. Not visually inspected in a browser yet.
- L3 (2026-11-04) Independent verifier (Risk item 6, visual regressions): S1 and S2 pass, no blockers; the missed filters "Done" button was migrated (web 8d7eb0b), suite 1217/1217 again. Native review: the user granted it, but START refused with `lens_context_budget_exceeded` (66 files, 804 lines are too big for one candidate); no authority was created. Pending: user decision on splitting for review, and a visual check in the browser.
- L4 (2026-11-04) User on the native review: "dejarlo así". No native review for this candidate; independent verification stands. Feature closed pending the browser visual check and push (user decisions).
