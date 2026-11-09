# Running the NewsDove Server

## Prerequisites

- JDK 25 installed (Eclipse Temurin `jdk-25.0.2.10-hotspot`)
- MySQL running (`net start MySQL80`)
- Schema up to date (`./gradlew :core:run --args="--migrate"` after every new migration)
- `server/config/server.properties` present (copy from `server.properties.example` and fill in values)

## Required config keys (`server/config/server.properties`)

| Key | Description |
|-----|-------------|
| `server.port` | HTTP port (plain, for `/health` only) |
| `server.https.port` | HTTPS port (e.g. `8443`) |
| `server.keystore.path` | Absolute path to `newsdove.p12` |
| `server.keystore.alias` | Alias used when creating the keystore |
| `jwt.secret` | JWT signing secret (min 32 bytes) |

Passwords and secrets are **never** committed to git.

## Environment variables

| Variable | Purpose |
|----------|---------|
| `NEWSDOVE_JWT_SECRET` | Alternative to `jwt.secret` in `server.properties` |
| `NEWSDOVE_KEYSTORE_PASSWORD` | Keystore password — copy from `C:\newsdove_tls\.keystore_pass_DO_NOT_COMMIT` |

Point the server at a custom properties file with the system property:
```
-Dnewsdove.server.config=/path/to/server.properties
```

## Starting the server

```bash
# From coursework/ (Gradle root)
./gradlew :server:run
```

The server binds on both ports simultaneously.  
Plain HTTP requests to any path except `/health` return **403 HTTPS_REQUIRED**.

## Bootstrapping the first admin

Before any admin panel is accessible, at least one user must have role `ADMIN`.  
Run this once after the user has registered via the app:

```bash
./gradlew :core:run --args="--make-admin <nick>"
```

The command refuses to run against any database whose URL does not contain
`db_use_test` or `db_use_test_it` (safety guard inherited from HeadlessMain).
To promote a user on `db_use`, call the SQL directly after taking a dump.

## Curl smoke tests (HTTPS with self-signed CA)

Replace `<CA>` with the path to `ca.crt` (e.g. `C:\newsdove_tls\ca.crt`), `<HOST>` with the
server's LAN IP, and `<TOKEN>` with the access token returned by login.

> **`--ssl-no-revoke`** is required on Windows: `curl.exe` uses Schannel, which tries to check
> certificate revocation (CRL/OCSP) and fails for a private CA that has no published endpoint.

```bash
# Health (plain HTTP, no auth required)
curl.exe http://<HOST>:8080/health

# Register
curl.exe --ssl-no-revoke --cacert <CA> -X POST https://<HOST>:8443/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d "{\"nick\":\"demo\",\"password\":\"Password1!\",\"languages\":[\"en\"],\"regions\":[]}"

# Login
curl.exe --ssl-no-revoke --cacert <CA> -X POST https://<HOST>:8443/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"nick\":\"demo\",\"password\":\"Password1!\"}"

# Me
curl.exe --ssl-no-revoke --cacert <CA> https://<HOST>:8443/api/v1/me \
  -H "Authorization: Bearer <TOKEN>"

# Feed
curl.exe --ssl-no-revoke --cacert <CA> "https://<HOST>:8443/api/v1/feed" \
  -H "Authorization: Bearer <TOKEN>"

# Rate an article (replace 1 with a real article id from /feed)
curl.exe --ssl-no-revoke --cacert <CA> -X POST https://<HOST>:8443/api/v1/articles/1/rate \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d "{\"rating\":1}"
```
