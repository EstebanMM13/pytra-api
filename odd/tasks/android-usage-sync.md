# Android usage sync (play time from UsageStatsManager)

Objective: Android play time of linked apps reaches Pytra exactly like Steam/PSN (run in progress or online hours on "Móvil", plus a record-only diary line).
Branches: pytra-api and pytra-web `feature/android-usage-sync` (api from origin/master, web from dev). Delivery: ask-on-risk. Runners: `./mvnw test` (Spring tests need Docker, absent locally; CI runs them), `npm test` (web, vitest via ng test).

## Specs
- S1. Plugin `UsageStats` (Java, ApkInstallerPlugin pattern, registered in MainActivity): "hasPermission(): comprueba el permiso con AppOpsManager.OPSTR_GET_USAGE_STATS." "openPermissionSettings(): abre Settings.ACTION_USAGE_ACCESS_SETTINGS." "listLaunchableApps(): devuelve [{packageName, label}] con las apps que tienen launcher. Añade <queries> con intent MAIN/LAUNCHER al manifest" "getDailyUsage({from, to, packages}): devuelve [{date, packageName, minutes}]. Usa queryEvents con ACTIVITY_RESUMED/ACTIVITY_PAUSED [...] córtalo por día local (zona Europe/Madrid [...]) No uses queryUsageStats(INTERVAL_DAILY)". Manifest: `PACKAGE_USAGE_STATS` with `tools:ignore="ProtectedPermissions"`.
- S2. "En Angular, crea un wrapper TS con registerPlugin('UsageStats') que no haga nada si Capacitor.getPlatform() !== 'android'."
- S3 (changed by L2). Links reuse `game_platform_links` with platform `ANDROID`, `external_id` = package name (one package per game; one game per package and user via a partial unique index). CRUD "GET/POST/DELETE /api/v1/android-links con {packageName, gameId}. Valida que el juego pertenezca al usuario."
- S4. Baseline table `android_usage_days` (user_id, package_name, usage_date, minutes_recorded INT, PK (user_id, package_name, usage_date)): "Hay que guardar lo ya registrado y pasar solo nuevo_total - minutes_recorded cuando sea mayor que 0. Así evitas contar dos veces."
- S5 (changed by L2). "POST /api/v1/android-usage/sync con body [{date, packageName, minutes}]": unlinked packages ignored; "rechaza fechas futuras o de hace más de 14 días, y minutos fuera de 0–1440" (per item, counted as ignored, assumption A2); delta against the baseline, one transaction with row lock/upsert; "Devuelve un resumen: {recorded, ignored}". The delta goes through ExternalPlaytimeWriter like Steam/PSN: "te sume las horas a tu partida en curso y si es online que te las cuente en la plataforma de movil", and its diary line stays record-only (counts_hours = false) on the item's date. Deltas under 1 minute are ignored without moving the baseline.
- S6 (assumption A1). Days before the link's creation day only set the baseline (no hours), like Steam/PSN never importing the history before a link.
- S7. Android-linked games behave as synced games everywhere Steam/PSN ones do (diary lines of synced games are record-only; HYBRID share counts the Android minutes against runs on Móvil).
- S8. UI: an Android panel on /sync (L3) "visible solo en Android": "estado del permiso y botón para concederlo; lista de apps instaladas con un selector para vincular cada una a un juego de mi biblioteca; botón “Sincronizar ahora” y fecha de la última sincronización." Texts in es and en.
- S9. "Sincronización automática al arrancar y al volver a primer plano (App.addListener('appStateChange')), como mucho una vez cada 30 minutos. Envía los últimos 7 días de los paquetes vinculados."
- S10. Tests: "API: tests de servicio para el cálculo del delta (primera sincronización, reenvío del mismo total, total que aumenta, paquete sin vincular, fecha fuera de rango) y un test de controlador para el endpoint de sincronización y el de vínculos." "Web: test del wrapper con el plugin mockeado y del servicio de sincronización (throttle de 30 min)."
- S11. "No toques los flujos de Steam/PSN." Small commits; `./mvnw test` and `npm test` pass.

## Tasks
- [ ] T1 [S3,S4,S6,S7] API: migrations V43/V44, ANDROID platform wiring, link CRUD. Route: inline.
- [ ] T2 [S4,S5,S6,S10] API: sync endpoint + service + tests. Route: inline.
- [ ] T3 [S1] Android plugin + manifest + MainActivity. Route: inline.
- [ ] T4 [S2,S8,S9,S10] Web: wrapper, API client, sync service, /sync panel, i18n, tests. Route: inline.

## Log
- L1 (2026-11-02) User request (pasted spec, summarised in S1-S11 with verbatim quotes): Android-only automatic diary record of play time from UsageStatsManager; plugin, V43 android_app_links, V44 android_usage_days, link CRUD, sync endpoint, Ajustes screen, 30-min throttled auto sync, tests, branch feature/android-usage-sync in each repo, "No toques los flujos de Steam/PSN."
- L2 (2026-11-02) User: "yo lo que quiero esque haga lo mismo que el de psn y steam te sume las horas a tu partida en curso y si es online que te las cuente en la plataforma de movil" → accepted reusing ExternalPlaytimeWriter with a GamePlatformLink (platform ANDROID, catalog "Móvil" legacy code MOBILE) instead of a separate android_app_links table; diary line stays counts_hours = false.
- L3 (2026-11-02) No "Ajustes" screen exists; user chose "Panel en Sincronización".
- L4 Assumptions: A1 days before the link day are baseline only; A2 invalid items are ignored (counted) instead of failing the whole sync.
