# Pytra API

Backend REST API for **Pytra**, a personal video game tracking application — manage your game library, sagas, genres, play sessions and stats across platforms, with automatic playtime sync from Steam.

## Tech Stack

- **Java 21** + **Spring Boot 4.1.0**
- **Spring Data JPA** + **PostgreSQL 16**
- **Flyway** for database migrations
- **Spring Security** + **JWT** (jjwt 0.12.6) for stateless authentication
- **OAuth2 / OIDC** login with Google
- **Steam OpenID 2.0** account linking (manual implementation — no client library; see Architecture)
- **Bean Validation** (Jakarta Validation)
- **springdoc-openapi** for API documentation
- **Testcontainers** for integration tests

## Architecture

The codebase is organized by **module (bounded context)**, not by technical layer:

```
com.estebanmm13.pytra_api/
├── auth/         # Users, registration, login, password reset, Google OAuth2/OIDC, JWT
├── games/        # Genres (catalog), Sagas, Games, GamePlatformLink
├── experiences/  # Experience (playthroughs), OnlinePlaytime
├── steamsync/    # Steam OpenID linking, Steam Web API client, library sync
├── stats/        # Aggregated stats across games/sagas/genres/experiences
├── config/       # SecurityConfig
└── error/        # Global exception handling
```

Each module follows the same internal structure: `model/`, `dto/`, `mapper/`, `repository/`, `service/`, `controller/`.

**Key architectural decisions:**
- Cross-module references use a plain scalar id (e.g. `Saga.userId: Long`, `Experience.gameId: Long`), never a JPA `@ManyToOne`, to keep bounded contexts decoupled at the object-graph level. Relationships within the same module (e.g. `Game → Saga`, `Game ↔ Genre`, `GamePlatformLink → Game`) do use real JPA associations. One direct consequence: cross-module aggregates (e.g. Stats grouping Games by hours logged in Experience) can't be a single JPQL join — they're computed by fetching both sides separately and combining them in the service layer.
- Controllers resolve the authenticated user via `CurrentUserResolver` and pass `userId` explicitly into service methods — services never depend on Spring Security directly, keeping them trivially testable.
- Mappers only convert `Entity → ResponseDto` (pure, no side effects). `RequestDto → Entity` construction stays in the service layer, since it usually carries business logic (password hashing, defaults, normalization) that doesn't belong in a mapper.
- Every user-scoped query (e.g. `findByIdAndUserId`) filters by owner at the query level, so accessing another user's resource returns `404`, not `403` — avoids confirming resource existence to an attacker. Audited explicitly as a dedicated multi-tenancy checkpoint (see Roadmap).
- Uniqueness that must hold under concurrent writes is enforced with real database constraints (including case-insensitive functional indexes), not just an application-level pre-check — the pre-check is only there for a fast, friendly error message.
- **Steam integration is account linking, not an alternative login.** `SteamLink` is tenant-scoped (requires an existing, already-authenticated Pytra user) — it's the equivalent of "Connect Steam" in account settings, not a parallel sign-in flow like Google. `openid4java` was evaluated and rejected (unmaintained since 2015); Steam's OpenID 2.0 profile is stateless and simplified enough that verification is just one HTTP POST with `openid.mode=check_authentication`, no library needed.
- **Steam-synced hours never guess which playthrough they belong to.** Steam only reports one cumulative total per game, with no way to attribute it to a specific run. For `SINGLEPLAYER` games, sync maintains exactly one canonical `Experience` per game (tracked via `GamePlatformLink.experienceId`), completely separate from any `Experience` the user logs by hand. For `ONLINE`/`HYBRID` games, synced hours accumulate into `OnlinePlaytime` instead (a natural fit: it's already a single running total per game, same shape as Steam's own number).
- A freshly Steam-synced game starts with `category = null` and `reviewStatus = PENDING_REVIEW` (hence `Game.category` is nullable) until the user confirms it with a real category via the pending-review queue — the single/online classification heuristic is manual-only for now, there's no automatic tagging yet.

## Getting Started

