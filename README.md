# Pytra API

Backend REST API for **Pytra**, a personal video game tracking app: games, sagas, genres, playthroughs, online playtime, aggregated stats and Steam playtime sync.

| | |
|---|---|
| Live API | https://pytra-api-production.up.railway.app |
| Web app | https://pytra.up.railway.app |
| Frontend repo | [EstebanMM13/pytra-web](https://github.com/EstebanMM13/pytra-web) (Angular web + Android app via Capacitor) |

## Quick start (local)

1. Start PostgreSQL 16 with Docker:
   ```bash
   cp .env.example .env      # then set POSTGRES_PASSWORD
   docker compose up -d
   ```
2. Add the remaining local settings to `.env` (it is loaded as a properties file on startup):
   ```properties
   PGHOST=localhost
   JWT_SECRET=<base64-encoded key, at least 256 bits>
   JWT_EXPIRATION=2592000000
   GOOGLE_CLIENT_ID=<google oauth client id>
   GOOGLE_CLIENT_SECRET=<google oauth client secret>
   ```
3. Run the app:
   ```bash
   ./mvnw spring-boot:run
   ```
   Flyway applies all migrations on startup. The API listens on `http://localhost:8080`.

> `PGHOST` defaults to a LAN address (`192.168.1.55`), not `localhost`, so set it explicitly when using the bundled `docker-compose.yml`.

Without `BREVO_API_KEY`, verification and password-reset emails are **logged instead of sent**, so you can copy the links from the console.

## Tech stack

| Area | Choice |
|---|---|
| Runtime | Java 21, Spring Boot 4.1.0 (Web MVC) |
| Persistence | Spring Data JPA, PostgreSQL 16, Flyway (`ddl-auto: validate`) |
| Security | Spring Security, stateless JWT (jjwt 0.12.6), OAuth2/OIDC login with Google |
| Integrations | Steam OpenID 2.0 (hand-written, no client library), Steam Web API, Brevo transactional email (HTTP API) |
| Validation / docs | Jakarta Bean Validation, springdoc-openapi 3.1.1 (Swagger UI, non-prod only) |
| Tests | JUnit 5, MockMvc, Testcontainers (PostgreSQL), GitHub Actions CI |

## Configuration

`application.yaml` holds local-friendly defaults. The `prod` profile (`SPRING_PROFILES_ACTIVE=prod`, file `application-prod.yaml`) removes the defaults for URL, CORS and mail settings, so the app **fails fast at startup** if any of them is missing. It also marks the session cookie `Secure`, `HttpOnly`, `SameSite=Lax` (the session only holds the pending Google authorization request), and disables the OpenAPI docs and Swagger UI.

Only variable names are listed here. Never commit real values.

| Variable | Required | Default (non-prod) | Purpose |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Prod | none | Set to `prod` in production |
| `PORT` | No | `8080` | HTTP port (set by Railway) |
| `PGHOST` / `PGPORT` | No | `192.168.1.55` / `5432` | PostgreSQL host and port |
| `POSTGRES_DB` / `POSTGRES_USER` | No | `pytra_db` / `pytra` | Database name and user |
| `POSTGRES_PASSWORD` | Yes | none | Database password |
| `JWT_SECRET` | Yes | none | Base64-encoded HMAC-SHA256 signing key |
| `JWT_EXPIRATION` | Yes | none | Token lifetime in **milliseconds** (`2592000000` = 30 days) |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Yes | none | Google OAuth2 client |
| `STEAM_API_KEY` | No | empty | Steam Web API key; without it linking still works but sync answers `503 STEAM_NOT_CONFIGURED` |
| `FRONTEND_URL` | Prod | `http://localhost:4200` | Web app origin; used for email links and the web OAuth callback |
| `PUBLIC_API_URL` | Prod | `http://localhost:8080` | Public API origin; used to build the Steam OpenID `return_to` |
| `CORS_ALLOWED_ORIGINS` | Prod | `http://localhost:4200,https://localhost` | Comma-separated allowed origins (`https://localhost` is the Capacitor Android WebView) |
| `MOBILE_REDIRECT_URI` | Prod | `com.estebanmm13.pytra://oauth-callback` | Final redirect for flows started from the Android app |
| `BREVO_API_KEY` | Prod | empty | Brevo API key; blank means emails are logged (blank is rejected in `prod`) |
| `MAIL_FROM_EMAIL` | Prod | empty | Sender address; required whenever `BREVO_API_KEY` is set |
| `MAIL_FROM_NAME` | No | `Pytra` | Sender display name |
| `DEMO_USER_ID` | No | empty | Id of the account exposed as the public read-only demo (`POST /api/v1/auth/demo`); empty disables the demo (`404`) |
| `JAVA_TOOL_OPTIONS` | No | none | JVM flags (for example a heap cap) set at the Railway service level; not read by the app itself |

**Google Cloud Console:** the authorized redirect URI must be `{PUBLIC_API_URL}/login/oauth2/code/google`.

## Auth and email

| Flow | Behavior |
|---|---|
| Register | Username 3 to 30 chars (letters, digits, `.`, `_`, `-`), stored lowercase. A verification email is sent; the link is valid for 24 h |
| Login | Identifier is an email (contains `@`) or a username. Login is refused until the email is verified |
| Resend verification | Re-sends the verification email, with a 60 s cooldown |
| Forgot / reset password | Reset link valid for 1 h |
| Google login | OIDC with `prompt=select_account` (always shows the account chooser). Only Google-verified emails are accepted; an existing account with the same email gets linked |
| Exchange code | Google and Steam flows end with a one-time code (valid 1 min) that the client exchanges at `POST /api/v1/auth/exchange-code` for a JWT |
| Rate limit | Max 5 POSTs per IP per minute on `register`, `login`, `resend-verification`, `forgot-password`, `demo`; extra requests get `429`. In-memory, per instance. Client IP = right-most `X-Forwarded-For` entry (the one Railway's proxy appends); paths are matched on the normalized servlet path, so `X-Forwarded-Prefix` or encoded URIs do not bypass it |
| Demo | `POST /api/v1/auth/demo` returns a JWT for the `DEMO_USER_ID` account with a `demo: true` claim, valid 2 h and never refreshed (the client asks again). `DemoReadOnlyFilter` answers `403 DEMO_READ_ONLY` to any non-GET/HEAD/OPTIONS request made with it (except the public `/api/v1/auth/*` endpoints, exact paths, which ignore the caller) and to `GET /users/me/export/**`. Demo sessions get a placeholder email in `GET /users/me` and no Steam id/persona in the Steam status. While enabled, the demo account is demo-only: password login, Google login, resend-verification, forgot-password and reset-password treat it as an unknown account. Unsetting `DEMO_USER_ID` also invalidates outstanding demo tokens |

Emails go through the Brevo transactional HTTP API (`api.brevo.com/v3`) rather than SMTP, because Railway blocks outbound SMTP on non-Pro plans. Email links point to `{FRONTEND_URL}/verify-email?token=...` and `{FRONTEND_URL}/reset-password?token=...`.

## Mobile (Android) OAuth flow

Google rejects OAuth inside embedded WebViews, so the Android app opens Google and Steam flows in a Custom Tab and adds `client=android`:

1. App opens `/oauth2/authorization/google?client=android` (or the Steam login URL with `client=android`).
2. The API stores the mobile flag inside the OAuth2 authorization request (Google) or the Steam-signed `return_to` (Steam).
3. On success the API redirects to `MOBILE_REDIRECT_URI?code=...`; web flows go to `{FRONTEND_URL}/oauth-callback?code=...`.
4. The app catches the deep link and exchanges the code for a JWT.

The request only selects **which** configured target is used, never the URL itself, so it cannot become an open redirect.

## Architecture

Code is organized by **module (bounded context)**, not by technical layer:

```
com.estebanmm13.pytra_api/
├── auth/         # Users, registration, login, email verification, password reset, Google OIDC, JWT, rate limiting
├── games/        # Genres (catalog), Sagas, Games, GamePlatformLink
├── experiences/  # Experience (playthroughs), OnlinePlaytime
├── steamsync/    # Steam OpenID linking, Steam Web API client, library sync
├── stats/        # Aggregated stats across games/sagas/genres/experiences
├── mail/         # EmailSender: Brevo HTTP sender or logging fallback
├── config/       # SecurityConfig, URL normalization
└── error/        # Global exception handling
```

Each module uses the same internal layout: `model/`, `dto/`, `mapper/`, `repository/`, `service/`, `controller/`.

**Key decisions:**

- Cross-module references use a plain scalar id (e.g. `Saga.userId: Long`, `Experience.gameId: Long`), never a JPA `@ManyToOne`, to keep bounded contexts decoupled at the object-graph level. Relationships inside a module (`Game → Saga`, `Game ↔ Genre`, `GamePlatformLink → Game`) use real JPA associations. Consequence: cross-module aggregates (e.g. Stats grouping Games by hours logged in Experience) are computed by fetching both sides and combining them in the service layer, not with a single JPQL join.
- Controllers resolve the authenticated user via `CurrentUserResolver` and pass `userId` explicitly into services. Services never depend on Spring Security directly, which keeps them easy to test.
- Mappers only convert `Entity → ResponseDto` (pure, no side effects). `RequestDto → Entity` construction stays in the service layer, since it usually carries business logic (password hashing, defaults, normalization).
- Every user-scoped query (e.g. `findByIdAndUserId`) filters by owner at the query level, so accessing another user's resource returns `404`, not `403`. This avoids confirming that a resource exists.
- Uniqueness that must hold under concurrent writes is enforced with database constraints (including case-insensitive functional indexes). The application-level pre-check only exists for a friendly error message.
- **Steam is account linking, not an alternative login.** `SteamLink` requires an already-authenticated Pytra user, like "Connect Steam" in account settings. `openid4java` was rejected (unmaintained since 2015); Steam's OpenID 2.0 verification is a single `check_authentication` POST, so no library is needed.
- **Steam-synced hours never guess which playthrough they belong to.** Steam reports one cumulative total per game. For `SINGLEPLAYER` games, sync maintains exactly one canonical `Experience` per game (tracked via `GamePlatformLink.experienceId`), separate from manually logged ones. For `ONLINE`/`HYBRID` games, synced hours accumulate into `OnlinePlaytime`.
- A freshly synced game starts with `category = null` and `reviewStatus = PENDING_REVIEW` until the user confirms it through the pending-review queue. Classification is manual for now.

## API overview

All endpoints except auth, the Google OAuth2 endpoints, Steam `login`/`callback` and the OpenAPI/Swagger paths require `Authorization: Bearer <jwt>`. A validly signed token whose user no longer exists (deleted account) is treated as anonymous (`401`).

Outside the `prod` profile, the OpenAPI spec is served at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`. Both are disabled in `prod` (`springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false` in `application-prod.yaml`).

### Auth (`/api/v1/auth`, public)
| Method | Endpoint | Description |
|---|---|---|
| POST | `/register` | Register a new user |
| POST | `/login` | Login with username or email + password |
| GET | `/verify-email?token=` | Verify email address |
| POST | `/resend-verification` | Re-send the verification email |
| POST | `/forgot-password` | Request a password reset |
| POST | `/reset-password` | Reset password with token |
| POST | `/exchange-code` | Exchange a one-time code (Google or Steam redirect) for a JWT |

Google login starts at `GET /oauth2/authorization/google` and returns through `/login/oauth2/code/google`.

### Current user (`/api/v1/users`)
| Method | Endpoint | Description |
|---|---|---|
| GET | `/me` | Profile: username, display name, email, whether a password is set, whether Google is linked, `createdAt` |
| PATCH | `/me` | Change username |
| GET | `/me/export?format=csv\|markdown` | Download all the user's data. CSV: one row per experience with the game columns repeated (games without runs get one row), UTF-8 with BOM, formula-like text (first non-blank character `= + - @`, or a leading control character) prefixed with `'`. Markdown (Obsidian-friendly): front matter (quoted YAML values), one `##` section per game with its `###` runs, then the yearly notes. Unknown format: `400 INVALID_EXPORT_FORMAT`. Max 5 exports per user per minute (in-memory): `429 EXPORT_RATE_LIMITED` |
| DELETE | `/me` | Body `{"confirm": "<username>", "password": "..."}`. `confirm` must match the username (case-insensitive, else `400 CONFIRMATION_MISMATCH`). Accounts with a password must send it (`400 INVALID_PASSWORD` if missing or wrong; never 401 so clients don't log out); Google-only accounts need a token issued in the last 10 minutes, else `403 REAUTH_REQUIRED` (log in with Google again). Deletes the user and everything they own in one transaction (all user tables cascade from `users`); `204`. Blocked with `409 SYNC_IN_PROGRESS` while a Steam sync runs |

### Genres (`/api/v1/genres`, global catalog)
| Method | Endpoint | Description |
|---|---|---|
| GET | `/` | List all genres |
| POST | `/` | Create a genre if it does not exist yet |

### Sagas (`/api/v1/sagas`)
| Method | Endpoint | Description |
|---|---|---|
| GET | `/` | List the current user's sagas |
| GET | `/{id}` | Get a saga |
| POST | `/` | Create a saga |
| PUT | `/{id}` | Update a saga |
| DELETE | `/{id}` | Delete a saga (its games remain, saga reference is cleared) |

### Games (`/api/v1/games`)
| Method | Endpoint | Description |
|---|---|---|
| GET | `/` | List the current user's games, with library aggregates: `experienceCount`, `totalHours`, `bestRating`, `lastExperienceStatus`, `lastPlayedYear`, `hasPlatinum`, `platforms` (distinct, enum order), `lastPlayedAt` (latest experience `endDate`/`startDate`, nullable date) (two queries in total, no N+1) |
| GET | `/{id}` | Get a game (same aggregates) |
| POST | `/` | Create a game (optionally linked to a saga and/or genres) |
| PUT | `/{id}` | Update a game |
| DELETE | `/{id}` | Delete a game |

### Experiences (playthroughs)
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/v1/games/{gameId}/experiences` | List playthroughs of a game |
| POST | `/api/v1/games/{gameId}/experiences` | Log a playthrough |
| GET | `/api/v1/experiences/{id}` | Get a playthrough |
| PUT | `/api/v1/experiences/{id}` | Update a playthrough |
| DELETE | `/api/v1/experiences/{id}` | Delete a playthrough |

Ratings (`rating` here, `generalRating` in online playtime) are `0`–`10` with up to 2 decimals (e.g. `9.25`), stored as `NUMERIC(4,2)`. Values out of range or with more decimals (`9.255`) are rejected with `400` (never rounded). Responses return them as JSON numbers without trailing zeros (`9`, `9.5`, `9.25`). Jackson's float-to-int coercion is disabled globally (`accept-float-as-int: false`), so a decimal sent to an integer field (e.g. `"year": 2024.5`) fails with `400` instead of being silently truncated.

### Online playtime (`/api/v1/games/{gameId}/online-playtime`)
| Method | Endpoint | Description |
|---|---|---|
| GET | `/` | Get tracked online playtime |
| PUT | `/` | Upsert online playtime |

### Stats (`/api/v1/stats`)
| Method | Endpoint | Description |
|---|---|---|
| GET | `/summary` | Totals: games, sagas, experiences, hours (singleplayer/online separate), platinums, `averageRating`, `replayCount`, `completedCount`, `abandonedCount`, `inProgressCount` |
| GET | `/by-year` | Hours, experience count and `averageRating` per year |
| GET | `/years` | Years with data, newest first (`[2025, 2024]`) |
| GET | `/years/{year}` | Year in review: totals, `months` (always 12), `hoursWithoutMonth`, the year's runs by rating, automatic highlights (`goty`, `topRated`, `mostPlayed`, `topSagas`, `topGenres`, `surprises`, `disappointments`) and `note` |
| PUT | `/years/{year}/note` | Upsert the yearly free-text note `{summary, highlights}` (max 10 000 chars each, blank = null) |
| GET | `/in-progress` | `EN_CURSO` runs across all games, most recently started first |
| GET | `/by-saga` | Hours and game count per saga (includes a "no saga" bucket) |
| GET | `/by-genre` | Hours and game count per genre |
| GET | `/top-rated?limit=` | Highest-rated experiences |
| GET | `/most-played/singleplayer?limit=` | Games ranked by `Experience` hours |
| GET | `/most-played/online?limit=` | Games ranked by `OnlinePlaytime` hours (never merged with singleplayer) |

Averages are over rated experiences only, computed exactly (`BigDecimal`) and rounded to 2 decimals half up (`[9.25, 8.5]` → `8.88`), `null` when nothing is rated. Highlight thresholds are inclusive and applied to the rounded average (surprise `>= 8.5`, disappointment `<= 6`). Year endpoints accept 1970 to next year (`400 INVALID_YEAR` otherwise).

Year attribution (`ExperiencePeriod`, used by `/by-year`, `/years`, year totals, months and library aggregates): the explicit `year` field, else the year of `endDate`, else of `startDate`; runs with none are left out. Month: `endDate` if it falls in that year, else `startDate` if it does, else no month (counted in `hoursWithoutMonth`).

Highlights (`YearHighlightsCalculator`) use two different attributions:
- **Completed in year**: `COMPLETADO` with `endDate` in the year. Feeds `goty` (best average per game), `topRated` (top 5), `topSagas` / `topGenres` (top 5 by run count; games without a saga are skipped, a run counts once per genre of its game), `surprises` (average >= 8.5, max 3, excluding the goty) and `disappointments` (average <= 6, worst first, max 3).
- **Played in year**: `year` field equals the year, any status. Feeds `mostPlayed` (top 5 by hours).
- Ties: more hours first, then game name.

### Steam (`/api/v1/integrations/steam`)
| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/connect-token` | Required | Issues a one-time state token (valid 10 min) for the current user |
| GET | `/login?state=` | Public | Full-page redirect to Steam OpenID; identity travels in `state` |
| GET | `/callback` | Public | Verifies Steam's response, upserts `SteamLink`, ALWAYS redirects to the client callback: `?code=…&next=steam` or `?error=steam_link_failed\|steam_account_already_linked&next=steam` |
| GET | `/status` | Required | `{linked, steamId, personaName, lastSyncAt, configured, linkedGamesCount, pendingCount, ignoredCount}` (`linkedGamesCount` = STEAM links, pending placeholders included) |
| DELETE | `/link` | Required | Unlinks (`204`). Confirmed games keep their hours; pending Steam placeholders and the ignore list are deleted |
| POST | `/sync` | Required | Pulls the Steam library → `{gamesScanned, newGamesPending, linkedExisting, gamesUpdated, ignored, skipped, errored, profilePrivate}` |
| GET | `/pending` | Required | Games discovered by sync awaiting review: game fields plus `appId`, `steamPlaytimeMinutes` (latest Steam total) and `lastPlayedAt` (Steam `rtime_last_played` as an ISO instant, null if never played) |
| PUT | `/pending/{gameId}/confirm` | Required | Confirms category/saga/genres and imports the Steam total |
| PUT | `/pending/{gameId}/ignore` | Required | Deletes the placeholder and never re-imports that app (`204`) |
| GET | `/ignored` | Required | Ignored apps `{appId, name, ignoredAt}` |
| DELETE | `/ignored/{appId}` | Required | Un-ignores; the app comes back as pending on the next sync (`204`) |

Errors carry a stable code in `message`: `503 STEAM_NOT_CONFIGURED` (blank `STEAM_API_KEY`), `404 STEAM_NOT_LINKED`, `409 SYNC_IN_PROGRESS`, `409 STEAM_LINK_CHANGED`, `429 STEAM_RATE_LIMITED`, `502 STEAM_API_KEY_REJECTED`, `503 STEAM_UNAVAILABLE`.

Sync rules:
- The Steam HTTP call (5 s connect / 15 s read timeout) runs outside any DB transaction; already-linked apps are then updated in one transaction and each new app is attached/created in its own, so one failing app is counted as `errored` without aborting the rest.
- An app with no link whose name matches an existing game (case-insensitive) is attached to it with the current Steam playtime as baseline: those hours were already logged by hand, only later deltas are added.
- Confirming an `ONLINE`/`HYBRID` game sets `OnlinePlaytime` to `max(existing, steamTotal)`; later syncs add deltas. A negative delta (refund, reset) only lowers the baseline.
- One Steam account per Pytra user (`steam_links.steam_id` is unique). Re-linking a different account resets all Steam baselines.
- `SteamSyncScheduler` re-syncs every linked account every 6 hours (first run 10 min after startup) and is skipped when `STEAM_API_KEY` is blank (linking still works). A run aborts after 3 consecutive `STEAM_UNAVAILABLE`. A per-user in-memory guard (single-instance deployment), held until commit, serializes sync, unlink, account switch, confirm and ignore (`409 SYNC_IN_PROGRESS`; a blocked account switch redirects with `error=steam_sync_in_progress`). `last_sync_at` is written with a conditional UPDATE on the expected `steam_id`, so a relink during the Steam call yields `STEAM_LINK_CHANGED` instead of being reverted.

## Tests

| Command | Needs Docker | What runs |
|---|---|---|
| `./mvnw test` | Yes | Everything, including the integration tests below |
| `./mvnw test -Dtest="RegistrationPolicyTest,UsernamePolicyTest,AuthRateLimitFilterTest,AuthEmailServiceTest,BrevoEmailSenderTest,SteamWebApiClientTest,SteamSyncSchedulerTest,ExperiencePeriodTest,StatsMathTest,YearHighlightsCalculatorTest,AccountExportWriterTest,ExportRateLimiterTest,DemoReadOnlyFilterTest"` | No | Unit tests only |

Integration tests extend `AbstractIntegrationTest`: full Spring context, MockMvc, the `test` profile (`src/test/resources/application-test.yaml`, which supplies dummy JWT/Google/mail settings so no environment variable is needed) and one shared PostgreSQL Testcontainer via `@ServiceConnection`.

- `TenantIsolationTests` proves that one user can never read, modify or delete another user's games, sagas, experiences, online playtime, stats, profile or Steam pending queue (cross-user access is always `404`), and that every protected endpoint answers `401` without a valid token.
- `SteamIntegrationTests` covers the Steam sync with the HTTP client mocked: pending creation, same-name linking, per-app failure isolation, private profiles, negative deltas, ignore/unignore, status, unlink/relink and cross-user isolation.
- `StatsIntegrationTests`, `GameLibraryAggregatesTests` and `AccountIntegrationTests` cover averages and status counts, year/month attribution, highlights, yearly notes, in-progress runs, library aggregates, both export formats and account deletion (every user-owned table emptied, other users untouched).
- `DemoModeIntegrationTests` covers the read-only demo: `404` when disabled, demo token reads the demo account, every write method gets `403 DEMO_READ_ONLY`, normal tokens unaffected.
- `ApiDocsTests` checks that `/v3/api-docs` is served outside `prod`.
- `PytraApiApplicationTests` is the context-load smoke test.

**CI:** `.github/workflows/ci.yml` runs `./mvnw -B verify` (JDK 21 Temurin, Maven cache) on every push and pull request to `master`. GitHub's `ubuntu-latest` runners have Docker, so the Testcontainers tests run there even when they can't run locally.

## Deployment (Railway)

Production runs on Railway as three services: `pytra-api` (this repo), `pytra-web` and PostgreSQL. There is no GitHub auto-deploy; deploys are manual from the repo directory with the Railway CLI:

```bash
railway up --service pytra-api --environment production --detach
```

There is no Dockerfile in this repo; Railway builds the Maven project directly. Before deploying, make sure the service has `SPRING_PROFILES_ACTIVE=prod` and every variable marked **Prod** or **Yes** in the configuration table. `server.forward-headers-strategy: framework` makes generated URLs (such as the OAuth2 redirect URI) use the public HTTPS host behind Railway's proxy.

## Roadmap

- [x] Auth: register, login, email verification, resend verification, password reset
- [x] Auth: Google OAuth2/OIDC login (web and Android)
- [x] Transactional email via Brevo
- [x] Per-IP rate limiting on public auth endpoints
- [x] Current-user profile and username change
- [x] Genres, Sagas, Games, Experiences, OnlinePlaytime
- [x] Aggregated stats
- [x] Steam account linking, library sync and pending-review queue
- [x] Frontend ([pytra-web](https://github.com/EstebanMM13/pytra-web)) and production deployment on Railway
- [ ] Broader automated test suite (integration and multi-tenancy isolation tests)
- [ ] CI/CD
