# Stage 4 Plan — Ktor Server

**Status:** planning  
**Date:** 2026-11-04

---

## 1. Dependency Versions

All versions verified against Maven Central / Sonatype Central on 2026-11-04.

| Library | groupId:artifactId | Version | Source |
|---|---|---|---|
| Ktor server | `io.ktor:ktor-server-core` | **3.6.1** | https://mvnrepository.com/artifact/io.ktor/ktor-server-core (released 2026-10-30) |
| Ktor Netty engine | `io.ktor:ktor-server-netty` | 3.6.1 | same Ktor BOM |
| Ktor content-negotiation | `io.ktor:ktor-server-content-negotiation` | 3.6.1 | same |
| Ktor kotlinx-serialization | `io.ktor:ktor-serialization-kotlinx-json` | 3.6.1 | same |
| Ktor auth-jwt | `io.ktor:ktor-server-auth-jwt` | 3.6.1 | https://central.sonatype.com/artifact/io.ktor/ktor-server-auth-jwt/3.6.0 — bundles `com.auth0:java-jwt` 4.4.x transitively; no separate JWT dep needed |
| Ktor status-pages | `io.ktor:ktor-server-status-pages` | 3.6.1 | same |
| Ktor call-logging | `io.ktor:ktor-server-call-logging` | 3.6.1 | same |
| Ktor test host | `io.ktor:ktor-server-test-host` | 3.6.1 | https://central.sonatype.com/artifact/io.ktor/ktor-server-test-host-jvm |
| Ktor client (for test assertions) | `io.ktor:ktor-client-content-negotiation` | 3.6.1 | same |
| mockk | `io.mockk:mockk` | **1.14.9** | https://central.sonatype.com/artifact/io.mockk/mockk |
| kotlinx-serialization-json | already in project | 1.12.0 | already in `libs.versions.toml` |
| logback-classic | already in project | 1.6.5 | already in `libs.versions.toml` |
| kotlin | already in project | 2.4.21 | already in `libs.versions.toml` |

**New entries needed in `libs.versions.toml`:**

```toml
[versions]
ktor = "3.6.1"
mockk = "1.14.9"

[libraries]
ktor-server-core             = { module = "io.ktor:ktor-server-core",                    version.ref = "ktor" }
ktor-server-netty            = { module = "io.ktor:ktor-server-netty",                   version.ref = "ktor" }
ktor-server-content-neg      = { module = "io.ktor:ktor-server-content-negotiation",     version.ref = "ktor" }
ktor-serialization-json      = { module = "io.ktor:ktor-serialization-kotlinx-json",     version.ref = "ktor" }
ktor-server-auth-jwt         = { module = "io.ktor:ktor-server-auth-jwt",                version.ref = "ktor" }
ktor-server-status-pages     = { module = "io.ktor:ktor-server-status-pages",            version.ref = "ktor" }
ktor-server-call-logging     = { module = "io.ktor:ktor-server-call-logging",            version.ref = "ktor" }
ktor-server-test-host        = { module = "io.ktor:ktor-server-test-host",               version.ref = "ktor" }
ktor-client-content-neg      = { module = "io.ktor:ktor-client-content-negotiation",     version.ref = "ktor" }
mockk                        = { module = "io.mockk:mockk",                              version.ref = "mockk" }
```

`server/build.gradle.kts` gains `alias(libs.plugins.kotlin.serialization)` and the dependencies above. `mockk` and `ktor-server-test-host` go into `testImplementation`.

---

## 2. Endpoint List

Base path: `/api/v1`

### Auth

| Method | Path | Auth | Request DTO | Response DTO | Errors |
|---|---|---|---|---|---|
| POST | `/auth/register` | none | `RegisterRequest` | `TokenPairDto` | 409 nick taken, 422 validation |
| POST | `/auth/login` | none | `LoginRequest` | `TokenPairDto` | 401 wrong credentials, 423 account locked, 429 rate limit |
| POST | `/auth/refresh` | none | `RefreshRequest` | `TokenPairDto` | 401 token invalid/expired/revoked, 403 reuse detected (family revoked) |
| POST | `/auth/logout` | Bearer JWT | `RefreshRequest` | 204 | 401 |

### User (self)

