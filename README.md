# Pytra API

Backend REST API for **Pytra** ("Play, Track, Remember"), a personal video game tracker: games, sagas, genres, playthroughs (runs), online playtime, aggregated stats and a year in review, Steam and PlayStation Network library sync with automatic IGDB enrichment of imported games, and a read-only demo account.

| | |
|---|---|
| Live API | https://pytra-api-production.up.railway.app |
| Web app | https://pytra.up.railway.app |
| Frontend repo | [EstebanMM13/pytra-web](https://github.com/EstebanMM13/pytra-web) (Angular web + Android app via Capacitor) |

## Quick start (local)

1. Start PostgreSQL with Docker (the bundled `docker-compose.yml` runs `postgres:16-alpine`):
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

Without `BREVO_API_KEY`, verification and password-reset emails are **logged instead of sent**, so you can copy the links from the console. Steam, PSN and IGDB are optional: leave their keys empty and those features report themselves as not configured.

## Tech stack

| Area | Choice |
|---|---|
| Runtime | Java 21, Spring Boot 4.1.0 (Web MVC) |
| Persistence | Spring Data JPA, PostgreSQL 18 (Railway), Flyway migrations `V1`–`V28` (`ddl-auto: validate`) |
| Security | Spring Security, stateless JWT (jjwt 0.12.6), OAuth2/OIDC login with Google |
| Integrations | Steam OpenID 2.0 (hand-written, no client library), Steam Web API and store, PlayStation Network (unofficial mobile API, one service account), IGDB (Twitch app credentials), Brevo transactional email (HTTP API) |
| Validation / docs | Jakarta Bean Validation, springdoc-openapi 3.1.1 (Swagger UI in dev, disabled in `prod`) |
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
| `PSN_NPSSO` | No | empty | NPSSO cookie (64 chars) of Pytra's PSN service account, used to read users' public game lists; empty disables PSN (`configured: false`, link/sync answer `503 PSN_NOT_CONFIGURED`). Expires after about 2 months: see [Renewing `PSN_NPSSO`](#renewing-psn_npsso) |
| `PSN_SCHEDULER_PAUSE` | No | `3s` | Pause between two accounts in the scheduled PSN sync (Spring duration syntax) |
| `TWITCH_CLIENT_ID` / `TWITCH_CLIENT_SECRET` | No | empty | Twitch app credentials for IGDB metadata autofill (`app.igdb.*`); if either is blank IGDB is disabled: `/metadata/providers` reports `igdb: false` and `/metadata/igdb/**` answers `404 IGDB_DISABLED` |
| `IGDB_PENDING_ENRICHMENT_ENABLED` | No | `true` | Background IGDB fill of the pending games Steam/PSN syncs create (`app.igdb.pending-enrichment.enabled`); only runs when IGDB is enabled |
| `FRONTEND_URL` | Prod | `http://localhost:4200` | Web app origin; used for email links and the web OAuth callback |
| `PUBLIC_API_URL` | Prod | `http://localhost:8080` | Public API origin; used to build the Steam OpenID `return_to` |
| `CORS_ALLOWED_ORIGINS` | Prod | `http://localhost:4200,https://localhost` | Comma-separated allowed origins (`https://localhost` is the Capacitor Android WebView) |
| `MOBILE_REDIRECT_URI` | Prod | `com.estebanmm13.pytra://oauth-callback` | Final redirect for flows started from the Android app |
| `BREVO_API_KEY` | Prod | empty | Brevo API key; blank means emails are logged (blank is rejected in `prod`) |
| `MAIL_FROM_EMAIL` | Prod | empty | Sender address; required whenever `BREVO_API_KEY` is set |
| `MAIL_FROM_NAME` | No | `Pytra` | Sender display name |
| `ALLOWED_EMAILS` | No | empty | Invite-only registration: comma-separated emails allowed to create an account (password or Google). Empty = open registration. Existing users can always sign in |
| `EXCLUSIVE_AVATAR_OWNERS` | No | empty | Single-owner avatar presets as `key:userId` pairs (bound by id, since usernames can change). Empty = nobody may pick them |
| `DEMO_USER_ID` | No | empty | Id of the account exposed as the public read-only demo (`POST /api/v1/auth/demo`); empty disables the demo (`404`) |
| `PLATFORMS_MAX_TOTAL` | No | `200` | Cap on the global platform catalog (seeded entries included); new entries beyond it get `409 PLATFORM_CATALOG_FULL` |
| `PLATFORMS_CREATE_MAX_PER_WINDOW` | No | `20` | New platforms each user may create per window; extra creations get `429 PLATFORM_RATE_LIMITED` |
| `PLATFORMS_CREATE_WINDOW` | No | `1h` | Window of that per-user limit (Spring duration syntax: `1h`, `30m`, ...) |
| `JAVA_TOOL_OPTIONS` | No | none | JVM flags set at the Railway service level; not read by the app itself. Recommended: `-Xmx256m -Xss512k -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC -XX:TieredStopAtLevel=1`. See [Runtime sizing](#runtime-sizing) |

**Google Cloud Console:** the authorized redirect URI must be `{PUBLIC_API_URL}/login/oauth2/code/google`.

### Runtime sizing

The service runs in a small single-CPU container next to a shared Postgres, so `application.yaml` sizes it down on purpose:

| Setting | Value | Why |
|---|---|---|
| Hikari `maximum-pool-size` / `minimum-idle` | `5` / `5` | Hikari's default keeps 10 connections open; a fixed pool stays warm, so a burst of parallel requests never waits for connections opened one at a time |
| Hikari `max-lifetime` | 15 min | Recycle connections before any proxy idle cut |
| Tomcat `threads.max` | `30` | More threads than pool connections would only queue on the pool |
| Response compression | On, from 1 KB | Library and stats lists are JSON-heavy |

Recommended `JAVA_TOOL_OPTIONS` on the Railway service:

```
-Xmx256m -Xss512k -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC -XX:TieredStopAtLevel=1
```

Heap sized from the container limit, a low-overhead GC for one CPU, smaller thread stacks, and exit on OOM so Railway restarts the service instead of leaving it half-alive.

### Scheduled jobs

| Job | Schedule | Skipped when |
|---|---|---|
| `SteamSyncScheduler` | Every 6 h (first run 10 min after startup) | `STEAM_API_KEY` is blank |
| `PsnSyncScheduler` | Every 12 h (first run 20 min after startup) | `PSN_NPSSO` is blank |
| `ExpiredTokenPurgeScheduler` | Daily (first run 30 min after startup): deletes expired or consumed email-verification, password-reset and exchange-code tokens, each table in its own transaction | Never |
| IGDB pending enrichment | Queued after every Steam/PSN import (not a timer) | IGDB disabled, or `IGDB_PENDING_ENRICHMENT_ENABLED=false` |

All jobs run in memory and assume a single instance.

## Auth and email

| Flow | Behavior |
|---|---|
| Register | Username 3 to 30 chars (letters, digits, `.`, `_`, `-`), stored lowercase. A verification email is sent; the link is valid for 24 h. When `ALLOWED_EMAILS` is set, only those emails can create an account (Google sign-up too) |
| Login | Identifier is an email (contains `@`) or a username. Login is refused until the email is verified |
| Resend verification | Re-sends the verification email, with a 60 s cooldown |
| Forgot / reset password | Reset link valid for 1 h |
| Google login | OIDC with `prompt=select_account` (always shows the account chooser). Only Google-verified emails are accepted; an existing account with the same email gets linked |
| Exchange code | Google and Steam flows end with a one-time code (valid 1 min) that the client exchanges at `POST /api/v1/auth/exchange-code` for a JWT |
| Rate limit | Max 5 POSTs per IP per minute on `register`, `login`, `resend-verification`, `forgot-password`, `demo`; extra requests get `429`. In-memory, per instance. Client IP = right-most `X-Forwarded-For` entry (the one Railway's proxy appends); paths are matched on the normalized servlet path, so `X-Forwarded-Prefix` or encoded URIs do not bypass it |
| Platform creation | `POST /api/v1/platforms` (create-if-missing on the global catalog) is limited per user to `PLATFORMS_CREATE_MAX_PER_WINDOW` new entries per `PLATFORMS_CREATE_WINDOW` (`429 PLATFORM_RATE_LIMITED`, in-memory, per instance) and the catalog is capped at `PLATFORMS_MAX_TOTAL` entries (`409 PLATFORM_CATALOG_FULL`). Requests that resolve to an existing entry (case-insensitive name match) count toward neither |
| Demo | `POST /api/v1/auth/demo` returns a JWT for the `DEMO_USER_ID` account with a `demo: true` claim, valid 2 h and never refreshed (the client asks again). `DemoReadOnlyFilter` answers `403 DEMO_READ_ONLY` to any non-GET/HEAD/OPTIONS request made with it (except the public `/api/v1/auth/*` endpoints, exact paths, which ignore the caller) and to `GET /users/me/export/**`. Demo sessions get a placeholder email in `GET /users/me`, no Steam id/persona in the Steam status and no PSN account id/online ID in the PSN status. While enabled, the demo account is demo-only: password login, Google login, resend-verification, forgot-password and reset-password treat it as an unknown account. Unsetting `DEMO_USER_ID` also invalidates outstanding demo tokens |

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
├── auth/         # Users, registration, login, email verification, password reset, Google OIDC, JWT, rate limiting, demo, token purge
├── account/      # Data export (CSV / Markdown) and account deletion
├── games/        # Genres (catalog), Sagas, Games, GamePlatformLink
├── experiences/  # Experience (playthroughs), OnlinePlaytime
├── platforms/    # Platform catalog (global, editable)
├── steamsync/    # Steam OpenID linking, Steam Web API client, library sync
├── psnsync/      # PSN online-ID linking, service-account auth, PS4/PS5 game list sync
├── platformsync/ # Shared by both syncs: playtime writer, pending queue, pending confirm
├── metadata/     # IGDB and Steam store lookups: form autofill and pending-game enrichment
├── covers/       # Downscaled Steam cover thumbnails for list views
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
- **Tenant isolation:** every user-scoped query (e.g. `findByIdAndUserId`) filters by owner at the query level, so accessing another user's resource returns `404`, not `403`. This avoids confirming that a resource exists.
- Uniqueness that must hold under concurrent writes is enforced with database constraints (including case-insensitive functional indexes). The application-level pre-check only exists for a friendly error message.
- **Steam is account linking, not an alternative login.** `SteamLink` requires an already-authenticated Pytra user, like "Connect Steam" in account settings. `openid4java` was rejected (unmaintained since 2015); Steam's OpenID 2.0 verification is a single `check_authentication` POST, so no library is needed.
- **Steam-synced hours never guess which playthrough they belong to.** Steam reports one cumulative total per game. For `SINGLEPLAYER` games, sync maintains exactly one canonical `Experience` per game (tracked via `GamePlatformLink.experienceId`), separate from manually logged ones. For `ONLINE`/`HYBRID` games, synced hours accumulate into `OnlinePlaytime`.
- A freshly synced game starts with `reviewStatus = PENDING_REVIEW` until the user confirms it through the pending-review queue (the web app opens the full game form for this). IGDB enrichment may suggest a category, but only confirmation moves the game into the library.

## API overview

All endpoints except auth, the Google OAuth2 endpoints, Steam `login`/`callback`, `GET /api/v1/covers/thumb` and the OpenAPI/Swagger paths require `Authorization: Bearer <jwt>`. A validly signed token whose user no longer exists (deleted account) is treated as anonymous (`401`).

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
| POST | `/demo` | JWT for the read-only demo account (see [Auth and email](#auth-and-email)) |

Google login starts at `GET /oauth2/authorization/google` and returns through `/login/oauth2/code/google`.

### Current user (`/api/v1/users`)
| Method | Endpoint | Description |
|---|---|---|
| GET | `/me` | Profile: username, display name, email, avatar, whether a password is set, whether Google is linked, `createdAt` |
| PATCH | `/me` | Partial update: `username` and/or `avatar` (preset key; explicit `null` clears it) |
| GET | `/me/export?format=csv\|markdown` | Download all the user's data. CSV: one row per experience with the game columns repeated (games without runs get one row), UTF-8 with BOM, formula-like text (first non-blank character `= + - @`, or a leading control character) prefixed with `'`. Markdown (Obsidian-friendly): front matter (quoted YAML values), one `##` section per game with its `###` runs, then the yearly notes. Unknown format: `400 INVALID_EXPORT_FORMAT`. Max 5 exports per user per minute (in-memory): `429 EXPORT_RATE_LIMITED` |
| DELETE | `/me` | Body `{"confirm": "<username>", "password": "..."}`. `confirm` must match the username (case-insensitive, else `400 CONFIRMATION_MISMATCH`). Accounts with a password must send it (`400 INVALID_PASSWORD` if missing or wrong; never 401 so clients don't log out); Google-only accounts need a token issued in the last 10 minutes, else `403 REAUTH_REQUIRED` (log in with Google again). Deletes the user and everything they own in one transaction (all user tables cascade from `users`); `204`. Blocked with `409 SYNC_IN_PROGRESS` while a Steam or PSN sync runs |

### Genres (`/api/v1/genres`, global catalog)
| Method | Endpoint | Description |
|---|---|---|
| GET | `/` | List all genres |
| POST | `/` | Create a genre if it does not exist yet |

### Platforms (`/api/v1/platforms`, global catalog)
| Method | Endpoint | Description |
|---|---|---|
| GET | `/` | List all platforms ordered by `sortOrder`, then name: `{id, name, color, family, legacyCode, sortOrder}` |
| POST | `/` | Body `{name, family?, color?}`. Create-if-missing: `201` when created, `200` with the existing entry when the trimmed name matches case-insensitively. `name` 1–40 chars; `family` one of `PLAYSTATION`, `XBOX`, `NINTENDO`, `PC`, `MOBILE`, `OTHER` (default `OTHER`); `color` `#RRGGBB` (default: the family color) |

Seeded with the common platforms (PC, PlayStation, Xbox, Nintendo, Móvil, Retro / Emulador). The six values of the old fixed enum keep a `legacyCode` (`PC`, `PS5`, `PS4`, `XBOX` → generic "Xbox", `SWITCH`, `MOBILE`).

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
| GET | `/` | List the current user's games, with library aggregates: `experienceCount`, `totalHours`, `bestRating`, `lastExperienceStatus`, `lastPlayedYear`, `hasPlatinum`, `platforms` (legacy codes, distinct, catalog order; `OTHER` for platforms the old enum lacked), `platformRefs` (`[{id, name, color}]`, catalog order), `lastPlayedAt` (latest experience `endDate`/`startDate`, nullable date) (two queries in total, no N+1) |
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

Platform: send `platformId` (catalog id). Clients built before the catalog may send the legacy `platform` code instead (`PC`, `PS5`, `PS4`, `XBOX`, `SWITCH`, `MOBILE`); on update, echoing the run's current legacy code (including `OTHER`) keeps its platform. Unknown id/code → `400 INVALID_PLATFORM`. Responses carry `platformId`, `platformName`, `platformColor` and the legacy `platform` code (`OTHER` for platforms the old enum lacked); the stats run lists (`/years/{year}` experiences, `/in-progress`) do too.

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
| DELETE | `/link` | Required | Unlinks (`204`). Confirmed games keep their hours; pending Steam placeholders (unless PSN also links them) and the ignore list are deleted |
| POST | `/sync` | Required | Pulls the Steam library → `{gamesScanned, newGamesPending, linkedExisting, gamesUpdated, ignored, skipped, errored, profilePrivate}` |
| GET | `/pending` | Required | Steam's slice of the pending queue (games with a Steam link, or with no link at all; games only linked to PSN are listed under PSN): game fields plus `appId`, `steamPlaytimeMinutes` (latest Steam total) and `lastPlayedAt` (Steam `rtime_last_played` as an ISO instant, null if never played) |
| PUT | `/pending/{gameId}/confirm` | Required | Confirms category/saga/genres and imports the synced total of every link of the game (Steam and PSN) |
| PUT | `/pending/{gameId}/ignore` | Required | Deletes the Steam link and the placeholder (kept if PSN also links it) and never re-imports that app (`204`) |
| GET | `/ignored` | Required | Ignored apps `{appId, name, ignoredAt}` |
| DELETE | `/ignored/{appId}` | Required | Un-ignores; the app comes back as pending on the next sync (`204`) |

Errors carry a stable code in `message`: `503 STEAM_NOT_CONFIGURED` (blank `STEAM_API_KEY`), `404 STEAM_NOT_LINKED`, `409 SYNC_IN_PROGRESS`, `409 STEAM_LINK_CHANGED`, `429 STEAM_RATE_LIMITED`, `502 STEAM_API_KEY_REJECTED`, `503 STEAM_UNAVAILABLE`.

Sync rules:
- The Steam HTTP call (5 s connect / 15 s read timeout) runs outside any DB transaction; already-linked apps are then updated in one transaction and each new app is attached/created in its own, so one failing app is counted as `errored` without aborting the rest.
- An app with no link whose name matches an existing game is attached to it with the current Steam playtime as baseline: those hours were already logged by hand, only later deltas are added. Names match ignoring case, trademark marks (`™ ® ©`), typographic apostrophes/quotes, Unicode compatibility forms (NFKC), repeated spaces, a trailing console suffix such as `(PlayStation®5)`, `(PS4)` or `(PS4 & PS5)`, and standalone Roman numerals II to XX versus digits (`Hades II` = `Hades 2`; `I` and `X` are never converted) (`ImportedGameNames.nameKey`, in memory only). An app with no match becomes a pending game named with the clean name (marks, NFKC, quotes, spaces and console suffix handled the same way; the raw name when nothing is left), also kept as `games.imported_name`.
- Pending games are filled from IGDB in the background when `TWITCH_CLIENT_ID`/`TWITCH_CLIENT_SECRET` are set (`IGDB_PENDING_ENRICHMENT_ENABLED=false` turns it off). Every Steam/PSN import (even one that fails halfway) queues one run for the user and never waits on it; a run looks up the user's pending games IGDB was never asked about (`games.igdb_lookup_at IS NULL`, at most 200 per run), so games missed by a dropped run (IGDB down, restart) are caught up on the next sync, while a game without a confident match is asked about once. One IGDB request per game (searched by the clean name, filtered to PC for Steam and PS4/PS5 for PSN), at most ~3 requests/s, in batches of 20; a run stops after 3 failed lookups in a row (those games stay unmarked and are retried). Only a confident match is used: exactly one IGDB game with the same normalized name on one of the synced platforms. Developer, publisher, release date, genres (mapped onto existing genres like the web autofill; none are created) and a category suggested from IGDB's game modes (single player only `SINGLEPLAYER`, multiplayer only `ONLINE`, both `HYBRID`) are only filled when empty. The cover becomes IGDB's portrait cover (`t_cover_big`) when empty or still the imported platform image (`games.imported_cover_url`), and the name becomes IGDB's canonical name while it is still the imported one (`games.imported_name`) and no other game of the user has it. Games confirmed meanwhile are left as they are.
- Confirming an `ONLINE`/`HYBRID` game sets `OnlinePlaytime` to `max(existing, steamTotal)`; later syncs add deltas. A negative delta (refund, reset) only lowers the baseline.
- One Steam account per Pytra user (`steam_links.steam_id` is unique). Re-linking a different account resets all Steam baselines.
- `SteamSyncScheduler` re-syncs every linked account every 6 hours (first run 10 min after startup) and is skipped when `STEAM_API_KEY` is blank (linking still works). A run aborts after 3 consecutive `STEAM_UNAVAILABLE`. A per-user in-memory guard (single-instance deployment), held until commit, serializes sync, unlink, account switch, confirm and ignore (`409 SYNC_IN_PROGRESS`; a blocked account switch redirects with `error=steam_sync_in_progress`). `last_sync_at` is written with a conditional UPDATE on the expected `steam_id`, so a relink during the Steam call yields `STEAM_LINK_CHANGED` instead of being reverted.

### PlayStation Network (`/api/v1/integrations/psn`)
Users never sign in to PSN. They type their **online ID** and Pytra reads that account's **public** game list with one Pytra-owned service account (`PSN_NPSSO`). The user's PSN privacy setting for "Gaming | Media" (games and trophies) must be visible to **Anyone**, the same way Steam needs a public profile. PSN only reports PS4 and PS5 titles (older consoles have no playtime data).

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/status` | Required | `{linked, accountId, onlineId, lastSyncAt, configured, linkedGamesCount, pendingCount, ignoredCount, serviceAuthError, serviceAuthFailingSince}`. The last two are only filled for `ADMIN` tokens: `serviceAuthError = "PSN_SERVICE_AUTH_FAILED"` while Sony rejects the service account (renew `PSN_NPSSO`), null otherwise |
| PUT | `/link` | Required | Body `{"onlineId": "EstebanK13"}` (3 to 16 letters, digits, `-`, `_`; trimmed). Resolves the ID on PSN and stores it; answers the status. Re-linking the same account (renamed ID) keeps everything; another account resets all PSN baselines |
| DELETE | `/link` | Required | Unlinks (`204`). Confirmed games keep their hours; PSN-only pending placeholders and the ignore list are deleted |
| POST | `/sync` | Required | Pulls the PS4/PS5 game list → `{gamesScanned, newGamesPending, linkedExisting, gamesUpdated, ignored, skipped, errored, profilePrivate}` (same shape as Steam). `profilePrivate: true` means the user's privacy settings hide the game list; nothing was changed |
| GET | `/pending` | Required | Pending games with a PSN link: game fields plus `titleId`, `psnPlatform` (`"PS5"`/`"PS4"`, the console of the run that confirm will create), `psnPlaytimeMinutes` and `lastPlayedAt` (ISO instant, nullable). A game played on both consoles shows its PS5 title, the summed playtime and the latest date |
| PUT | `/pending/{gameId}/confirm` | Required | Same body and behavior as Steam's confirm |
| PUT | `/pending/{gameId}/ignore` | Required | Ignores the game's PSN titles (`204`); the placeholder is deleted unless Steam also links it |
| GET | `/ignored` | Required | Ignored titles `{titleId, platform, name, ignoredAt}` (`platform` is `"PS5"`/`"PS4"`) |
| DELETE | `/ignored/{titleId}` | Required | Un-ignores; the title comes back as pending on the next sync (`204`) |

Errors carry a stable code in `message`: `400 PSN_INVALID_ONLINE_ID`, `404 PSN_USER_NOT_FOUND` (no account with that online ID), `422 PSN_PROFILE_PRIVATE` (link only: privacy settings hide the account; at sync time a hidden game list is `profilePrivate: true` instead), `404 PSN_NOT_LINKED`, `409 SYNC_IN_PROGRESS`, `409 PSN_LINK_CHANGED`, `429 PSN_RATE_LIMITED`, `503 PSN_NOT_CONFIGURED` (blank `PSN_NPSSO`), `503 PSN_UNAVAILABLE` (Sony down **or** the service account rejected; users never see auth details).

Sync rules (same as Steam unless stated):
- PSN reports the PS4 and PS5 versions of a game as two titles with separate playtime. Both link to the same Pytra game (`game_platform_links.platform` = `PSN_PS4` / `PSN_PS5`, `external_id` = title id). Confirming a `SINGLEPLAYER` game creates one canonical run per console ("Importado de PlayStation", catalog platform `PS4` or `PS5`); `ONLINE`/`HYBRID` games get `max(existing, sum of the linked totals)`. A second title of the same console with the same name (regional versions) is `skipped`.
- `playDuration` (ISO-8601, e.g. `PT228H56M33S`) is stored in minutes, rounded to the nearest minute. `lastPlayedDateTime` becomes the link's `lastPlayedAt`. The title artwork (`imageUrl`) is the cover of new placeholders and of existing games without one.
- The pending queue is shared with Steam (`games.review_status`); each integration lists the games linked to it. The per-user sync guard is shared too, so Steam and PSN operations of one user never overlap; a PSN sync fetches the game list before taking it and only holds it while applying the list (a link removed or switched meanwhile yields `PSN_LINK_CHANGED`).
- Ownership of an online ID is not proven (there is no PSN login), so several Pytra users may link the same PSN account; only public data is read.
- Rate limit: every user shares the service account, so a `429` from Sony pauses **all** PSN calls (link, manual sync, scheduler) for `Retry-After` (clamped to 1 min to 1 h; 15 min when absent); meanwhile they fail fast with `429 PSN_RATE_LIMITED` without calling Sony. In-memory, per instance.
- `PsnSyncScheduler` re-syncs every linked account every 12 hours (first run 20 min after startup) and is skipped without `PSN_NPSSO`. Accounts go least recently synced first (never-synced first), with `PSN_SCHEDULER_PAUSE` (default `3s`) between two accounts, so a run cut short never starves the same users. A run stops at once when the service account is rejected or PSN is rate limiting, or after 3 consecutive `PSN_UNAVAILABLE`. Calls use 5 s connect / 15 s read timeouts.

How the service account authenticates (the PlayStation App flow, as implemented by the open-source [psn-api](https://github.com/achievements-app/psn-api) and [psnawp](https://github.com/isFakeAccount/psnawp) libraries): the NPSSO cookie is exchanged at `ca.account.sony.com/api/authz/v3/oauth/authorize` for a one-time code (read from the `Location` header of the 302), and the code at `/token` for an access token (about 1 h) and a refresh token (about 2 months). Expired access tokens are refreshed with the refresh token; if Sony rejects it, the API signs in again from the NPSSO. After Sony rejects the NPSSO, sign-in is not retried for 5 minutes (requests fail fast with `PSN_UNAVAILABLE`). Tokens live in memory only (single instance) and are never logged; a restart signs in again. Online IDs are resolved with `us-prof.np.community.playstation.net/userProfile/v1/users/{onlineId}/profile2` and game lists come from `m.np.playstation.com/api/gamelist/v2/users/{accountId}/titles?categories=ps4_game,ps5_native_game` (pages of 200).

#### Renewing `PSN_NPSSO`
The NPSSO lasts about **2 months**. When it expires, PSN syncs answer `503 PSN_UNAVAILABLE`, the logs show `WARN PSN service account authentication failed ... Renew PSN_NPSSO`, and `GET /api/v1/integrations/psn/status` with an `ADMIN` token reports `serviceAuthError: "PSN_SERVICE_AUTH_FAILED"`. To renew it:
1. In a browser, sign in at https://www.playstation.com with the **service account** (a dedicated PSN account owned by Pytra, not a personal one).
2. In the same browser, open https://ca.account.sony.com/api/v1/ssocookie. It answers `{"npsso":"<64 characters>"}`.
3. Set that value as `PSN_NPSSO` on the `pytra-api` Railway service and restart it. Never commit it or paste it in logs or issues.
4. As admin, check `GET /api/v1/integrations/psn/status`: `serviceAuthError` is null again after the next PSN call.

Signing out of that browser session or changing the account's password can invalidate the NPSSO early.

### Game metadata autofill (`/api/v1/metadata`)
Read-only proxies used to prefill the game form; nothing is persisted. Results are cached in memory (searches 10 min, details 6 h); upstream calls use 3 s connect / 5 s read timeouts.

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/providers` | Required | `{igdb, steam}`: which sources this deployment offers (`igdb` is false without Twitch credentials) |
| GET | `/igdb/search?q=` | Required | Up to 10 `{igdbId, name, year, imageUrl, platforms[]}`; editions, bundles, packs and mods are left out, DLC/expansions/remakes stay |
| GET | `/igdb/{igdbId}` | Required | `{igdbId, name, developer, publisher, releaseDate, genres[], coverImageUrl, platforms[]}` (same shape as Steam's details plus `platforms`) |
| GET | `/steam/search?q=` | Required | Up to 10 `{appId, name, imageUrl}` from the Spanish Steam store |
| GET | `/steam/{appId}` | Required | `{steamAppId, name, developer, publisher, releaseDate, genres[], coverImageUrl}` (`coverImageUrl` is the portrait library capsule `library_600x900.jpg`) |

- IGDB: the Twitch app token (client credentials) is kept in memory, renewed 5 min before it expires and once more if IGDB answers `401`. The search term is escaped inside the Apicalypse string literal. `coverImageUrl` is the official portrait box art (`t_cover_big`, Pytra shows covers as 2:3 posters), with a landscape artwork (`t_screenshot_big`) only as a fallback. Future release dates become `null`.
- Errors (`message` code): `400 INVALID_SEARCH_QUERY` (2 to 100 chars), `400 INVALID_IGDB_ID` / `INVALID_STEAM_APP_ID`, `404 IGDB_DISABLED`, `404 IGDB_GAME_NOT_FOUND`, `503 IGDB_UNAVAILABLE`, `404 STEAM_APP_NOT_FOUND`, `422 STEAM_APP_NOT_A_GAME`, `503 STEAM_UNAVAILABLE`.

### Cover thumbnails (`/api/v1/covers`, public)
| Method | Endpoint | Description |
|---|---|---|
| GET | `/thumb?url=&w=` | Downscaled JPEG of a Steam cover for list views, cached in memory and served with a 1-year immutable `Cache-Control`. Public because `<img>` tags can't send the token; a URL allowlist limits it to Steam's image CDNs. Errors: `400 INVALID_COVER_URL` / `INVALID_COVER_WIDTH`, `404 COVER_NOT_FOUND`, `502 COVER_UNAVAILABLE` |

## Tests

The class suffix tells the two kinds apart:

| Suffix | Kind | Needs Docker |
|---|---|---|
| `*Test` | Unit test (plain JUnit with mocks) | No |
| `*Tests` | Integration test (extends `AbstractIntegrationTest`) | Yes |

| Command | What runs |
|---|---|
| `./mvnw test` | Everything (Surefire picks up both suffixes) |
| `./mvnw test -Dtest='*Test'` | Unit tests only, no Docker needed |
| `./mvnw test -Dtest=SteamIntegrationTests` | One class |

Integration tests extend `AbstractIntegrationTest`: full Spring context, MockMvc, the `test` profile (`src/test/resources/application-test.yaml`, which supplies dummy JWT/Google/mail settings so no environment variable is needed) and one shared PostgreSQL Testcontainer via `@ServiceConnection`.

- `TenantIsolationTests` proves that one user can never read, modify or delete another user's games, sagas, experiences, online playtime, stats, profile or Steam pending queue (cross-user access is always `404`), and that every protected endpoint answers `401` without a valid token.
- `SteamIntegrationTests` covers the Steam sync with the HTTP client mocked: pending creation, same-name linking, per-app failure isolation, private profiles, negative deltas, ignore/unignore, status, unlink/relink and cross-user isolation.
- `PsnIntegrationTests` covers the PSN sync with the HTTP client mocked: online-ID linking and validation, PS4 + PS5 titles sharing one game, one run per console on confirm, same-name linking, private profiles, ignore/unignore, unlink and cross-user isolation.
- `StatsIntegrationTests`, `GameLibraryAggregatesTests` and `AccountIntegrationTests` cover averages and status counts, year/month attribution, highlights, yearly notes, in-progress runs, library aggregates, both export formats and account deletion (every user-owned table emptied, other users untouched).
- `DemoModeIntegrationTests` covers the read-only demo: `404` when disabled, demo token reads the demo account, every write method gets `403 DEMO_READ_ONLY`, normal tokens unaffected.
- `ApiDocsTests` checks that `/v3/api-docs` is served outside `prod`.
- `IgdbMetadataIntegrationTests`, `GameMetadataIntegrationTests` and `PendingGameNameMigrationTests` cover the autofill endpoints and the pending-name cleanup migration. The enrichment worker and name normalization are unit-tested (`PendingGameEnricherTest`, `IgdbPendingMatchTest`, `ImportedGameNamesTest`).
- `PytraApiApplicationTests` is the context-load smoke test.

**CI:** `.github/workflows/ci.yml` runs `./mvnw -B verify` (JDK 21 Temurin, Maven cache) on every push and pull request to `master`. GitHub's `ubuntu-latest` runners have Docker, so the Testcontainers tests run there even when they can't run locally.

## Deployment (Railway)

Production runs on Railway as three services: `pytra-api` (this repo), `pytra-web` and PostgreSQL. There is no GitHub auto-deploy; deploys are manual from the repo directory with the Railway CLI:

```bash
railway up --service pytra-api --environment production --detach
```

There is no Dockerfile in this repo; Railway builds the Maven project directly. Before deploying, make sure the service has `SPRING_PROFILES_ACTIVE=prod` and every variable marked **Prod** or **Yes** in the configuration table. `server.forward-headers-strategy: framework` makes generated URLs (such as the OAuth2 redirect URI) use the public HTTPS host behind Railway's proxy.

## Roadmap

- [x] Auth: register, login, email verification, password reset, Google OAuth2/OIDC (web and Android), invite-only mode
- [x] Transactional email via Brevo, per-IP rate limiting on public auth endpoints
- [x] Genres, sagas, games, runs, online playtime, platform catalog, decimal ratings
- [x] Aggregated stats and year in review (GOTY, highlights, yearly notes)
- [x] Steam and PlayStation Network library sync with a shared pending-review queue
- [x] IGDB autofill and automatic IGDB enrichment of pending games
- [x] Read-only demo, data export, account deletion
- [x] Integration and tenant-isolation tests on Testcontainers, CI on GitHub Actions
- [x] Frontend ([pytra-web](https://github.com/EstebanMM13/pytra-web)) and production deployment on Railway
- [ ] PSN trophies
- [ ] Automatic deploys (manual today)
