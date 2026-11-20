# NewsDove — Application Layer Architecture Documentation

> **Project:** NewsDove — Email Newsletter News Aggregator  
> **Stack:** Java 24 · JavaFX 25 · Hibernate 7 · MySQL · Ollama Llama3.2 · Jakarta Mail  
> **Date:** 2026-05-12

---

## 1. High-Level Architecture Overview

NewsDove is a **desktop application** built on a 4-layer architecture:

```
┌─────────────────────────────────────────────────────────────────────┐
│                     PRESENTATION LAYER                              │
│         JavaFX Controllers + FXML (UI Components)                  │
├─────────────────────────────────────────────────────────────────────┤
│                     SERVICE LAYER                                   │
│         Business Logic + Orchestration + Scoring                   │
├─────────────────────────────────────────────────────────────────────┤
│                   DATA ACCESS LAYER                                 │
│         Hibernate DAOs (Generic + Specific)                        │
├─────────────────────────────────────────────────────────────────────┤
│                  INFRASTRUCTURE LAYER                               │
│         MySQL DB · Ollama AI · Gmail IMAP · Scheduler              │
└─────────────────────────────────────────────────────────────────────┘
```

Additionally, a **Pipeline subsystem** (Email → AI → DB) operates as a background thread independent of the UI.

---

## 2. Layer Definitions

### 2.1 Presentation Layer

**Package:** `org.example.controller`  
**Technology:** JavaFX 25, FXML  
**Role:** Renders UI, captures user events, delegates logic to Service layer.

| Controller | FXML File | Resolution | Access | Purpose |
|---|---|---|---|---|
| `LoginController` | `Login.fxml` | 520×932 | All users | Login + article carousel |
| `RegistrationController` | `Registration.fxml` | 430×932 | Guests | Account creation (step 1/3) |
| `LanguageRegionController` | `LanguageRegion.fxml` | 430×932 | New users | Preferences (step 2/3) |
| `CategoriesController` | `Categories.fxml` | 430×932 | New users | Tag interests (step 3/3) |
| `MainScreenController` | `MainScreen.fxml` | 520×932 | Regular users | Personalized news feed |
| `SearchByKeywordController` | `SearchByKeyword.fxml` | 1280×800 | Admins only | Article CRUD |
| `SearchByUsernameController` | `SearchByUsername.fxml` | 1280×800 | Admins only | User management |
| `SearchByReportsController` | `SearchByReports.fxml` | 1280×800 | Admins only | Moderation / complaints |

#### Navigation Flow

```
Login ──► [role=ADMIN]  ──► SearchByKeyword (CRUD Panel)
      │                              │
      │                      SearchByUsername
      │                              │
      │                      SearchByReports
      │
      └► [role=USER] ──► Registration ──► LanguageRegion ──► Categories ──► MainScreen
                                                                                      │
                                                                               (re-entry on login)
```

#### MainScreen UI Regions

```
┌──────────────────────────────────────┐
│  [◉] Sidebar Pill (43px)             │  ← user icon (initial), logout
├──────────────────────────────────────┤
│  [Lang] [Newsroom] [⚙]              │  ← filter row 1
│  [🔍] [Categories] [Region] [✕]    │  ← filter row 2
│  [🔔 N new articles available  ↻]   │  ← polling banner (conditional)
│  [🔥 N articles liked by 50%+]      │  ← popularity banner (conditional)
│  Found: N articles                   │
├──────────────────────────────────────┤
│  ┌────────────────────────────────┐  │
│  │  Article Card                  │  │  ← title, language badge, tags,
│  │  [EN] Main theme · Sub theme  │  │     Match score (e.g. +72%)
│  │  Match: +72%  🔥 84% liked   │  │     🔥 green border if popular
│  │  [👍] [✓] [👎] [⚠]          │  │  ← rating buttons (after click)
│  └────────────────────────────────┘  │
│  (scrollable list)                   │
├──────────────────────────────────────┤
│  [settingsOverlay / logoutOverlay]   │  ← AnchorPane overlays
└──────────────────────────────────────┘
```