| Method | Path | Auth | Request DTO | Response DTO | Errors |
|---|---|---|---|---|---|
| GET | `/me` | Bearer JWT | — | `UserProfileDto` | 401 |
| PUT | `/me/settings` | Bearer JWT | `UpdateSettingsRequest` | `UserProfileDto` | 401, 422 |
| PUT | `/me/password` | Bearer JWT | `ChangePasswordRequest` (new) | 204 | 401, 422 weak/reused |
| GET | `/me/interests` | Bearer JWT | — | `InterestsDto` (new) | 401 |
| PUT | `/me/interests` | Bearer JWT | `SetInterestsRequest` (new) | 204 | 401, 422 |

### Taxonomy

| Method | Path | Auth | Request DTO | Response DTO | Errors |
|---|---|---|---|---|---|
| GET | `/categories` | Bearer JWT | — | `List<CategoryDto>` (new) | 401 |

### Feed

| Method | Path | Auth | Query params | Response DTO | Errors |
|---|---|---|---|---|---|
| GET | `/feed` | Bearer JWT | `cursor`, `limit` (default 20, max 50), `lang`, `region`, `publisher`, `category` | `FeedPageDto` | 401, 422 |

Cursor is an opaque string encoding `(score, article_id)` for keyset pagination (no OFFSET). Newest-first within same score.

### Articles

| Method | Path | Auth | Request DTO | Response DTO | Errors |
|---|---|---|---|---|---|
| GET | `/articles/{id}` | Bearer JWT | — | `FeedArticleDto` | 401, 404 |
| POST | `/articles/{id}/rate` | Bearer JWT | `RateRequest` | 204 | 401, 404, 422 |
| DELETE | `/articles/{id}/rate` | Bearer JWT | — | 204 | 401, 404 |
| POST | `/articles/{id}/complain` | Bearer JWT | `ComplaintRequest` | 204 | 401, 404, 422 |
| POST | `/articles/{id}/opened` | Bearer JWT | — | 204 | 401, 404 |

### Notifications

| Method | Path | Auth | Response DTO | Errors |
|---|---|---|---|---|
| GET | `/notifications/new-count` | Bearer JWT | `NewCountDto` (new: `{ count: Int }`) | 401 |

### Admin (role = ADMIN or MODERATOR as noted)

| Method | Path | Roles | Request DTO | Response DTO |
|---|---|---|---|---|
| GET | `/admin/articles` | ADMIN, MOD | query: `q`, `status`, `cursor`, `limit` | `FeedPageDto` |
| PUT | `/admin/articles/{id}` | ADMIN | `AdminArticleUpdateRequest` (new) | `FeedArticleDto` |
| DELETE | `/admin/articles/{id}` | ADMIN | — | 204 |
| GET | `/admin/users` | ADMIN | query: `q`, `role`, `cursor` | `AdminUserPageDto` (new) |
| PUT | `/admin/users/{id}/role` | ADMIN | `SetRoleRequest` (new) | 204 |
| DELETE | `/admin/users/{id}` | ADMIN | — | 204 |
| GET | `/admin/complaints` | ADMIN, MOD | query: `resolved`, `cursor` | `ComplaintPageDto` (new) |
| GET | `/admin/audit` | ADMIN | query: `cursor`, `limit` | `AuditPageDto` (new) |
| GET | `/admin/stats` | ADMIN | — | `PipelineStatsDto` (new) |

### Health

| Method | Path | Auth | Response |
|---|---|---|---|
| GET | `/health` | none | 200 `{"status":"ok"}` |

### New DTOs needed in `:shared`

`ChangePasswordRequest`, `InterestsDto`, `SetInterestsRequest`, `CategoryDto`, `NewCountDto`, `AdminArticleUpdateRequest`, `SetRoleRequest`, `AdminUserPageDto`, `ComplaintPageDto`, `AuditPageDto`, `PipelineStatsDto`.

---

## 3. Auth Design

### JWT access tokens

- **Algorithm:** HS256 with a 256-bit secret.
- **Lifetime:** 15 minutes (`exp` claim).
- **Claims:** `sub` = user id (int, as string), `role`, `jti` (UUID, for future revocation list if needed).
- **Signing secret:** read from env var `NEWSDOVE_JWT_SECRET` at startup; if absent, read from `server/config/server.properties` (gitignored). Never hardcoded, never in git. Startup fails fast if secret is missing.
- **`server/config/server.properties.example`** committed with placeholder values; `server/config/server.properties` gitignored. Same pattern for keystore path/password.
- **Minimum secret length:** 32 bytes; enforced at startup with a clear error message.

