# NewsDove

Дипломная работа. Агрегатор новостей из email-рассылок с персонализированной лентой на базе локального ИИ (Ollama).

Сервер (Ktor) работает на ноутбуке; Android-приложение (Jetpack Compose) подключается по локальной сети через HTTPS.

---

## Модули

| Модуль | Язык | Роль |
|--------|------|------|
| `:core` | Java 24 | Пайплайн (email → парсинг → ИИ-классификация), сущности Hibernate, сервисы, CLI |
| `:server` | Kotlin | Ktor-сервер: REST API, JWT-аутентификация, маршруты |
| `:shared` | Kotlin | Общие DTO (kotlinx.serialization) для сервера и Android |
| `:androidApp` | Kotlin | Jetpack Compose: лента, онбординг, настройки, жалобы, админ-панель |

---

## Стек и версии

| Компонент | Версия |
|-----------|--------|
| Kotlin | 2.4.21 |
| Ktor | 3.6.1 |
| Hibernate ORM | 7.2.3.Final |
| MySQL Connector/J | 9.0.0 |
| Flyway | 13.11.1 |
| kotlinx.serialization | 1.12.0 |
| OkHttp | 5.5.1 |
| Jsoup | 1.23.2 |
| Argon2-jvm | 2.12 |
| SLF4J / Logback | 2.0.20 / 1.6.5 |
| Jackson BOM | 2.22.0 |
| Lingua (определение языка) | 1.2.2 |
| JUnit Jupiter | 6.1.3 |
| MockK | 1.14.9 |
| AGP | 9.5.1 |
| Compose BOM | 2026.09.00 |
| Activity Compose | 1.13.0 |
| Navigation Compose | 2.10.2 |
| Lifecycle | 2.11.0 |
| DataStore | 1.3.0 |

---

## Требования

- **JDK 25** (Eclipse Temurin `jdk-25.0.2.10-hotspot`)
- **MySQL 8.0** — схема `db_use`, тестовая копия `db_use_test`
- **Ollama** + модель `qwen3:8b` (`ollama pull qwen3:8b`)
- **Android SDK** compileSdk/targetSdk 37, minSdk 26 (Android 8.0+)

---

## Быстрый старт

### 1. Конфигурация

Скопировать шаблоны и заполнить реальные значения:

```
core/src/main/resources/hibernate.cfg.xml.example  →  hibernate.cfg.xml
core/src/main/resources/ai.properties.example      →  ai.properties
core/src/main/resources/email.properties.example   →  email.properties
server/config/server.properties.example            →  server/config/server.properties
```

Секреты (пароль БД, JWT-ключ, пароль keystore) — только через переменные окружения или локальные файлы; в git не коммитятся.

### 2. База данных — миграции

```bash
# Запустить MySQL:
net start MySQL80

# Применить все миграции (Flyway):
./gradlew :core:run --args="--migrate"
```

> **Внимание:** команда работает только с базой, содержащей `db_use_test` в URL.  
> Для `db_use` требуется явное подтверждение.

### 3. Запуск сервера

```bash
ollama pull qwen3:8b   # однократно
ollama serve           # запустить Ollama (или запускается автоматически)

./gradlew :server:run
```

По умолчанию сервер слушает HTTP на `:8080`. HTTPS на `:8443` включается после настройки TLS — см. [docs/tls-setup.md](docs/tls-setup.md).

### 4. TLS (для подключения Android-приложения)

Инструкции по генерации CA, сертификата сервера, keystore и настройке Android: **[docs/tls-setup.md](docs/tls-setup.md)**.

### 5. Android — эмулятор

```bash
# Пробросить порт с эмулятора на хост (выполнить один раз за сессию):
adb reverse tcp:8443 tcp:8443

# Собрать и установить debug-сборку:
./gradlew :androidApp:installDebug
```

Для физического устройства по USB — `adb reverse tcp:8443 tcp:8443`; для Wi-Fi — указать LAN IP сервера в `local.properties`:

```
newsdove.base.url=https://<IP>:8443
```

---

## Документация

| Файл | Содержание |
|------|-----------|
| [docs/architecture.md](docs/architecture.md) | Схема модулей, поток данных (пайплайн), ER-диаграмма, список миграций |
| [docs/api.md](docs/api.md) | Все REST-эндпоинты, поток аутентификации |
| [docs/security.md](docs/security.md) | JWT, refresh-токены, защита входа, роли, аудит, TLS, Android Keystore |
| [docs/android.md](docs/android.md) | Экраны пользователя и навигация, лента, настройки, жалобы, админ-панель |
| [docs/feed-score.md](docs/feed-score.md) | Формула скора совпадения (matchPercent) |
| [docs/tls-setup.md](docs/tls-setup.md) | Генерация CA, сертификата, keystore; настройка Android и браузера |
| [docs/old-vs-new.md](docs/old-vs-new.md) | Сравнение с курсовой работой (JavaFX → Ktor + Android) |
| [docs/server-run.md](docs/server-run.md) | Детали запуска сервера |
