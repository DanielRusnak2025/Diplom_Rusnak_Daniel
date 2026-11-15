# Plan: Stage 5g — Admin Panel

## Overview

ADMIN → admin panel after login (tabs: Статьи, Пользователи, Жалобы, Журнал, Статистика).  
MODERATOR → panel with at least Жалобы; Статьи with hide/unhide is an open question (see §8).  
USER → feed (unchanged).  
Staff bypass onboarding.

---

## 1. Existing Endpoints Reused

| Method | Endpoint | Used in tab | Notes |
|--------|----------|-------------|-------|
| GET | `/api/v1/admin/complaints` | Жалобы | cursor paging, reasonCode IS NOT NULL |
| PUT | `/api/v1/admin/articles/{id}/hidden` | Статьи detail + Жалобы | MODERATOR+ |
| DELETE | `/api/v1/admin/articles/{id}/complaints` | Жалобы | dismiss (UPDATE reasonCode=NULL) |
| GET | `/api/v1/admin/users` | Пользователи list | cursor paging; returns `AdminUserPageDto` |
| PUT | `/api/v1/admin/users/{id}/role` | Пользователи detail | ADMIN; server rejects self-change with 409 |
| GET | `/api/v1/admin/audit` | Журнал | cursor paging; returns `AuditPageDto` |
| GET | `/api/v1/admin/stats` | Статистика | returns `StatsDto` |

---

## 2. New Endpoints Needed

### 2a. Article search — `GET /api/v1/admin/articles`

| Field | Detail |
|-------|--------|
| Auth | JWT ADMIN (open question: MODERATOR+ — see §8.1) |
| Query params | `q` (title keyword, optional), `fromDate` (yyyy-MM-dd, optional), `toDate` (yyyy-MM-dd, optional), `hidden` (true/false/omit=all), `cursor` (int, last id), `limit` (default 20, max 100) |
| Paging | cursor-based, `id DESC` |
| Response | `AdminArticlePageDto(items: List<AdminArticleDto>, nextCursor: String?)` |
| `AdminArticleDto` | `id, title, link, publisher, language, region, publicationDate, hidden, classified(Boolean)` |
| Filter logic | `q` → `LIKE %q%` on `title`; date range on `COALESCE(publication_date, DATE(created_at))`; `hidden` filter optional; classified = `promptVersion IS NOT NULL` |
| New DTOs in `:shared` | `AdminArticleDto`, `AdminArticlePageDto` |

### 2b. Article detail — `GET /api/v1/admin/articles/{id}`

| Field | Detail |
|-------|--------|
| Auth | JWT ADMIN (open question — see §8.1) |
| Response | `AdminArticleDetailDto` |
| `AdminArticleDetailDto` | all `AdminArticleDto` fields + `tags: List<TagRefDto>` (reuse existing DTO, add `kind` field) + `likeCount, dislikeCount, complaintCount: Int` + `complainants: List<AdminUserRefDto>(userId, nick)` |
| Tags | `article_tag_percent` JOIN `tag`; both `name` (en) and `nameRu`; sorted by percent DESC |
| Ratings | COUNT from `report` WHERE `article_id=:id AND rating=1/-1` and `reasonCode IS/IS NOT NULL` |
| New DTOs | `AdminArticleDetailDto`, `AdminUserRefDto`, `TagRefDto` gets optional `kind: String` (or new `AdminTagDto`) |

### 2c. User detail — `GET /api/v1/admin/users/{id}`

| Field | Detail |
|-------|--------|
| Auth | JWT ADMIN |
| Response | `AdminUserDetailDto` |
| `AdminUserDetailDto` | id, nick, email, registrationDate, role + `languages: List<String>`, `regions: List<String>` + `interests: List<AdminInterestDto>` + `blocks: List<AdminBlockDto>` + `ratings: AdminUserRatingsDto` |
| `AdminInterestDto` | `tagKey, nameEn, nameRu, percent` — all `user_tag_percent` entries for this user JOIN `tag` WHERE `kind='CATEGORY'` (non-zero or all — see §8.4) |
| `AdminBlockDto` | `blockType (TAG/REGION/PUBLISHER), tagKey?, region?, publisher?` |
| `AdminUserRatingsDto` | `likeCount, dislikeCount, complaintCount` — counts from `report` WHERE `user_id=:id` |
| New DTOs | `AdminUserDetailDto`, `AdminInterestDto`, `AdminBlockDto`, `AdminUserRatingsDto` |

---

## 3. Shared DTOs to Add