### Refresh token rotation

- **Storage:** `refresh_token` table (already created in V10): `token_hash CHAR(64)`, `family_id UUID`, `created_at`, `expires_at` (30 days), `revoked_at`, `replaced_by_id`.
- **Issue:** on login or register, generate a random 256-bit token, store its SHA-256 hash in the table, return the raw token to the client.
- **Rotation:** on `/auth/refresh`, look up by hash:
  1. Not found → 401 (token unknown).
  2. Found, `revoked_at IS NOT NULL` → **reuse detected**: revoke the entire `family_id` (all tokens with that family), return 403. This covers stolen token scenarios.
  3. Found, expired → 401.
  4. Found, valid → revoke old token (set `revoked_at`), issue new token with same `family_id` and new `replaced_by_id`, return new pair.
- **Logout:** marks the presented refresh token revoked. Access token is not revocable (short lifetime); client discards it.
- **Cleanup:** a background coroutine (launched once at startup, runs every 24 h) deletes refresh tokens where `expires_at < NOW() - 7 days` (keeps recent history for audit).

### Login protection (no hard account lockout)

Hard account lockout (locking a nick after N failures) lets any attacker lock a victim's account. Instead:

- **Mechanism:** in-memory `ConcurrentHashMap<Pair<String, String>, AttemptRecord>` keyed by `(remoteHost, nick)`. Use `call.request.origin.remoteHost` only — ignore `X-Forwarded-For` (no proxy in this setup; that header is trivially spoofed).
- **Policy:** growing delay per key — after each consecutive failure double the lockout window (e.g. 1 s, 2 s, 4 s, …, capped at 60 s) → 429 for the duration of the window. Reset on successful login.
- **No hard account lockout.** Remove the `failed_login_count` / `locked_until` columns from use (keep existing columns if present, but do not set them).
- **Audit:** every failed login → `audit_log(action="LOGIN_FAILED", …)`.

---

## 4. Transaction / Session Handling

### One Hibernate session per request