---

### 2.2 Service Layer

**Package:** `org.example.service`  
**Role:** Encapsulates business rules, transactions, and cross-DAO coordination.

| Service | Key Responsibilities |
|---|---|
| `UserService` | Registration (with/without email), authentication, preferences update, notification frequency |
| `ArticleService` | Keyword search, recent articles, ID-based queries, new article count for polling |
| `TagService` | Tag lookup, `getOrCreate` (idempotent), top popular tags |
| `TagPercentService` | User interest weights (read/write/adjust), article-tag weights, scoring algorithm |
| `ReportService` | Upsert rating (like/neutral/dislike/complaint), per-article aggregations, like-percentage map |

#### Recommendation Scoring (TagPercentService)

```
Score = ( Σ userInterest[tag] × articleWeight[tag] ) / ( Σ |articleWeight[tag]| )

Range: -100 .. +100
Displayed as: "Match: +72%" (green) / "-15%" (red) / "0%" (grey)
```

Tag weight adjustment on rating:
- **Like (+1):** delta = +max(1, 5 × tagPercent/100)
- **Dislike (−1):** delta = −max(1, 5 × tagPercent/100)
- **Neutral (0):** weight nudged 10% toward zero
- **Complaint (−2):** no tag weight change (only saves report)

---

### 2.3 Data Access Layer

**Package:** `org.example.dao` + `org.example.dao.impl`  
**Technology:** Hibernate 7 ORM, HQL, SessionFactory (singleton)

#### Generic DAO Pattern

```
GenericDAO<T, ID>  (interface)
    save(T) / update(T) / delete(T) / deleteById(ID)
    findById(ID) / findAll() / existsById(ID) / count()
         ↑ implemented by
GenericDAOImpl<T, ID>  (abstract class, manages Session/Transaction)
         ↑ extended by
  UserDAOImpl · ArticleDAOImpl · TagDAOImpl
  ReportDAOImpl · UserTagPercentDAOImpl · ArticleTagPercentDAOImpl
```

#### DAO Custom Methods Summary

**ArticleDAOImpl:**
- `findRecentWithTags(limit)` — 2-step query to avoid Cartesian product
- `findByTitle(title)` — deduplication check
- `findByLink(link)` — deduplication check
- `countNewerThan(afterId)` — polling: how many new articles exist
- `findIdsNewerThan(afterId)` — threshold-based notification filtering
- `searchByKeywordAndDate(keyword, date)` — admin keyword search
- `findByIds(Collection<Integer>)` — batch load with tags

**ReportDAOImpl:**
- `findByUserAndArticle(user, article)` — upsert check
- `findRatedArticleIds(user)` — filter already-rated articles from feed
- `findLikePercentageByArticleIds(ids)` — single SQL aggregation: `SUM(rating=1)/COUNT(*)×100` per article

**UserTagPercentDAOImpl:**
- All queries use `JOIN FETCH utp.tag` — avoids `LazyInitializationException`

**ArticleTagPercentDAOImpl:**
- `findByArticle(article)` — uses `JOIN FETCH atp.tag ORDER BY percent DESC`

---

### 2.4 Infrastructure Layer

#### 2.4.1 Database (MySQL)

**Host:** localhost:3306  
**Schema:** db_use  
**DDL:** `hbm2ddl.auto=update` (Hibernate auto-manages schema)

##### Entity-Relationship Diagram

