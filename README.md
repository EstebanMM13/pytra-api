# Pytra API

Backend REST API for **Pytra**, a personal video game tracking application — manage your game library, sagas, genres, play sessions and stats across platforms.

## Tech Stack

- **Java 21** + **Spring Boot 4.1.0**
- **Spring Data JPA** + **PostgreSQL 16**
- **Flyway** for database migrations
- **Spring Security** + **JWT** (jjwt 0.12.6) for stateless authentication
- **OAuth2 / OIDC** login with Google
- **Bean Validation** (Jakarta Validation)
- **springdoc-openapi** for API documentation
- **Testcontainers** for integration tests

## Architecture

The codebase is organized by **module (bounded context)**, not by technical layer:

```
com.estebanmm13.pytra_api/
├── auth/       # Users, registration, login, password reset, Google OAuth2/OIDC, JWT
├── games/      # Game catalog: Genres, Sagas, (Games, Experiences, OnlinePlaytime in progress)
├── security/   # JWT filter, AuthenticatedUser principal, CurrentUserResolver
└── error/      # Global exception handling
```

Each module follows the same internal structure: `model/`, `dto/`, `mapper/`, `repository/`, `service/`, `controller/`.

**Key architectural decisions:**
- Cross-module references use a plain scalar id (e.g. `Saga.userId: Long`), never a JPA `@ManyToOne`, to keep bounded contexts decoupled at the object-graph level. Relationships within the same module (e.g. future `Game → Saga`) do use real JPA associations.
- Controllers resolve the authenticated user via `CurrentUserResolver` and pass `userId` explicitly into service methods — services never depend on Spring Security directly, keeping them trivially testable.
- Mappers only convert `Entity → ResponseDto` (pure, no side effects). `RequestDto → Entity` construction stays in the service layer, since it usually carries business logic (password hashing, defaults, normalization) that doesn't belong in a mapper.
- Every user-scoped query (e.g. `findByIdAndUserId`) filters by owner at the query level, so accessing another user's resource returns `404`, not `403` — avoids confirming resource existence to an attacker.
- Uniqueness that must hold under concurrent writes is enforced with real database constraints (including case-insensitive functional indexes), not just an application-level pre-check — the pre-check is only there for a fast, friendly error message.

## Getting Started

### Prerequisites
- JDK 21
- Docker (for PostgreSQL)

### 1. Start the database

```bash
cp .env.example .env   # set POSTGRES_PASSWORD
docker compose up -d
```

### 2. Configure environment

Copy `.env.example` to `.env` and fill in the required values (DB credentials, JWT secret, Google OAuth client id/secret, mail settings, etc.).

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
| POST | `/exchange-code` | Exchange Google OAuth2 code for a Pytra JWT |

### Genres — `/api/v1/genres`
| Method | Endpoint | Description |
|---|---|---|
| GET | `/` | List all genres |
| POST | `/` | Create a genre if it doesn't exist yet |

### Sagas — `/api/v1/sagas` (per authenticated user)
| Method | Endpoint | Description |
|---|---|---|
| GET | `/` | List the current user's sagas |
| GET | `/{id}` | Get a saga by id |
| POST | `/` | Create a saga |
| PUT | `/{id}` | Update a saga |
| DELETE | `/{id}` | Delete a saga |

## Roadmap

Execution order: **Games/Sagas/Genres → Experiences/OnlinePlaytime → Stats → Multi-tenancy → Steam import** (Steam integration deprioritized in favor of core tracking features first).

- [x] Auth: register, login, email verification, password reset
- [x] Auth: Google OAuth2/OIDC login
- [x] Genres CRUD (catalog)
- [x] Sagas CRUD (per-user)
- [ ] Games CRUD (linked to Sagas and Genres)
- [ ] Experiences / OnlinePlaytime tracking
- [ ] Aggregated stats
- [ ] Multi-tenancy checkpoint
- [ ] Steam library import
