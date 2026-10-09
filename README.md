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
| `STEAM_API_KEY` | No | empty | Steam Web API key; Steam calls fail without it |
| `FRONTEND_URL` | Prod | `http://localhost:4200` | Web app origin; used for email links and the web OAuth callback |
| `PUBLIC_API_URL` | Prod | `http://localhost:8080` | Public API origin; used to build the Steam OpenID `return_to` |
| `CORS_ALLOWED_ORIGINS` | Prod | `http://localhost:4200,https://localhost` | Comma-separated allowed origins (`https://localhost` is the Capacitor Android WebView) |
| `MOBILE_REDIRECT_URI` | Prod | `com.estebanmm13.pytra://oauth-callback` | Final redirect for flows started from the Android app |
| `BREVO_API_KEY` | Prod | empty | Brevo API key; blank means emails are logged (blank is rejected in `prod`) |
| `MAIL_FROM_EMAIL` | Prod | empty | Sender address; required whenever `BREVO_API_KEY` is set |
| `MAIL_FROM_NAME` | No | `Pytra` | Sender display name |
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
| Rate limit | Max 5 POSTs per IP per minute on `register`, `login`, `resend-verification`, `forgot-password`; extra requests get `429`. In-memory, per instance |

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

All endpoints except auth, the Google OAuth2 endpoints, Steam `login`/`callback` and the OpenAPI/Swagger paths require `Authorization: Bearer <jwt>`.

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
| GET | `/me` | Profile: username, display name, email, whether a password is set, whether Google is linked |
| PATCH | `/me` | Change username |

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
| GET | `/` | List the current user's games |
| GET | `/{id}` | Get a game |
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

### Online playtime (`/api/v1/games/{gameId}/online-playtime`)
| Method | Endpoint | Description |
|---|---|---|
| GET | `/` | Get tracked online playtime |
| PUT | `/` | Upsert online playtime |

### Stats (`/api/v1/stats`)
| Method | Endpoint | Description |
|---|---|---|
| GET | `/summary` | Totals: games, sagas, experiences, hours (singleplayer/online separate), platinums |
| GET | `/by-year` | Hours and experience count per year |
| GET | `/by-saga` | Hours and game count per saga (includes a "no saga" bucket) |
| GET | `/by-genre` | Hours and game count per genre |
| GET | `/top-rated?limit=` | Highest-rated experiences |
| GET | `/most-played/singleplayer?limit=` | Games ranked by `Experience` hours |
| GET | `/most-played/online?limit=` | Games ranked by `OnlinePlaytime` hours (never merged with singleplayer) |

### Steam (`/api/v1/integrations/steam`)
| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/connect-token` | Required | Issues a one-time state token (valid 10 min) for the current user |
| GET | `/login?state=` | Public | Full-page redirect to Steam OpenID; identity travels in `state` |
| GET | `/callback` | Public | Verifies Steam's response, upserts `SteamLink`, redirects to the client with a one-time code |
| POST | `/sync` | Required | Pulls the Steam library: new titles become pending games, confirmed ones get playtime deltas |
| GET | `/pending` | Required | Games discovered by sync awaiting review |
| PUT | `/pending/{gameId}/confirm` | Required | Confirms category/saga/genres and materializes accumulated Steam hours |

`SteamSyncScheduler` also re-syncs every linked account every 6 hours.

## Tests

| Command | Needs Docker | What runs |
|---|---|---|
| `./mvnw test` | Yes | Everything, including the integration tests below |
| `./mvnw test -Dtest="RegistrationPolicyTest,UsernamePolicyTest,AuthRateLimitFilterTest,AuthEmailServiceTest,BrevoEmailSenderTest"` | No | Unit tests only |

Integration tests extend `AbstractIntegrationTest`: full Spring context, MockMvc, the `test` profile (`src/test/resources/application-test.yaml`, which supplies dummy JWT/Google/mail settings so no environment variable is needed) and one shared PostgreSQL Testcontainer via `@ServiceConnection`.

- `TenantIsolationTests` proves that one user can never read, modify or delete another user's games, sagas, experiences, online playtime, stats, profile or Steam pending queue (cross-user access is always `404`), and that every protected endpoint answers `401` without a valid token.
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
