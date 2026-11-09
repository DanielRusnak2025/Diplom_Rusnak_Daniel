# Plan: Stage 5 — Android Client

## 1. Build Setup Decision

### Recommendation: `:androidApp` inside this monorepo (Gradle 9.8 / JDK 25)

**Compatibility check (all requirements met):**

| Requirement | Required | Monorepo | Status |
|------------|---------|----------|--------|
| Gradle | ≥ 9.6.0 | 9.8 | ✓ |
| JDK | ≥ 17 | 25 | ✓ |
| Kotlin | 2.x (AGP 9.x) | 2.4.21 | ✓ |

Source: https://developer.android.com/build/releases/gradle-plugin (fetched 2026-11-09).
AGP 9.5.1 is the latest stable; its compatibility table lists Gradle 9.6.0 minimum and JDK 17 minimum.

**Why monorepo:**
- `:shared` is available immediately — no `includeBuild` or publishing overhead.
- Single version catalog (`gradle/libs.versions.toml`), single repo.
- AGP 9.5.1 + Gradle 9.8 is a supported combination (not an edge case).
- Diploma demo → build simplicity matters more than toolchain separation.

**One required setup step:** in Android Studio → Settings → Build, Execution, Deployment → Build Tools →
Gradle → Gradle JDK — set to the same JDK 25 path used by the monorepo.
Or add `org.gradle.java.home=C:/Program Files/Eclipse Adoptium/jdk-25.0.2.10-hotspot` to the
root `gradle.properties` (gitignored line, so each developer sets it locally).

**Fallback (if JDK 25 causes unexpected Android toolchain failures):**
Create a sibling Android project with `includeBuild("../coursework")` to substitute `:shared`.
Only switch if concrete errors appear; do not plan for a hypothetical.

---

## 2. Exact Versions

All versions below were looked up from live pages on 2026-11-09.

| Library | Version | Source |
|---------|---------|--------|
| AGP (`com.android.application`) | **9.5.1** | developer.android.com/build/releases/gradle-plugin |
| Compose BOM | **2026.09.00** | developer.android.com/jetpack/compose/bom/bom-mapping |
| ↳ Compose UI (`androidx.compose.ui:ui`) | **1.12.1** | (via BOM) |
| ↳ Material3 (`androidx.compose.material3:material3`) | **1.4.0** | (via BOM) |
| Navigation Compose (`androidx.navigation:navigation-compose`) | **2.10.2** | developer.android.com/jetpack/androidx/releases/navigation |
| Lifecycle (`androidx.lifecycle:lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`) | **2.11.0** | developer.android.com/jetpack/androidx/releases/lifecycle |
| DataStore Preferences (`androidx.datastore:datastore-preferences`) | **1.3.0** | developer.android.com/jetpack/androidx/releases/datastore |
| Ktor client (all `io.ktor:ktor-client-*`) | **3.6.1** | ktor.io/changelog/3.6/ |
| Kotlin | **2.4.21** | gradle/libs.versions.toml (already in project) |
| Kotlin serialization | **1.12.0** | gradle/libs.versions.toml (already in project) |
| `androidx.browser` (Custom Tabs) | **1.10.0** | developer.android.com/jetpack/androidx/releases/browser (fetched 2026-11-09) |
| MockK | **1.14.9** | gradle/libs.versions.toml (already in project) |
| `ktor-client-mock` (MockEngine) | **3.6.1** | same artifact group as Ktor client |