All in `shared/src/main/kotlin/org/example/dto/`:

```
AdminArticleDto.kt
AdminArticlePageDto.kt
AdminArticleDetailDto.kt
AdminUserRefDto.kt          (userId: Int, nick: String)
AdminUserDetailDto.kt
AdminInterestDto.kt
AdminBlockDto.kt
AdminUserRatingsDto.kt
```

`TagRefDto` already exists; add an optional `kind: String? = null` field or create `AdminTagDto` if that field would break existing uses. Preference: add `kind` with a default to avoid a new class.

---

## 4. App Navigation Changes

### 4a. AppViewModel

Add `NavTarget.AdminPanel` to the enum.

`checkOnboarding()` changes:
```
val me = meApi.me()
_profile.value = me
_navTarget.value = when {
    me.role == RoleDto.ADMIN || me.role == RoleDto.MODERATOR -> NavTarget.AdminPanel
    me.languages.isNotEmpty() -> NavTarget.Home
    else -> NavTarget.Onboarding
}
```

Staff are never sent to onboarding. If a staff member taps "Лента" in the panel, navigate directly to `"home"` — do not re-run `checkOnboarding()` (see §8.5 for the edge case of staff with empty languages).

### 4b. AppNavGraph

Add `composable("admin")` destination that hosts `AdminPanelScreen`. The `LaunchedEffect(navTarget)` block routes `NavTarget.AdminPanel` → `navController.navigate("admin") { popUpTo(0) { inclusive = true } }`.

---

## 5. Screens, ViewModels, and Tests

### AdminPanelScreen (`ui/admin/`)

| Component | Description |
|-----------|-------------|
| `AdminPanelScreen` | `Scaffold` + `TabRow` (tab count depends on role); each tab is a full-height composable |
| `AdminPanelViewModel` | holds `profile` to determine visible tabs; exposes nav events (toLenta, logout) |

### Статьи tab

| Component | Description |
|-----------|-------------|
| `ArticlesTab` | `SearchBar` + optional date pickers + `LazyColumn`; loads on first entry and on search submit |
| `ArticlesViewModel` | `searchQuery`, `fromDate`, `toDate`, `hidden` filter; cursor paging via `AdminApi.searchArticles()`; exposes `ArticlesUiState` |
| `AdminArticleDetailScreen` | full detail: metadata, tags (horizontal bars), rating counts, complainants list; actions: hide/unhide |
| `ArticleDetailViewModel` | calls `AdminApi.getArticleDetail(id)` + `AdminApi.hideArticle(id, hidden)` |
| Tests | `ArticlesViewModelTest`: load, search term changes list, hide success updates `hidden` flag; `ArticleDetailViewModelTest`: detail loaded, hide/unhide toggles state |

### Пользователи tab

| Component | Description |
|-----------|-------------|
| `UsersTab` | search by nick + registration date pickers + `LazyColumn` (reuses existing `GET /admin/users`) |
| `UsersViewModel` | cursor paging; wraps existing `AdminApi.getUsers()`; search/date filter params |
| `AdminUserDetailScreen` | nick, email, reg date, role dropdown, languages/regions chips, interest bars, block list, ratings summary |
| `UserDetailViewModel` | calls `AdminApi.getUserDetail(id)` + `AdminApi.setUserRole(id, role)` |
| Tests | `UsersViewModelTest`: load, role change success/409 handled; `UserDetailViewModelTest`: detail loaded |

### Жалобы tab

| Component | Description |
|-----------|-------------|
| `ComplaintsTab` | port of existing `ModeratorScreen`; adds search (articleTitle keyword) + date filter params to `GET /admin/complaints` |
| `ComplaintsViewModel` | extend `ModeratorViewModel` or extract shared state; same hide + dismiss actions; adds `searchQuery`, `fromDate`, `toDate` params |
| Tests | extend existing `ModeratorViewModelTest` for search param |

Note: `GET /admin/complaints` already supports `cursor`/`limit`. Adding `q` and `fromDate`/`toDate` params is a small server change (add optional filters to the existing HQL query — no new endpoint, just extend the existing one).

### Журнал tab

| Component | Description |
|-----------|-------------|
| `AuditTab` | `LazyColumn` of audit entries; cursor paging via existing `GET /admin/audit` |
| `AuditViewModel` | cursor paging; `AuditUiState(items, isLoading, isLoadingMore, isEndOfList, error)` |
| Tests | `AuditViewModelTest`: initial load, pagination |

### Статистика tab

