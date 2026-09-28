# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**NewsDove** — diploma thesis. An email newsletter news aggregator: emails from subscribed publishers are fetched via Gmail IMAP, links extracted, articles classified by a local AI (Ollama), and served to users as a personalized feed with a recommendation score.

**Current state (legacy):** Java 24 + JavaFX 25 desktop app, Maven, Hibernate 7, MySQL, Ollama Llama3.2.  
**Target (diploma):** Ktor server (laptop) ↔ Android app (Jetpack Compose), Gradle Kotlin DSL monorepo with modules `core`, `server`, `shared`, `androidApp`. The Java logic moves to `core`; all new code is Kotlin.

The full migration plan with stage-by-stage checklists and completion criteria lives in `ROADMAP.md` (root of this directory). Read it before starting any task.

---

## Non-negotiable Rules

1. **No destructive DB operations.** All migrations are additive. Always dump first; run on `db_use_test` before `db_use`. Verify row counts and FK integrity before touching production. `db_use` contains real articles — never alter its schema or data without explicit permission.
2. **Secrets never in git.** DB password, Gmail app-password, JWT key, TLS keys → env vars or a local file outside the repo. Files already gitignored: `email.properties`, `hibernate.cfg.xml`, `ai.properties`. If a secret is found anywhere — report location with masked value, do not print the full value.
3. **All AI is local.** Only Ollama. No cloud API calls.
4. **No article text stored or displayed.** The app shows only: title, tags, publisher, language, score. Articles open in an external browser via URL.
5. **Before any irreversible or destructive action → describe the plan and wait for confirmation.**
6. **Small steps.** After each step — a brief report: what was done, what was verified, what remains. If anything is unclear or conflicts with ROADMAP.md — ask, don't guess.
7. **Push to remote only after explicit user confirmation ("подтверждаю").**
8. **Before any step that may change data in db_use_test** — take a fresh dump first:
   `mysqldump --login-path=newsdove_dev --no-tablespaces --single-transaction --routines --triggers --default-character-set=utf8mb4 --result-file=C:\newsdove_backups\db_use_test_<YYYY-MM-DD>_<HH-MM>.sql db_use_test`
9. **One way to run the app.** After launch, check the first `[Config]` log line — it must contain `db.url=...db_use_test`. If the line is absent or the DB is not `db_use_test` — close the app immediately and investigate.
10. **Any decrease in row count in any table → STOP and report** before doing anything else.

---

## Build & Run (current Maven project)

**Requires JDK 25** (Eclipse Temurin `jdk-25.0.2.10-hotspot` at `C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot`). Maven: `B:\apache-maven-3.9.12\bin\mvn.cmd`.  
All commands run from `coursework/Project/hibernate/`.

```bash
mvn compile            # compile
mvn package            # build jar
mvn test               # run all tests
mvn test -Dtest=TagPercentServiceTest   # run single test class
```

**Start infrastructure before running the app:**

```bash
net start MySQL80      # start MySQL service (Windows)
ollama pull llama3.2   # first-time model download
ollama serve           # Ollama is auto-started by the CLI; or run explicitly
```

**Required local config files** (not in git; create from templates):

- `src/main/resources/hibernate.cfg.xml` — DB connection (host, schema `db_use`, credentials)
- `src/main/resources/email.properties` — Gmail IMAP address + app-password
- `src/main/resources/ai.properties` — Ollama endpoint (default: `http://localhost:11434`)

---

## Architecture (current Java app)

```
Presentation  org.example.controller  (JavaFX controllers, FXML)
Service       org.example.service     (business logic, scoring)
DAO           org.example.dao / .impl (Hibernate, generic + specific)
Infra         org.example.ai / email / scheduler / util
Entities      org.example (top-level package)
```

**Email pipeline** (background, every 30 min, 5-thread pool):

```
EmailScheduler
  └─ EmailReader → LinkExtractor → LinkFilter
      └─ ArticleExtractor (OkHttp → Ollama /api/chat → ExtractedArticle)
          └─ ArticleSaver (dedup by link+title, getOrCreate tags, write article_tag_percent)
```

**JavaFX coupling in pipeline:** `EmailScheduler` calls `Platform.runLater(controller::refreshArticles)` and holds a reference to `SearchByKeywordController.getInstance()` (static singleton). This coupling must be removed when moving the pipeline to the `core` module.

**AI JSON parsing:** The JSON schema is embedded only in the prompt text — Ollama's `format` parameter is NOT used. The response is stripped of ` ```json ``` ` fences, the first `{...}` block is extracted, and Jackson deserializes it. On any parse exception the article is silently dropped (no retry). If the extracted title is not in English, a second Ollama call translates it (Lingua language detector determines language; if not English → second `/api/chat` call → translated title replaces original before saving).

**Recommendation score** (TagPercentService):

```
Score = Σ(userInterest[tag] × articleWeight[tag]) / Σ|articleWeight[tag]|   → range −100..+100
```

Weight update on rating: Like → +delta, Dislike → −delta, Neutral → random ±1–3 nudge (`ThreadLocalRandom.nextInt(1,4)` with random sign — **known issue**: non-deterministic, to be replaced with deterministic decay in Stage 1), Complaint → no weight change.

**Database:** MySQL `db_use` (test copy: `db_use_test`). Hibernate `hbm2ddl=update` in legacy; Flyway migrations target the diploma version.  
**Ollama endpoint:** `http://localhost:11434/api/chat`, model `llama3.2`, `OLLAMA_NUM_PARALLEL=5`.  
**Gmail IMAP:** `imap.gmail.com:993` (SSL), app-password auth.

---

## Key Design Constraints for New Code

- All HQL queries must use named parameters — no string concatenation.
- Hibernate entities must not cross the server boundary; use DTOs from `shared` (kotlinx.serialization).
- Services must accept `userId` as a parameter; no global "current user" state.
- The `ArticleClassifier` interface (Stage 3) is the only entry point to AI — `OllamaClassifier` is the implementation. Future models just implement the interface.
- RBAC enforced server-side on every admin endpoint; the Android app navigates by role but the server always re-checks.
- Tokens stored encrypted with a key from Android Keystore; admin data never cached in Room.