```
┌──────────────┐         ┌───────────────────────┐         ┌──────────────┐
│    user      │         │   user_tag_percent    │         │    tag       │
│──────────────│         │───────────────────────│         │──────────────│
│ nick (PK)    │◄────────│ nick (FK, PK)         │────────►│ id (PK)      │
│ password     │         │ tag_id (FK, PK)       │         │ name (UNIQUE)│
│ email        │         │ percent (0-100)       │         └──────┬───────┘
│ region       │         └───────────────────────┘                │
│ language     │                                                   │
│ role         │         ┌───────────────────────┐                │
│ notif_freq   │         │ article_tag_percent   │                │
└──────┬───────┘         │───────────────────────│                │
       │                 │ article_id (FK, PK)   │◄───────────────┘
       │                 │ tag_id (FK, PK)       │
       │                 │ percent (0-100)       │
       │                 └───────────┬───────────┘
       │                             │
       │                 ┌───────────▼───────────┐
       │                 │       article         │
       │                 │───────────────────────│
       │                 │ id (PK, auto)         │
       │                 │ title                 │
       │                 │ link (URL)            │
       │                 │ article_language      │
       │                 │ article_region        │
       │                 │ publication_date      │
       │                 │ read_count            │
       │                 │ publisher             │
       │                 └───────────┬───────────┘
       │                             │
       │          ┌──────────────────┴───────────┐
       │          │          report              │
       └─────────►│──────────────────────────────│
                  │ id (PK, auto)                │
                  │ user_nick (FK)               │
                  │ article_id (FK)              │
                  │ rating (-2/-1/0/+1)          │
                  │ reason (TEXT)                │
                  │ date                         │
                  │ UNIQUE(user_nick, article_id)│
                  └──────────────────────────────┘
```

##### Rating Values

| Value | Meaning | Effect on Tags | Appears in |
|---|---|---|---|
| +1 | Like | +delta | Grade (admin), feed hidden |
| 0 | Neutral | nudge to 0 | Grade (admin), feed hidden |
| −1 | Dislike | −delta | Grade (admin), feed hidden |
| −2 | Complaint/Block | none | Blocks (admin), SearchByReports |

#### 2.4.2 AI Engine (Ollama / Llama3.2)

**Endpoint:** `http://localhost:11434/api/chat`  
**Model:** `llama3.2`  
**Env var for parallelism:** `OLLAMA_NUM_PARALLEL=5`

##### AI Request/Response Cycle

```
URL
 │
 ▼
ArticleFetcher.fetchArticle(url)
 │  jsoup → HTTP GET → follow redirects → extract <article>/<main>/<body> text
 │  returns: FetchResult { text: String, finalUrl: String }
 │
 ▼
ArticleExtractor.extract(url, existingTagNames)
 │  1. Fetch text (ArticleFetcher)
 │  2. Filter finalUrl through LinkFilter (skip non-articles)
 │  3. Build Ollama prompt:
 │     - Article text (max 2000 chars)
 │     - Existing tags from DB (reuse before creating new)
 │     - Instructions: JSON with title/language/region/publication/tags[]
 │  4. POST to Ollama API (OkHttp, 30s write / 120s read timeout)
 │  5. Parse JSON → ExtractedArticle (Jackson)
 │  returns: ExtractedArticle | null
 │
 ▼
ExtractedArticle { title, url, language, region, publication, tags[{name, percent}] }
```

##### AI JSON Output Schema

```json
{
  "title": "Article headline (English)",
  "url": "https://canonical-url.com/article",
  "language": "English",
  "region": "Middle East",
  "publication": "Reuters",
  "tags": [
    { "name": "israel",        "percent": 25 },
    { "name": "war",           "percent": 20 },
    { "name": "donald_trump",  "percent": 20 },
    { "name": "gaza",          "percent": 20 },
    { "name": "diplomacy",     "percent": 15 }
  ]
}
```

#### 2.4.3 Email (Gmail IMAP)

**Server:** `imap.gmail.com:993` (SSL)  
**Account:** `usmprojectjakarta@gmail.com`  
**Auth:** App Password (not OAuth; loaded from `email.properties`)  
**Library:** Jakarta Mail

```
EmailConfig          loads email.properties (address + app-password)
EmailReader          connects to Gmail, reads INBOX UNREAD messages
EmailMessage (DTO)   { subject, body, htmlBody, from, receivedDate }
```