| Component | Description |
|-----------|-------------|
| `StatsTab` | static display of `StatsDto` fields with simple label+value rows or horizontal bars |
| `StatsViewModel` | single call `AdminApi.getStats()`; `StatsUiState(stats?, isLoading, error)` |
| Tests | `StatsViewModelTest`: success path, error path |

### AdminApi (`network/AdminApi.kt`)

Extend existing class with:
```kotlin
suspend fun searchArticles(q: String?, fromDate: String?, toDate: String?, hidden: Boolean?, cursor: Int?, limit: Int): AdminArticlePageDto
suspend fun getArticleDetail(id: Int): AdminArticleDetailDto
suspend fun getUserDetail(id: Int): AdminUserDetailDto
suspend fun getAuditLog(cursor: Long?, limit: Int): AuditPageDto
suspend fun getStats(): StatsDto
```

---

## 6. Window Insets Fix

`OnboardingLanguagesScreen.kt` (and `OnboardingRegionsScreen`, `OnboardingInterestsScreen`): the root `Column` or `Surface` needs `Modifier.systemBarsPadding()` (or `WindowInsets.systemBars.asPaddingValues()`) so the title does not draw behind the status bar.

Apply in the same sub-step as the first screen change to avoid a separate commit.

---

## 7. Sub-steps

| Step | Scope | Deliverable |
|------|-------|-------------|
| **5g-1** | Server: new endpoints §2a–2c + server-side complaint search filter + new DTOs in `:shared` | `:server:test` green; new endpoints smoke-tested with curl |
| **5g-2** | AppViewModel routing (staff → AdminPanel) + NavGraph AdminPanel destination (blank screen) + AppViewModelTest updated | build green; ADMIN/MODERATOR account lands on blank panel after login |
| **5g-3** | AdminPanelScreen shell (tabs, role-gated), Статистика tab, Журнал tab, AdminPanelViewModel, AuditViewModel, StatsViewModel | ADMIN sees stats and audit log; Журнал pagination works |
| **5g-4** | Статьи tab + AdminArticleDetailScreen + ArticlesViewModel + ArticleDetailViewModel + hide action + tests | search, detail, hide/unhide work |
| **5g-5** | Пользователи tab + AdminUserDetailScreen + UsersViewModel + UserDetailViewModel + role change + tests | user detail with interest bars + role dropdown |
| **5g-6** | Жалобы tab (port of ModeratorScreen + search + date filter); extend complaint search on server | complaints tab in panel replaces standalone ModeratorScreen |
| **5g-7** | Window insets fix (onboarding screens); final polish (panel top-bar "Лента"/"Выйти" buttons, navigation edge cases) | insets fixed; "Лента" navigates to feed |

Each sub-step: `./gradlew clean :core:test :server:test :androidApp:testDebugUnitTest` must pass before commit.

---

## 8. Open Questions

### Answered (Q1–Q4)

1. **Статьи tab visibility for MODERATOR**: **CONFIRMED: yes.** MODERATOR sees Статьи tab with hide/unhide. Пользователи, Журнал, Статистика = ADMIN only.

2. **Delete user / Edit article**: Not needed for now. Read-only panel + hide/unhide + role change only.

3. **"Лента" for staff with empty languages**: **CONFIRMED: option (b)** — staff with no languages go to onboarding first, then feed. Back from feed returns to the panel.

4. **Unify ComplaintsViewModel**: **CONFIRMED.** Rename `ModeratorViewModel` → `ComplaintsViewModel`. Remove "Жалобы" button from feed top bar; staff get "Панель" button there instead.

   Also confirmed in this round:
   - **Interest weights**: show only non-zero (percent != 0) CATEGORY tags.
   - **Complainants list**: nick + reasonCode (confirmed by inclusion of `AdminUserRefDto(userId, nick, reasonCode)`).
   - **Article detail tags**: add `nameRu: String? = null` to `TagRefDto` (backward compatible, done in 5g-1).

### Open (Q5–Q8)

5. **"Лента" button for staff with no languages set** (from Q3 above, confirmed: option b — go to onboarding; back returns to panel). Implemented in 5g-7.

6. **Complainants list format**: show nick + reasonCode (confirmed — see `AdminUserRefDto`). Already reflected in `AdminArticleDetailDto`.

7. **ModeratorViewModel → ComplaintsViewModel rename**: confirmed in Q4. The rename happens in 5g-6; no code exists yet that needs changing.

8. **Article detail tags `nameRu`**: `TagRefDto.nameRu: String? = null` added in 5g-1. UI should display `nameRu ?: name`. Implement in 5g-4.