**Do not add:** `androidx.security:security-crypto` — version 1.1.0 is the last stable release but
all APIs are deprecated in it ("deprecated in favour of existing platform APIs and direct use of
Android Keystore"). Source: developer.android.com/jetpack/androidx/releases/security (fetched 2026-11-09).

**compileSdk:** Compose UI 1.12.1 (from BOM 2026.09.00) requires `compileSdk 37` and AGP 9.
Source: developer.android.com/develop/ui/compose/compiler (fetched 2026-11-09).

**Kotlin plugins for `androidApp`:** Apply ONLY:
- `org.jetbrains.kotlin.plugin.compose` — Compose compiler; version must match Kotlin (2.4.21).
  Required for Kotlin 2.0+. Source: developer.android.com/develop/ui/compose/compiler (fetched 2026-11-09).

**Do NOT apply `org.jetbrains.kotlin.android`.** Since AGP 9.0, Kotlin support is built into AGP.
Applying the plugin alongside AGP 9 fails the build: "Cannot add extension with name 'kotlin'" /
"no longer required for Kotlin support since AGP 9.0". Source: official "Migrate to built-in Kotlin"
page, verified by user 2026-11-09.

---

## 3. Token Storage

### Approach: Android Keystore + AES-256-GCM + DataStore Preferences

`EncryptedSharedPreferences` and `EncryptedFile` from `security-crypto` are deprecated; do not use.

**Implementation:**
1. On first run, generate a symmetric AES-256-GCM key in the **Android Keystore** via
   `KeyGenerator.getInstance("AES", "AndroidKeyStore")` with `KeyGenParameterSpec` configured for
   `PURPOSE_ENCRYPT | PURPOSE_DECRYPT`. The key never leaves the secure element.
2. Encrypt the access token and refresh token separately: each stored as `base64(iv) + "." + base64(ciphertext)`.
3. Store the two encrypted strings in **DataStore Preferences 1.3.0** (typed, coroutine-based, atomic writes).
4. `TokenRepository` exposes `Flow<TokenPair?>` (null = not logged in) and `suspend fun store(...)` / `suspend fun clear()`.

### Ktor bearer auth and concurrent refresh

Ktor 3.6.1's `bearerTokens { ... }` plugin serializes concurrent refresh calls automatically.
Verbatim from ktor.io/docs/client-bearer-auth.html (fetched 2026-11-09, full page, HTTP 200):

> "When multiple requests fail with `401 Unauthorized` at the same time, the client performs the
> token refresh only once. The first request that receives the `401` response triggers the
> `refreshTokens {}` callback. Other requests wait for the refresh operation to complete and are
> then retried with the new token."

This is sourced. A **MockEngine test in step 5b** will confirm this behaviour holds in practice:
5 concurrent requests → server returns 401 → `/auth/refresh` called exactly once → all 5 retried
with the new token. If the test fails (plugin does not serialize), add a `Mutex`-based single-flight
wrapper around the refresh call and re-run the test.

**403 `TOKEN_REUSED`** must be handled outside the bearer plugin (the plugin only catches 401):
in a response interceptor or `HttpResponseValidator`, detect `code == "TOKEN_REUSED"` →
call `tokenRepository.clear()` → emit a logout event consumed by the root ViewModel → pop back stack
→ navigate to Login. Test: MockEngine returns `403 TOKEN_REUSED` → verify tokens are cleared and
the logout side-effect fires.

---

## 4. TLS

### CA trust (bundled, no user CAs)

Copy `C:\newsdove_tls\ca.crt` → `androidApp/src/main/res/raw/newsdove_ca.crt`.

`androidApp/src/main/res/xml/network_security_config.xml`:
```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="@raw/newsdove_ca"/>
            <!-- no src="system" — only the bundled CA is trusted -->
        </trust-anchors>
    </base-config>
</network-security-config>
```

Reference in `AndroidManifest.xml`:
```xml
<application android:networkSecurityConfig="@xml/network_security_config" ...>
```

The server cert already covers all three connection scenarios (from docs/tls-setup.md Step 5):

| Scenario | App base URL | SAN entry |
|----------|-------------|-----------|
| USB demo (adb reverse) | `https://localhost:8443` | `DNS:localhost` |
| Android emulator | `https://10.0.2.2:8443` | `IP:10.0.2.2` |
| Wi-Fi (LAN) | `https://192.168.0.143:8443` | `IP:192.168.0.143` |

### Base URL from `local.properties` → `BuildConfig`

`local.properties` (gitignored — set per developer/session):
```
newsdove.base.url=https://localhost:8443
```

`androidApp/build.gradle.kts`:
```kotlin
val baseUrl = project.findProperty("newsdove.base.url") ?: "https://10.0.2.2:8443"
buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
```

---

## 5. Screens and Navigation

**Nav graph** (single Activity, Compose Navigation 2.10.2, type-safe routes):

```
Auth graph (not logged in):
  Login ←→ Register

Onboarding (after first registration only):
  Onboarding (languages → regions → interests)

Main graph (logged in):
  BottomNav:
    Feed
    Settings
  ADMIN only (shown conditionally in bottom nav):
    ModeratorPanel
```

**Feed screen:**
- Cursor-based paging: `GET /api/v1/feed?after=<lastId>&limit=20`; lazy-load on scroll end.
- Article card: title, publisher, language tag, recommendation score, Like / Dislike buttons.
- Long-press → Complain bottom sheet (reason code radio group + confirm).
- Tap → open URL in **Custom Tabs** (no article text stored — rule 4).
- Like / Dislike: optimistic UI update; revert card state on API error.

**States:** every screen must handle Loading, Empty, and Error (with retry button) states explicitly.

**Auth error handling:**
- 401 → Ktor bearer plugin triggers `refreshTokens {}`, retries automatically.
- 403 `TOKEN_REUSED` (refresh token replayed) → clear tokens in `TokenRepository` → pop entire
  back stack → navigate to Login.
- 403 `HTTPS_REQUIRED` → log error + show "Server misconfiguration" toast (should never happen).

**Settings screen:**
- Display and edit language/region preferences (`GET/PUT /api/v1/me`).
- Adjust interests: show the 15 categories from `GET /api/v1/categories` with +/− weight controls.
- Change password (`POST /api/v1/auth/change-password`).
- Logout: call `DELETE /api/v1/auth/logout`, clear local tokens.

**Moderator screen (ADMIN only, not cached):**
- Article list with Hide / Unhide action.
- User list with Block / Unblock and role-change actions.
- Complaint queue with article context and resolution action.
- Loaded on demand, never stored in Room.

---

## 6. Architecture

```
Composables (UI only — no logic)
    ↓ observes StateFlow
ViewModel (UI state, side effects)
    ↓ calls suspend funs
Repository interface
    ↓ implemented by
ApiClient (Ktor HttpClient — OkHttp engine)
    ↓ serializes with
:shared (kotlinx.serialization DTOs)
```

**Rules:**
- No business logic in Composables. No data fetching in Composables.
- Repository is an interface; `ApiRepository` implements it; tests use `FakeRepository`.
- One `HttpClient` instance per app lifetime (single bearer-auth config, single TLS config).
- No Room. No offline cache (consistent with rule 4 and no-admin-cache requirement).
- Dependency injection by constructor — no Hilt for the diploma scope; wire manually in `Application`.

---

## 7. Tests

| Layer | Tools | What is tested |
|-------|-------|---------------|
| ViewModel | JUnit 6, MockK, `kotlinx-coroutines-test` | state machine (loading → success → error), 403 TOKEN_REUSED → logout side effect fires, tokens cleared |
| ApiClient — bearer serialization | JUnit 6, `ktor-client-mock` (MockEngine) | 5 concurrent requests all receive 401 → `/auth/refresh` called exactly once → all 5 retried with new token; if fails → add Mutex |
| ApiClient — general | JUnit 6, `ktor-client-mock` (MockEngine) | URL construction, auth headers, request body shape, response parsing, error codes → typed exceptions |
| TokenRepository | JUnit 6 on JVM, mock Cipher | DataStore read/write round-trip, `clear()` wipes tokens |

No instrumented tests in Stage 5. All tests run on the JVM (`./gradlew :androidApp:test`).

---

## 8. Step Breakdown

### 5a — Project skeleton + build wiring
- Add `androidApp/` module; apply AGP 9.5.1 and `org.jetbrains.kotlin.plugin.compose` (version 2.4.21).
  Do NOT apply `org.jetbrains.kotlin.android` — it fails the build with AGP 9 (see §2).
  Set `compileSdk 37`, `minSdk 26`.
- Add `:shared` as a dependency.
- Create `network_security_config.xml` (bundled CA only) and reference in manifest.
- Wire `local.properties` → `BuildConfig.BASE_URL`.
- Deliverable: `./gradlew :androidApp:assembleDebug` succeeds; app installs and shows a blank screen.

### 5b — API client + token storage
- Configure `HttpClient` (OkHttp engine, bearer auth plugin, kotlinx-serialization content-negotiation).
- Add `HttpResponseValidator` to detect `403 TOKEN_REUSED` → emit logout event.
- Implement Android Keystore encrypt/decrypt helpers.
- Implement `TokenRepository` (DataStore 1.3.0 backed).
- Implement `AuthApi` (register, login, refresh, me, logout).
- Write MockEngine tests:
  - **Bearer serialization:** 5 concurrent coroutines each make a request; MockEngine returns 401
    for the first batch and 200 for the retry; assert `/auth/refresh` was called exactly once.
    If this test fails, add a `Mutex`-based single-flight wrapper.
  - **403 TOKEN_REUSED:** single request returns `403 {"code":"TOKEN_REUSED"}`; assert the logout
    side-effect fires and `tokenRepository.clear()` was called.
- Deliverable: all MockEngine tests pass; `TokenRepository` JVM test passes.

### 5c — Login / Register + onboarding
- Login and Register screens + ViewModels.
- Onboarding: language → region → interests (show 15 categories from `GET /api/v1/categories`,
  no subtopics; submit selection via `POST /api/v1/me`).
- Navigate to Feed on success; show inline error on failure.
- Deliverable: manual smoke test via `adb reverse tcp:8443 tcp:8443`; register + login work end-to-end.

### 5d — Feed screen
- Cursor-based paging, article cards.
- Like / Dislike (optimistic), Complain dialog.
- Custom Tabs for article links.
- 401 → automatic refresh → retry.
- Deliverable: feed displays articles; interactions recorded on the server.

### 5e — Settings screen
- Language / region / interests preferences (round-trip with /api/v1/me).
- Change password.
- Logout.
- Deliverable: settings changes survive app restart; logout clears tokens.

### 5f — Moderator screen
- Admin-gated navigation (role check from `/api/v1/me`).
- Hide/unhide articles, block/unblock users, complaint queue.
- Deliverable: admin flow smoke-tested with `--make-admin`-bootstrapped account.

---

## Decisions (resolved 2026-11-09)

1. **`minSdk`:** 26 (Android 8.0).
2. **Layout:** Phone portrait only. No adaptive layout for the diploma demo.
3. **Offline:** Error screen + Retry button. No stale feed, no Room.
4. **Onboarding interests:** Show the 15 categories from `GET /api/v1/categories` without subtopics.
5. **Module location:** `androidApp/` at the monorepo root (alongside `core/server/shared`).