#### 2.4.4 Background Scheduler (EmailScheduler)

**Interval:** Every 30 minutes  
**Thread pools:**
- `scheduler`: 1-thread `ScheduledExecutorService` (30 min timer)
- `articleExecutor`: `newFixedThreadPool(5)` for parallel AI calls

```
EmailScheduler.runPipeline()
│
├── EmailReader.readAndProcess()         ← marks emails as READ after processing
│   └── for each email:
│       ├── LinkExtractor.extractLinks() ← plain text regex + HTML href
│       └── LinkFilter.filter()          ← max 3/domain, max 25/email, block socials
│
├── CompletableFuture.allOf(...)         ← 5 parallel AI requests
│   └── for each URL:
│       └── ArticleExtractor.extract()  ← fetch → AI → ExtractedArticle
│
├── for each ExtractedArticle:
│   └── ArticleSaver.saveArticle()      ← dedup by link+title, normalize lang/region
│                                          getOrCreate tags, write article_tag_percent
│
└── ArticleService.cleanupOldUnread()   ← delete articles >60 days old, readCount ≤ 0
```

---

## 3. Component Dependency Graph

```
                     ┌─────────────┐
                     │ HibernateUtil│  (singleton SessionFactory)
                     └──────┬──────┘
                            │ provides Session
              ┌─────────────┼─────────────────────────────┐
              ▼             ▼             ▼                ▼
         UserDAOImpl  ArticleDAOImpl  TagDAOImpl     ReportDAOImpl
         UserTagPercentDAOImpl        ArticleTagPercentDAOImpl
              │             │             │                │
              └─────────────┴──────┬──────┴────────────────┘
                                   ▼
                    ┌──────────────────────────────┐
                    │         Service Layer        │
                    │  UserService  ArticleService │
                    │  TagService   TagPercentSvc  │
                    │  ReportService               │
                    └──────────────┬───────────────┘
                                   │
               ┌───────────────────┼──────────────────────┐
               ▼                   ▼                       ▼
    MainScreenController   SearchByKeywordCtrl    LoginController
    RegistrationController SearchByUsernameCtrl   LanguageRegionCtrl
    CategoriesController   SearchByReportsCtrl
               │
               │  uses
               ▼
    ┌──────────────────────────────────────────────────────┐
    │           AI/Email Pipeline (background)             │
    │  EmailScheduler → EmailReader → LinkExtractor        │
    │  LinkFilter → ArticleExtractor → ArticleSaver        │
    │           notifies UI via Runnable callback          │
    └──────────────────────────────────────────────────────┘
```

---

## 4. Data Flow: End-to-End Article Ingestion

```
[Gmail Inbox]
     │  IMAP / SSL
     ▼
EmailReader               reads UNREAD emails, marks READ
     │
     ▼
LinkExtractor             regex + jsoup → raw URL list (plain + HTML)
     │
     ▼
LinkFilter                drops social/unsubscribe/file/tracking links
                          limits: 3 URLs/domain, 25 URLs/email
     │  filtered URL list
     ▼
─────────────────── ThreadPool (5 workers) ───────────────────
     │
     ▼
ArticleFetcher            jsoup GET → follow redirects → extract text
     │                    max 2000 chars, 10s timeout
     ▼
(LinkFilter check)        rejects non-article final URLs (Google Play, YouTube…)
     │
     ▼
ArticleExtractor          OkHttp POST → Ollama (llama3.2)
                          prompt includes existing DB tags
                          120s timeout, JSON parse → ExtractedArticle
     │
     ▼
ArticleSaver              dedup (link + title), normalize lang/region
                          getOrCreate Tags, write ArticleTagPercent
                          calls onArticleSaved() Runnable → UI refresh
     │
     ▼
[MySQL — article + tag + article_tag_percent tables]
     │
     ▼
MainScreenController.loadArticles()   re-fetches and re-scores feed
```

