# REST API — NewsDove

Базовый путь: `/api/v1`

Все защищённые эндпоинты требуют заголовок:
```
Authorization: Bearer <access-token>
```

Роли доступа: `USER` (обычный пользователь), `MODERATOR`, `ADMIN`.

---

## Аутентификация

### Поток JWT

```
Регистрация / Вход  →  access JWT (15 мин) + refresh token (30 дней)
                                    │
                      Истёк access  │
                                    ↓
                   POST /auth/refresh  →  новая пара токенов
                   (ротация семейства — старый refresh отзывается)
                                    │
                   Повторное использование отозванного токена
                                    ↓
                   403 TOKEN_REUSED  →  всё семейство отзывается
```

**Алгоритм:** HMAC256, issuer `newsdove`.  
**Хранение:** в базе хранится SHA-256 хэш refresh-токена; raw-значение клиент не сохраняет на сервере.  
**Защита входа:** экспоненциальная задержка при неверных паролях (1 с, 2 с, 4 с … до 60 с), окно сброса 15 мин.

---

## Таблица эндпоинтов

### Аутентификация (`/auth`)

| Метод | Путь | Роль | Параметры / тело | Успешный ответ | Ошибки |
|-------|------|------|-----------------|---------------|--------|
| POST | `/auth/register` | — | `{nick, password}` | 201 `TokenPairDto` (accessExpiresIn=900 с) | 409 NICK\_TAKEN |
| POST | `/auth/login` | — | `{login, password}` | 200 `TokenPairDto` | 401 INVALID\_CREDENTIALS, 429 TOO\_MANY\_ATTEMPTS |
| POST | `/auth/refresh` | — | `{refreshToken}` | 200 `TokenPairDto` | 401 (не найден/истёк), 403 TOKEN\_REUSED |
| POST | `/auth/logout` | jwt-access | `{refreshToken}` | 204 | 401 |

### Лента (`/feed`, `/articles`)

| Метод | Путь | Роль | Параметры | Успешный ответ | Ошибки |
|-------|------|------|-----------|---------------|--------|
| GET | `/feed` | jwt-access | `limit`, `cursor`, `region`, `category`; заголовок `Accept-Language` | 200 `[FeedItemDto]` с `matchPercent` | 401 |
| GET | `/articles/{id}` | jwt-access | — | 200 `ArticleDto` | 401, 404 |

### Оценки и жалобы (`/articles/{id}/...`)

| Метод | Путь | Роль | Тело | Успешный ответ | Ошибки |
|-------|------|------|------|---------------|--------|
| POST | `/articles/{id}/rate` | jwt-access | `{rating: 1 или -1}` | 204 | 401, 404, 422 |
| DELETE | `/articles/{id}/rate` | jwt-access | — | 204 | 401, 409 (есть жалоба) |
| POST | `/articles/{id}/complain` | jwt-access | `{reasonCode}` | 204 | 401, 404, 409 |

### Профиль пользователя (`/me`)

| Метод | Путь | Роль | Тело | Успешный ответ |
|-------|------|------|------|---------------|
| GET | `/me` | jwt-access | — | 200 `UserProfileDto` |
| PUT | `/me/settings` | jwt-access | `{languages, regions, notificationFrequency}` | 200 `UserProfileDto` |
| PUT | `/me/password` | jwt-access | `{currentPassword, newPassword}` | 204 |
| GET | `/me/interests` | jwt-access | — | 200 `[InterestDto]` |
| PUT | `/me/interests` | jwt-access | `{categoryIds: [int]}` | 204 |

### Метаданные (`/categories`, `/regions`, `/languages`)

| Метод | Путь | Роль | Параметры | Успешный ответ |
|-------|------|------|-----------|---------------|
| GET | `/categories` | jwt-access | заголовок `Accept-Language` | 200 `[CategoryDto]` с вложенными subtopics |
| GET | `/regions` | jwt-access | — | 200 `[RegionDto]` |
| GET | `/languages` | jwt-access | — | 200 `[LanguageDto]` |
| GET | `/language-names` | jwt-access | `lang=ru` или `en` | 200 `Map<code, name>` |

### Служебный

| Метод | Путь | Роль | Ответ |
|-------|------|------|-------|
| GET | `/health` | — | 200 `{"status":"ok"}` (доступен и по HTTP) |

### Администрирование (`/admin`)

Все `/admin`-эндпоинты доступны только ролям `MODERATOR` и `ADMIN`, если не указано иное.

#### Жалобы

| Метод | Путь | Параметры | Ответ |
|-------|------|-----------|-------|
| GET | `/admin/complaints` | `limit`, `cursor`, `q`, `fromDate`, `toDate`, `reason`, `reporter`, `language` | 200 страница `AdminComplaintDto` |
| DELETE | `/admin/articles/{id}/complaints` | — | 204 (сбросить все жалобы на статью) |
| DELETE | `/admin/complaints/{reportId}` | — | 204 (удалить одну жалобу) |

#### Статьи

| Метод | Путь | Параметры | Ответ |
|-------|------|-----------|-------|
| GET | `/admin/articles` | `limit`, `cursor`, `q`, `fromDate`, `toDate`, `hidden`, `publisher`, `language`, `region`, `category`, `hasComplaints`, `classified` | 200 страница `AdminArticleDto` |
| GET | `/admin/articles/{id}` | — | 200 `AdminArticleDetailDto` |
| PUT | `/admin/articles/{id}/hidden` | тело: `{hidden: bool}` | 204 |

#### Пользователи (только `ADMIN`)

| Метод | Путь | Параметры | Ответ |
|-------|------|-----------|-------|
| GET | `/admin/users` | `limit`, `cursor`, `q`, `fromDate`, `toDate`, `role`, `language`, `region`, `hasComplaints` | 200 страница `AdminUserDto` |
| GET | `/admin/users/{id}` | — | 200 `AdminUserDetailDto` |
| PUT | `/admin/users/{id}/role` | тело: `{role}` | 204 |

#### Аудит и статистика (только `ADMIN`)

| Метод | Путь | Параметры | Ответ |
|-------|------|-----------|-------|
| GET | `/admin/audit` | `limit`, `cursor`, `actions` | 200 страница `AuditLogDto` |
| GET | `/admin/stats` | — | 200 `StatsDto` |

---

## Общие коды ошибок

| HTTP | Код | Когда |
|------|-----|-------|
| 400 | BAD\_REQUEST | Неверный запрос |
| 401 | MISSING\_TOKEN | Нет или невалидный токен |
| 401 | INVALID\_CREDENTIALS | Неверный пароль |
| 403 | TOKEN\_REUSED | Повторное использование отозванного refresh-токена |
| 403 | HTTPS\_REQUIRED | Запрос пришёл по HTTP (не `/health`) |
| 404 | NOT\_FOUND | Ресурс не найден |
| 409 | CONFLICT | Нарушение уникальности (ник занят, жалоба уже есть) |
| 422 | INVALID\_REQUEST | Некорректное тело запроса |
| 429 | TOO\_MANY\_ATTEMPTS | Слишком много неверных попыток входа |