Each route handler opens one Hibernate session via `HibernateUtil.getSessionFactory().openSession()`, performs all DB work within that session, and closes it in a `use {}` block (Kotlin's `Closeable.use`). Services accept an open `Session` parameter so a handler can call multiple services in one transaction.

**Pattern:**

```kotlin
withSession { session ->       // opens session, begins transaction
    val user = userService.findById(session, userId)
    val article = articleService.findById(session, articleId)
    reportService.upsertRating(session, article, user, rating)
}                              // commits on success, rolls back on exception
```

`withSession` and other Kotlin session/transaction helpers live in **`:server`** (e.g. `server/src/main/kotlin/org/example/server/db/SessionUtils.kt`), not in `:core`. `:core` is Java — Kotlin helpers don't belong there. Services gain overloads that accept `Session`; the existing zero-arg constructors stay for backward compatibility in existing tests.

### Keeping JDBC off Ktor's event loop

Ktor uses a Netty event loop (NIO threads). All Hibernate / JDBC calls **must not run on the event loop**. Each route handler wraps its DB work in `withContext(Dispatchers.IO) { ... }`. Dispatchers.IO uses a shared thread pool sized to `max(64, availableProcessors * 2)` — adequate for a laptop demo.

```kotlin
get("/feed") {
    val userId = call.principal<JWTPrincipal>()!!.subject.toInt()
    val page = withContext(Dispatchers.IO) {
        withSession { session -> feedService.getFeed(session, userId, params) }
    }
    call.respond(page)
}
```

---

## 5. Network: HTTP vs HTTPS

The demo runs on a laptop serving an Android phone on the same Wi-Fi / hotspot.

| | HTTP (plain) | HTTPS self-signed CA |
|---|---|---|
| **Setup effort** | Zero — just start the server | Generate CA + server cert with OpenSSL (~10 min); install CA on Android via Settings → Security → Install certificate |
| **Android security** | Android 9+ blocks cleartext HTTP for non-localhost by default; requires `network_security_config.xml` with `cleartextTrafficPermitted="true"` or domain exception | Works with `network_security_config.xml` pinning the custom CA; no cleartext needed |
| **Credential exposure** | Passwords, tokens, article metadata travel in plaintext on the LAN | Encrypted; meets OWASP requirement for the diploma |
| **Diploma requirement** | ROADMAP Stage 4 criterion: "all scenarios go through HTTPS" | ✓ |
| **Revocation / PKI overhead** | — | None; self-signed CA is trusted only by our Android app |

**Decision: HTTPS with a self-signed CA.** This is required by the ROADMAP criterion. Steps: `openssl genrsa` → CA key/cert → server CSR → sign with CA → `ktor { sslConnector { keyStore = ... } }`. The keystore file (`server.jks`) lives outside git; path and password in `server.properties`. HTTP port (8080) also stays open but only for a local `/health` probe; all API endpoints require HTTPS (8443).

---

## 6. Test Plan

All tests run against `db_use_test_it` only (rule 15 — `systemProperty("newsdove.db.name", "db_use_test_it")` already set in `core/build.gradle.kts`; `server/build.gradle.kts` will add the same).

### Test approach

Use `testApplication { }` from `ktor-server-test-host`. This spins up the full Ktor pipeline in-process with a real `db_use_test_it` database (Flyway migrations applied by `TestDb.ensureInitialized()`). No mocking of the DB layer — same rule as `:core` integration tests.

### Test categories

| Category | File(s) | What is tested |
|---|---|---|
| Auth flows | `AuthRouteTest.kt` | register → login → refresh → logout; reuse detection; account lock; rate limit (simulated by injecting a fake clock) |
| Feed | `FeedRouteTest.kt` | cursor pagination correctness; score ordering; language/region filters; blocked articles excluded |
| Rating | `RatingRouteTest.kt` | POST rate; DELETE rate; complain; weight changes in `user_tag_percent` |
| Me / settings | `MeRouteTest.kt` | GET me; PUT settings; PUT password (weak/reused rejection) |
| Interests | `InterestsRouteTest.kt` | PUT interests → initial weights; GET interests |
| RBAC | `RbacTest.kt` | USER gets 403 on every admin endpoint; MODERATOR access; ADMIN access |
| Input validation | `ValidationTest.kt` | 422 on missing fields, oversized strings, invalid enums |
| Error mapping | `ErrorMappingTest.kt` | 404 unknown article; 500 triggers error response (no stack trace in body) |

### Shared test infrastructure

- `ServerTestSetup.kt`: `beforeAll` calls `TestDb.ensureInitialized()`, seeds one ADMIN and one USER, issues tokens for each.
- Tests that mutate data clean up via `TestDb` helpers / `@AfterEach`. **No per-test rollback through HTTP** — HTTP requests commit normally; cleanup deletes the inserted rows after each test.

---

## 7. Step Breakdown

Each step ends with `./gradlew clean :server:test` (and `:core:test`) green before committing.

### 4a — Ktor skeleton + health endpoint
- Add Ktor 3.6.1 to `libs.versions.toml` and `server/build.gradle.kts`.
- `Application.kt`: `embeddedServer(Netty, port=8080)` with `routing { get("/health") { ... } }`.
- `ContentNegotiation` plugin with `json()`.
- `StatusPages` plugin: catch `Exception` → 500 `ErrorResponse`; catch `SerializationException` → 422.
- `CallLogging` plugin.
- `ServerMain.kt` reads `server.properties` for port / HTTPS config (plain HTTP for now).
- Test: `HealthRouteTest` — GET `/health` → 200.

### 4b — Auth routes (register / login) + JWT issuance
- `JwtConfig.kt`: reads secret from env / `server.properties`; issues and validates HS256 tokens.
- `RefreshTokenService.kt` (Kotlin, in `:server`): issue, rotate, revoke, cleanup coroutine.
- `POST /auth/register`: calls `UserService.registerUserNoEmail`, issues token pair, writes `audit_log`.
- `POST /auth/login`: constant-time verify via `UserService.authenticate` (already exists), rate-limit check, account-lock check, issues pair, audit.
- Tests: `AuthRouteTest` — register, login, wrong-password-401, lock-after-5.

### 4c — Refresh + logout
- `POST /auth/refresh`: rotation with reuse detection.
- `POST /auth/logout`: revoke token.
- Tests: refresh happy path; reuse detection → 403 + family revoked; logout → subsequent refresh → 401.

### 4d — Me / settings / password
- JWT `authenticate("jwt-access")` plugin configured.
- `GET /me`, `PUT /me/settings`, `PUT /me/password`.
- Tests: `MeRouteTest`.

### 4e — Taxonomy + interests
- `GET /categories`: reads CATEGORY tags from DB, returns `List<CategoryDto>`.
- `GET /me/interests`, `PUT /me/interests`: reads/writes `user_tag_percent` for CATEGORY-kind tags.  
  Initial onboarding: `PUT /me/interests` with selected category ids → sets each to weight 50 via `TagPercentService.setUserTagPercent`.
- Tests: `InterestsRouteTest`.

### 4f — Feed endpoint
- `FeedService.kt` (Kotlin, `:server`): keyset-cursor query JOINing `article`, `article_tag_percent`, `user_tag_percent`, `user_block`, `report`; computes score per article as `SUM(atp.percent * utp.percent)` over **all tag kinds** (ENTITY, CATEGORY, SUBTOPIC — not filtered by kind); orders by `(score DESC, article.id DESC)`; uses `(score, id) < (cursor_score, cursor_id)` for the next page.
- **Only classified articles:** WHERE `article.prompt_version IS NOT NULL`.
- **Exclude:** articles the user has complained about or blocked.
- **No matching weights fallback:** if no `user_tag_percent` rows exist for the user, skip the score join and return newest-first (`ORDER BY article.id DESC`).
- Tests: `FeedRouteTest` — ordering, cursor, filters, blocked exclusion, no-weights fallback.

### 4g — Article actions (rate / complain)
- Route handlers calling existing `ReportService.upsertRating` / `ReportService.complain`.
- **Drop `/articles/{id}/opened`** — deferred indefinitely.
- Tests: `RatingRouteTest`.

### 4i — HTTPS + self-signed CA
- OpenSSL steps documented in `docs/tls-setup.md` (template committed; actual keys outside git).
- `server/config/server.properties` gains `server.https.port`, `server.keystore.path`, `server.keystore.password`.
- `ServerMain.kt`: add `sslConnector { ... }` block when keystore path is set.
- Verify with `curl --cacert ca.crt https://localhost:8443/health`.

### 4h (reduced) — Admin endpoints
- RBAC middleware: `fun ApplicationCall.requireRole(vararg roles: Role)` — reads JWT claim, throws 403.
- **ADMIN only:** article update/delete, user management, audit log, pipeline stats.
- **MODERATOR:** complaints list + hide article (PUT `/admin/articles/{id}` with `status=HIDDEN`).
- Tests: `RbacTest`.

_Steps 4j (validation DSL) and notifications/OpenAPI deferred to Stage 5._

---

## 8. Open Questions — Resolved

1. **Interests / onboarding flow:** ✅ `PUT /me/interests` is callable any time. Selected categories → `max(current_weight, 50)`; deselected → `0`. No onboarding gate needed.

2. **Score formula in SQL:** ✅ Score over **all tag kinds** (ENTITY, CATEGORY, SUBTOPIC). No kind filter.

3. **`/articles/{id}/opened` endpoint:** ✅ Dropped — deferred indefinitely.

4. **Admin roles:** ✅ MODERATOR: complaints list + hide article (PUT with `status=HIDDEN`). Everything else (user management, delete, audit, stats) is ADMIN only.

5. **Notifications / OpenAPI:** ✅ Deferred to Stage 5.

6. **`server.properties` location:** ✅ `server/config/server.properties` (gitignored); `server/config/server.properties.example` committed with placeholders.

7. **Ktor version:** ✅ 3.6.1 confirmed.

---

## 9. Client Notes for Stage 5

**Refresh token calls must be serialized (single-flight) on the Android client.**

The server uses `SELECT … FOR UPDATE` pessimistic locking on the refresh-token row so that two simultaneous refresh requests with the same token cannot both succeed. However, if the Android client retries a refresh call (e.g. on a network timeout where the server already committed) and the first request was actually processed, the retry presents a token that was already rotated. The server interprets this as token reuse, revokes the entire refresh family, and returns 403 — logging the user out.

**Consequence:** Use a single-flight pattern (e.g. a `Mutex`-guarded `Deferred`) so that only one refresh RPC is in-flight at a time per account. All callers that triggered a refresh wait on the same in-flight call and share its result. Never retry a refresh independently from multiple call sites.