---

## 5. Data Flow: User Interaction (Rating)

```
User clicks article card
     │
     ▼
onCardClick(article)       opens browser, shows rating buttons
     │
User clicks [👍 / ✓ / 👎 / ⚠]
     │
     ├── [rating ≠ -2]  doRate(article, rating)
     │       ├── ReportService.upsertRating(article, user, rating)   → report table
     │       └── TagPercentService.adjustUserTagWeights(article, direction)
     │               → update user_tag_percent for each of article's tags
     │
     └── [rating = -2]  onReport(article)
             └── ReportService.upsertRating(article, user, -2)       → report table
                 (no tag weight change)
     │
     ▼
ratedArticles.put(articleId, rating)   ← session cache (prevents duplicate)
articlesList.refresh()                 ← re-renders card (highlight active button)
applyFilters()                         ← removes rated article from feed
```

---

## 6. Data Flow: Recommendation Engine

```
setCurrentUser(user)
     │
     ├── TagPercentService.getUserTagMap(user)
     │       → Map<tagId, percent>  (user interests, 0-100)
     │
     ├── ArticleService.getRecentArticlesWithTags(100)
     │       → List<Article> with eager ArticleTagPercent
     │
     ├── ReportService.getRatedArticleIds(user)
     │       → Set<Integer>  (already-rated articles to exclude)
     │
     ├── ReportService.getLikePercentageMap(articleIds)
     │       → Map<Integer, Integer>  (articleId → likePercent)
     │
     └── for each article:
             TagPercentService.calculateArticleScore(article, userTagMap)
             → score = Σ(u[t] × a[t]) / Σ|a[t]|   ← -100..+100
             stored in scoreCache: Map<Integer, Integer>

applyFilters()
     │  filter: already-rated excluded
     │  filter: language / newsroom / category / region / search text
     │  sort:   scoreCache DESC  (best match first)
     ▼
articlesList (ListView)  — personalized, filtered, sorted feed
```

---

## 7. Notification & Polling Flow

```
setCurrentUser(user)
     └── startPolling()
             │
             ▼
     ScheduledExecutorService (daemon thread)
     runs every getNotifPollIntervalSec() seconds:
     │
     ├── [notif = "often"]   interval=300s,  threshold=15%
     ├── [notif = "rarely"]  interval=900s,  threshold=50%
     └── [notif = "never"]   interval=1800s, threshold=75%
             │
             ▼
     ArticleService.getArticleIdsNewerThan(maxLoadedArticleId)
             │
             ▼
     ReportService.getLikePercentageMap(newIds)
             │
             ▼
     count = IDs where likePercent >= threshold
             │
             ├── count > 0  →  Platform.runLater():
             │                   show newArticlesBanner "🔔 N new articles available [↻ Load]"
             │
             └── count = 0  →  banner stays hidden

onRefreshNewArticles() (↻ Load click)
     └── loadArticles()  →  maxLoadedArticleId updated, banner hidden
```

---

## 8. Registration Wizard Flow

```
LoginController.onRegister()
     └── open Registration.fxml (430×932)

RegistrationController.onEnter()
     │  validate: nick ≥3 chars, not taken; password ≥8 + special char; match
     └── UserService.registerUserNoEmail(nick, password)
             └── navigate to LanguageRegion.fxml (pass User)

LanguageRegionController.onEnter()
     │  validate: language + region selected
     └── UserService.setLanguages(nick, languages) + setRegions(nick, regionKeys)
             └── navigate to Categories.fxml (pass User)

CategoriesController.onEnter()
     │  for each selected category:
     │     TagService.getOrCreateTag(tagName)
     │     TagPercentService.setUserTagPercent(user, tag, 30)
     └── navigate to MainScreen.fxml (pass User)
```

---

## 9. Admin Panel Architecture

All three admin screens share the same layout pattern:

