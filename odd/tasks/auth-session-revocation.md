# Auth session revocation

Objective: a password reset ends every JWT issued before it, and one-time auth tokens (exchange code, reset token) can only be consumed once even under concurrent requests.
Branch: pytra-api `fix/auth-session-revocation`. Delivery: ask-on-risk. Runner: `./mvnw test` (integration tests need Docker/Testcontainers; CI runs them).

## Specs

- S1 Audit finding (user approved "si empieza por lo más importante"): "resetting a password does not log out existing sessions". After a successful password reset, every JWT issued before the reset gets 401 on authenticated endpoints; a JWT issued after it (new login) works. JWT `iat` has second precision, so the cut-off is the reset time truncated to the second (a token minted in the same second as the reset survives; accepted trade-off).
- S2 Assumption (same takeover-protection intent): when Google sign-in claims an unverified local account and drops its password, the previous JWTs of that account are revoked too.
- S3 Audit finding: "an OAuth exchange code can be used twice under a race". `exchange-code` consumes the code atomically: of two concurrent requests with the same code, only one gets a JWT; the other gets the existing "Invalid token" error. Same atomic consumption for the password reset token.
- S4 No extra query per request: the JWT filter's existing user-exists lookup also checks the revocation cut-off.

## Tasks

- [x] T1 S1-S4 migration + revocation check + atomic consumption + tests — inline (no writer trigger; one understood change) — 215b6d2

## Log

- L1 (2026-11-04) User: "me puedes hacer un analisis de toda la app y me dices que se puede mejorar, tanto en la api como visualmente" -> audit delivered -> "si empieza por lo más importante".
- L2 (2026-11-04) T1 done inline: RED (compile failure on missing API) then GREEN. Focused unit tests 31/31 pass; full suite 1249 tests, 0 failures, 356 errors, all Testcontainers integration classes failing to start without Docker on this machine (SessionRevocationIntegrationTests included: pending CI). Independent verifier (Risk item 2): S1-S4 pass, no blockers; advisories: null-iat binding unproven on live Postgres (out of domain), pending exchange codes not invalidated by a reset (baseline), clock skew if scaled out. Native review (risk, reliability) granted -> approved and acknowledged; two non-blocking suggestions (same-second window; no concurrent-race integration test).