### Prerequisites
- JDK 21
- Docker (for PostgreSQL)
- A Steam Web API key (free, from https://steamcommunity.com/dev/apikey) — only needed to exercise the Steam integration

### 1. Start the database

```bash
cp .env.example .env   # set POSTGRES_PASSWORD
docker compose up -d
```

### 2. Configure environment

Copy `.env.example` to `.env` and fill in the required values: DB credentials, `JWT_SECRET`/`JWT_EXPIRATION`, `GOOGLE_CLIENT_ID`/`GOOGLE_CLIENT_SECRET`, and `STEAM_API_KEY`. The app still starts without a Steam key, but any Steam endpoint call will fail until one is set.

### 3. Run the app

```bash
./mvnw spring-boot:run
```

Flyway applies all pending migrations automatically on startup.

## API Overview

### Auth — `/api/v1/auth`
| Method | Endpoint | Description |
|---|---|---|
| POST | `/register` | Register a new user |
| POST | `/login` | Login with username/email + password |
| GET | `/verify-email?token=` | Verify email address |
| POST | `/forgot-password` | Request a password reset |
| POST | `/reset-password` | Reset password with token |
| POST | `/exchange-code` | Exchange a one-time code (Google or Steam redirect) for a Pytra JWT |

### Genres — `/api/v1/genres` (global catalog, no per-user scoping)
| Method | Endpoint | Description |
|---|---|---|
| GET | `/` | List all genres |
| POST | `/` | Create a genre if it doesn't exist yet |

### Sagas — `/api/v1/sagas`
| Method | Endpoint | Description |
|---|---|---|
| GET | `/` | List the current user's sagas |
| GET | `/{id}` | Get a saga by id |
| POST | `/` | Create a saga |
| PUT | `/{id}` | Update a saga |
| DELETE | `/{id}` | Delete a saga (games keep existing, saga reference is cleared) |

### Games — `/api/v1/games`
| Method | Endpoint | Description |
|---|---|---|
| GET | `/` | List the current user's games |
| GET | `/{id}` | Get a game by id |
| POST | `/` | Create a game (optionally linked to a saga and/or genres) |
| PUT | `/{id}` | Update a game |
| DELETE | `/{id}` | Delete a game |

### Experiences — `/api/v1/games/{gameId}/experiences`, `/api/v1/experiences`
| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/v1/games/{gameId}/experiences` | List all playthroughs for a game |
| POST | `/api/v1/games/{gameId}/experiences` | Log a new playthrough |
| PUT | `/api/v1/experiences/{id}` | Update a playthrough |
| DELETE | `/api/v1/experiences/{id}` | Delete a playthrough |

### Online Playtime — `/api/v1/games/{gameId}/online-playtime`
| Method | Endpoint | Description |
|---|---|---|
| GET | `/` | Get the tracked online playtime for a game |
| PUT | `/` | Upsert online playtime (creates it on first call, updates it after) |

### Stats — `/api/v1/stats`
| Method | Endpoint | Description |
|---|---|---|
| GET | `/summary` | Totals: games, sagas, experiences, hours (singleplayer/online kept separate), platinums |
| GET | `/by-year` | Hours and experience count grouped by year |
| GET | `/by-saga` | Hours and game count grouped by saga (includes a "no saga" bucket) |
| GET | `/by-genre` | Hours and game count grouped by genre |
| GET | `/top-rated?limit=` | Highest-rated experiences |
| GET | `/most-played/singleplayer?limit=` | Games ranked by `Experience` hours |
| GET | `/most-played/online?limit=` | Games ranked by `OnlinePlaytime` hours — kept as a separate ranking, never merged with singleplayer |

### Steam Integration — `/api/v1/integrations/steam`
| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/login` | Required | Redirects to Steam OpenID to connect the current account |
| GET | `/callback` | Public | Steam redirects here; verifies the response, upserts `SteamLink`, redirects to the frontend with a one-time exchange code |
| POST | `/sync` | Required | Pulls the linked Steam library, creates pending games for new titles, applies playtime deltas to already-confirmed ones |
| GET | `/pending` | Required | Lists games discovered by sync that still need manual review (`reviewStatus = PENDING_REVIEW`) |
| PUT | `/pending/{gameId}/confirm` | Required | Confirms a pending game's category/saga/genres; materializes its accumulated Steam hours into `Experience` (singleplayer) or `OnlinePlaytime` (online/hybrid) |

A scheduled job (`SteamSyncScheduler`) also re-syncs every linked account automatically every 6 hours.

## Roadmap

- [x] Auth: register, login, email verification, password reset
- [x] Auth: Google OAuth2/OIDC login
- [x] Genres CRUD (catalog)
- [x] Sagas CRUD (per-user)
- [x] Games CRUD (linked to Sagas and Genres)
- [x] Experiences / OnlinePlaytime tracking
- [x] Aggregated stats
- [x] Multi-tenancy checkpoint (code audit + manual cross-tenant verification; automated isolation test written but not run — no local Docker to run Testcontainers)
- [x] Steam account linking + library sync + pending-review queue (built, compiles, boots clean against real Postgres — not yet verified end-to-end against a real Steam account)
- [ ] Full automated test suite (blocked locally by the same missing-Docker constraint as the multi-tenancy test)
- [ ] Frontend (`pytra-web`)
- [ ] CI/CD