```
┌─────────────────────────────────────────────────────┐
│  [Nav: SearchByKeyword | SearchByUsername | Reports]│
│  [Settings ⚙]  [Logout →|]                         │
├─────────────────────────────────────────────────────┤
│ Left Panel (search/list)    │ Right Panel (details) │
│                             │                        │
│  SearchByKeyword:           │  Article fields:       │
│  - keyword search           │  title (editable)      │
│  - date picker              │  link, lang, region    │
│  - articlesListView         │  publisher, date       │
│  - blocksListView (-2)      │  tags PieChart         │
│  - gradeListView (≠-2)      │  grade PieChart        │
│  - tagsListView             │                        │
│                             │                        │
│  SearchByUsername:          │  User fields:          │
│  - username search          │  nick (PK, read-only)  │
│  - usersListView            │  passwords, email      │
│  - blocksListView           │  region, language      │
│  - gradeListView            │  role, read count      │
│  - tagsListView             │  registration date     │
│                             │                        │
│  SearchByReports:           │  Report fields:        │
│  - reportsListView          │  date, user, article   │
│  (rating=-2 only)           │  rating, reason text   │
└─────────────────────────────────────────────────────┘
```

**Grade vs Blocks separation rule:**
- `gradeListView` / `gradePieChart`: shows reports where `rating != -2`
- `blocksListView`: shows reports where `rating == -2`
- `SearchByReports`: only `rating == -2` entries (moderation queue)

---

## 10. Technology Stack Summary

| Component | Technology | Version |
|---|---|---|
| Language | Java | 24 |
| UI Framework | JavaFX | 25.0.2 |
| ORM | Hibernate | 7.2.3.Final |
| Database | MySQL | 9.0.0 (driver) |
| AI Engine | Ollama / Llama3.2 | local (localhost:11434) |
| Email | Jakarta Mail | IMAP/SSL |
| HTTP Client | OkHttp | (latest in pom) |
| HTML Parser | Jsoup | (latest in pom) |
| JSON | Jackson Databind | (latest in pom) |
| Build | Maven | — |
| Container | Docker / Ubuntu 24.04 | — |

---

## 11. Package Structure Map

```
org.example/
│
├── (root) — JPA Entities
│   ├── Article.java          @Entity  table=article
│   ├── Tag.java              @Entity  table=tag
│   ├── User.java             @Entity  table=user
│   ├── Report.java           @Entity  table=report
│   ├── ArticleTagPercent.java @Entity  table=article_tag_percent
│   ├── UserTagPercent.java   @Entity  table=user_tag_percent
│   ├── ArticleTagId.java     @Embeddable composite PK
│   ├── UserTagId.java        @Embeddable composite PK
│   └── App.java              JavaFX entry point (extends Application)
│
├── util/
│   └── HibernateUtil.java    SessionFactory singleton
│
├── dao/
│   ├── GenericDAO.java       Generic CRUD interface
│   ├── ArticleDAO.java
│   ├── TagDAO.java
│   ├── UserDAO.java
│   ├── ReportDAO.java
│   ├── ArticleTagPercentDAO.java
│   └── UserTagPercentDAO.java
│
├── dao/impl/
│   ├── GenericDAOImpl.java   Abstract base (manages Session/Transaction)
│   ├── ArticleDAOImpl.java
│   ├── TagDAOImpl.java
│   ├── UserDAOImpl.java
│   ├── ReportDAOImpl.java
│   ├── ArticleTagPercentDAOImpl.java
│   └── UserTagPercentDAOImpl.java
│
├── service/
│   ├── UserService.java
│   ├── ArticleService.java
│   ├── TagService.java
│   ├── TagPercentService.java
│   └── ReportService.java
│
├── controller/
│   ├── LoginController.java
│   ├── RegistrationController.java
│   ├── LanguageRegionController.java
│   ├── CategoriesController.java
│   ├── MainScreenController.java
│   ├── SearchByKeywordController.java
│   ├── SearchByUsernameController.java
│   └── SearchByReportsController.java
│
├── ai/
│   ├── ArticleFetcher.java      jsoup → text + finalUrl
│   ├── LinkExtractor.java       regex + HTML → URL list
│   ├── LinkFilter.java          URL allowlist/blocklist
│   ├── ArticleExtractor.java    OkHttp → Ollama → ExtractedArticle
│   ├── ArticleSaver.java        ExtractedArticle → DB (dedup + normalize)
│   └── ExtractedArticle.java    DTO (Jackson @JsonProperty)
│
├── email/
│   ├── EmailConfig.java         loads email.properties
│   ├── EmailMessage.java        DTO {subject, body, htmlBody, from, date}
│   └── EmailReader.java         Jakarta Mail IMAP reader
│
└── scheduler/
    └── EmailScheduler.java      30-min pipeline orchestrator (2 thread pools)
```

---

## 12. Key Constants & Configuration

| Constant | Value | Location |
|---|---|---|
| DB host | localhost:3306/db_use | hibernate.cfg.xml |
| AI endpoint | localhost:11434/api/chat | ArticleExtractor |
| AI model | llama3.2 | ArticleExtractor |
| AI parallel workers | 5 | EmailScheduler |
| Article text limit | 2000 chars | ArticleExtractor |
| HTTP fetch timeout | 10s | ArticleFetcher |
| AI write timeout | 30s | ArticleExtractor |
| AI read timeout | 120s | ArticleExtractor |
| Min article content | 200 chars | ArticleExtractor |
| Scheduler interval | 30 min | EmailScheduler |
| Cleanup threshold | 60 days, readCount ≤ 0 | EmailScheduler |
| URLs per domain | max 3 | LinkFilter |
| URLs per email | max 25 | LinkFilter |
| Popular threshold | 50% liked | MainScreenController |
| Poll: often | 300s, 15% threshold | MainScreenController |
| Poll: rarely | 900s, 50% threshold | MainScreenController |
| Poll: never | 1800s, 75% threshold | MainScreenController |
| Feed size | 100 articles | MainScreenController |
| Login window | 520×932, not resizable | LoginController |
| Main window | 520×932, not resizable | LoginController |
| Admin CRUD window | 1280×800 | LoginController |
| Initial tag weight | 30% | CategoriesController |

---

## 13. Class Inheritance Diagram

```
java.lang.Object
├── GenericDAOImpl<T, ID>  implements GenericDAO<T, ID>
│   ├── UserDAOImpl          implements UserDAO
│   ├── ArticleDAOImpl       implements ArticleDAO
│   ├── TagDAOImpl           implements TagDAO
│   ├── ReportDAOImpl        implements ReportDAO
│   ├── UserTagPercentDAOImpl    implements UserTagPercentDAO
│   └── ArticleTagPercentDAOImpl implements ArticleTagPercentDAO
│
└── javafx.application.Application
    └── App  (main entry point, launches JavaFX)
```

---

## 14. Error Handling Patterns

| Issue | Location | Fix Applied |
|---|---|---|
| `LazyInitializationException` on tags | ArticleTagPercentDAOImpl | `JOIN FETCH atp.tag` |
| `LazyInitializationException` on user | ReportDAOImpl | `JOIN FETCH r.user` |
| `LazyInitializationException` on user tags | UserTagPercentDAOImpl | `JOIN FETCH utp.tag` |
| `java.sql.Date.toInstant()` throws | SearchByKeyword/UsernameController | `instanceof java.sql.Date sqlDate → sqlDate.toLocalDate()` |
| Duplicate articles from tracking URLs | ArticleFetcher + ArticleSaver | `finalUrl` after redirect + dedup by title |
| Cartesian product in article+tags query | ArticleDAOImpl | 2-step: IDs first, then `findByIds()` |
| Rating buttons fire twice | MainScreenController | `setOnAction` instead of `setOnMouseClicked` |
