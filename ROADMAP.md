# NewsDove — дорожная карта дипломного проекта

Тема «Разработка клиент-серверного приложения с мобильным интерфейсом на Kotlin,
с локальным ИИ-анализом данных».
Основа курсовой проект NewsDove (Java, Hibernate 7, JavaFX, MySQL, Ollama).

## 0. Правила работы (для Claude Code)

1. База данных содержит реальные статьи. НИКАКИХ удаляющих операций над данными.
   Все миграции — добавляющие, с переносом данных. Перед любой миграцией — бэкап.
2. Каждая миграция сначала прогоняется на копии базы (db_use_test), проверяется
   (количество строк допосле, отсутствие «осиротевших» FK), и только потом на основной.
3. Существующая Java-логика (почта, пайплайн, DAO, сервисы, скоринг) сохраняется
   и переезжает в модуль core. Новый код пишется на Kotlin.
4. Секреты (пароль БД, пароль приложения Gmail, JWT-ключ, ключи TLS) — только через
   переменные окружения или локальный файл вне git. В репозиторий не коммитятся.
5. Работа маленькими шагами. Каждый этап заканчивается проверкой из раздела
   «Критерии готовности». Перед разрушительными или необратимыми действиями — спросить.
6. Приложение — только агрегатор показывает заголовок, теги, издание, язык, скор.
   Текст статьи не хранится и не отображается; статья открывается во внешнем браузере.
7. Весь ИИ — локальный. Никаких облачных API.

## 1. Зафиксированные решения

- Архитектура Ktor-сервер (ноутбук) ↔ Android-приложение (Jetpack Compose).
  Демонстрация в одной Wi-Fi-сети  раздаче. Удалённый доступ не требуется.
- Сборка Gradle (Kotlin DSL), монорепо core, server, shared, androidApp.
- ORM Hibernate 7 остаётся. СУБД MySQL остаётся. Миграции Flyway.
- Роли USER, ADMIN. Админка — экраны в том же Android-приложении по роли.
- ИИ (Ollama + JSON Schema, enum) отвечает только: статья ли это; основная
  категория + подтема; до 2 дополнительных категорий; для каждой — уровень
  релевантности high/medium/low.
- Таксономия: ~20 категорий × подтемы (~100), гибко масштабируется по скорости
  и точности модели. Черновик списка готовит ассистент, утверждает автор.
- Процент категории = вес уровня от ИИ (high=3, medium=2, low=1) + бонус за
  совпадения ключевых слов подтемы в заголовке и начале текста, затем
  нормализация к 100. Формула документируется.
- Один вызов ИИ на статью, 1 повтор; после — FAILED в очереди.
- Модель выбирается мини-сравнением: 2 модели ~4B + llama3.2 (базовая линия),
  30 размеченных статей, точность категорий и время. РЕЗУЛЬТАТЫ СМОТРИМ ВМЕСТЕ
  С АВТОРОМ перед выбором.
- Julia-1 / Laya (decision-модели через llama.cpp /v1/systemone) — не внедряются,
  возможный эксперимент за интерфейсом ArticleClassifier.
- Алгоритмы вместо ИИ: язык — Lingua; сущности — курируемый словарь
  (200–500 записей, многоязычные варианты написания, новые кандидаты одобряет
  админ); регион — вычисляется из найденных сущностей; заголовок/издание/дата —
  из разметки с заглушками (title: og:title → <title> → тема письма, иначе не
  сохранять; издание: og:site_name → читаемый домен; дата: published_time →
  дата письма); дубли — хеш ссылки и нормализованный заголовок.
- Регионы — фиксированный список 8–12 значений; регион статьи вычисляется из
  найденных сущностей и всегда приводится к значению из этого списка.
- Веса пользователя: категории, подтемы, сущности, издания, регионы.
  Скор полностью объясним по тегам, без векторных моделей.
- Интерфейс только на английском. Заголовки в оригинале, без перевода.
  Языки чтения выбираются при регистрации и меняются в настройках;
  лента фильтруется по ним.
- Почта: только письма за последние N дней (scheduler.max.age.days, поиск по
  дате на стороне IMAP); старый архив не обрабатывается.
- Отказ от: свободных тегов-сущностей от ИИ, перевода заголовков (title_en),
  эмбеддингов и семантической дедупликации, дообучения моделей.
- Гибкость ИИ-слоя:
  - модель, endpoint, температура, таймауты — в ai.properties, не в коде;
  - вся система знает только ArticleClassifier; новая технология = новая
    реализация (Ollama, decision-модель и т.п.);
  - классификатор возвращает релевантность числом 0..1; перевод уровней
    high/medium/low в число — таблица в конфиге; формула процентов работает
    с любым источником;
  - JSON-схема (enum категорий/подтем) строится из таблицы tag при запуске;
  - промпт — отдельный файл ресурсов с версией;
  - в article хранятся classifier_model, prompt_version, taxonomy_version;
  - команда повторной классификации: вернуть выбранные статьи в article_queue.
- report остаётся единой таблицей (оценки и жалобы), добавляется reason_code.
- Формула персонализации основа сохраняется, добавляется сглаживание у границ ±100.
- Уведомления WorkManager на телефоне опрашивает сервер + локальное уведомление.
  Облачные push (Firebase) не используются.
- Список разрешённых отправителей писем не ведётся.
- Нейтральная оценка не меняет весов (текущее случайное ±1–3 — убрать на этапе 1).
- Жалоба меняет веса так же, как дизлайк, плюс создаёт запись в user_block,
  если пользователь выбрал такой вариант.
- Удаление старых статей выключено по умолчанию (scheduler.cleanup.enabled=false).
- Старое Java/JavaFX-приложение никогда не запускается на db_use (только db_use_test).
- Разработка ведётся на db_use_test под пользователем newsdove_dev
  (ALL на db_use_test, SELECT на db_use); доступ через mysql --login-path.
- JDK 25 (Eclipse Temurin).
- Сборка: Gradle 9.8.0, Kotlin 2.4.21, JDK 25 toolchain (Eclipse Temurin);
  байт-код: core — 21, server — 21, shared — 17 (под будущий Android);
  shared — Kotlin/JVM (не KMP); logback 1.6.5, slf4j 2.0.20,
  kotlinx-serialization-json 1.12.0.

## 2. Этапы

### Этап 0. Подготовка
- [x] Полный бэкап текущей БД (mysqldump) и копия базы db_use_test.
- [x] Ветка legacy-javafx с текущим состоянием проекта.
- [x] Миграция Maven → Gradle (Kotlin DSL), модули core server shared
      (androidApp — перенесено в этап 5).
- [x] JavaFX исключается из сборки.
- [x] Вынести конфигурацию и секреты в переменные окружения / локальный файл.
- [x] Флаги scheduler.enabled и scheduler.cleanup.enabled (очистка выключена по умолчанию).
- [x] Логирование SLF4J + logback, раздельные файлы app.log и error.log, ротация
      (пайплайн — в этапе 3).
- [x] HeadlessMain: пайплайн запускается без UI (./gradlew :core:run).
Критерии готовности core собирается; пайплайн почты запускается без UI
(headless main) и сохраняет статьи как раньше. ✓

### Этап 1. Очистка ядра
- [ ] Убрать из сервисов зависимости от JavaFX и глобальное состояние
      «текущего пользователя» — сервисы принимают userId (int) параметром.
      V2 выполнен: User.id есть, findByNick доступен. Разблокировано.
- [ ] Слой DTO в shared (kotlinx.serialization); сущности Hibernate не покидают сервер.
      V2 выполнен. Ждём V5 (kind=CATEGORY/SUBTOPIC у тегов) для финального состава DTO.
- [x] Все HQL-запросы — только с параметрами, никакой конкатенации строк.
      (аудит показал: все DAO используют именованные параметры; единственное
      исключение searchByKeywordAndDate переписано на HQL в этапе 1)
- [ ] Юнит-тесты на скоринг (TagPercentService) и на обновление весов.
- [ ] Сглаживание формулы обновления весов.
- [ ] Нейтральная оценка: убрать случайный ±1–3, delta = 0, не трогать БД.
- [ ] Жалоба (direction=-2): менять веса как дизлайк.
Критерии тесты зелёные, скоринг даёт те же результаты на старых данных
(кроме эффекта сглаживания).

### Этап 2. Миграция БД (Flyway, сначала на копии)
- [ ] V1 baseline (baselineOnMigrate) — текущая схема.
- [x] V2 user суррогатный id; user_id в report и user_tag_percent, заполнение по nick,
      переключение FK, nick → UNIQUE.
      Flyway v2, db_use_test: user=10/article=71/report=8/utp=70; cascade delete проверен.
- [x] V3 пароли: argon2id (RFC 9106, m=65536/t=3/p=4); password_history;
      password_changed_at, failed_login_count, locked_until; constant-time auth;
      смена пароля c проверкой last-3; firstPassword → history, колонка удалена.
      db_use_test v3.3: user=10/article=71/report=8/utp=70/ph=10; 46 тестов, 0 failures.
- [x] V4 is_admin → role VARCHAR(20) CHECK (USER|MODERATOR|ADMIN); enum Role в Java;
      findByRole(Role); setRole(nick,Role). db_use_test v4: ADMIN=2 USER=8; 46 тестов, 0 failures.
- [x] V5  tag: kind = CATEGORY / SUBTOPIC / ENTITY, parent_id (для подтем),
      keywords (для подтем), region (для сущностей-стран/городов); существующие теги → ENTITY;
      начальные данные — seed/taxonomy.yaml (категории, подтемы, ключевые слова, регионы).
      db_use_test v5.4: region=12, CATEGORY=15, SUBTOPIC=83, ENTITY=257, tag_keyword=1209,
      atp=360, utp=70; subtopics без parent=0; null/dup tag_key=0; 46 тестов, 0 failures.
- [x] V6  user_language (user_id FK CASCADE, language VARCHAR(5) ISO 639-1),
      PK(user_id, language); user_region (user_id FK CASCADE, region_key FK → region),
      PK(user_id, region_key). V6_1: CREATE + перенос из preferred_language/preferred_region.
      V6_2: удалить preferred_language/preferred_region из user + код
      (User.java, UserService: updatePreferences→setLanguages/setRegions,
      UserDAO/Impl: findByPreferredRegion→findByRegion JOIN u.regions,
      ARCHITECTURE.md, seed-test-users.sql).
      db_use_test v6.2: user_language=8, user_region=8; 46 тестов, 0 failures.
- [x] V7  processed_email + article_queue.
      V7_1 SQL: processed_email (dedup-лог по message_id_hash), article_queue
      (id, url, url_hash CHAR(64) UNIQUE, email_id FK SET NULL, article_id FK SET NULL,
      status PENDING/PROCESSING/DONE/REJECTED/FAILED CHECK, reject_reason CHECK,
      attempts, last_error, lock_token CHAR(36), locked_until DATETIME(6), created_at,
      processed_at, idx_aq_status_created).
      V7_2 Java: все 71 статья → PENDING через UrlNormalizer (normalize+hash SHA-256).
      UrlNormalizer: lowercase scheme/host, убрать default-порт/#fragment/utm_*/fbclid/gclid/
      mc_cid/mc_eid, сортировать оставшиеся params, убрать trailing "/" (кроме root).
      Broken URL/host null/не http-https → IllegalArgumentException.
      ArticleQueueDAO: claimPending (FOR UPDATE SKIP LOCKED, lock_token=UUID, время из БД),
      resetStaleLocks, markDone/Rejected/Failed (WHERE lock_token guard, 0 rows → WARN).
      db_use_test v7.2: processed_email=0, article_queue=71 PENDING, url_hash дублей=0,
      article=71/atp=360/utp=70; 46+UrlNormalizerTest+ArticleQueueTest тестов, 0 failures.
      Вся цепочка V1_1…V7_2 воспроизводима с нуля: TestDb (baseline_schema.sql из db_use →
      baseline 1 → migrate) на db_use_test_it; 77 тестов.
- [x] V8  article: + link_hash CHAR(64) UNIQUE, + created_at DATETIME(6),
      + classifier_model, + prompt_version, + taxonomy_version; − read_count
      (legacy JavaFX read counter, popularity will be derived from report).
      @PrePersist/@PreUpdate вычисляют linkHash через UrlNormalizer; ArticleDAO.findByLinkHash().
      findOldArticles исключает статьи с Report (история весов пользователя).
      db_use_test v8.3: 71 строк, link_hash NULL=0, dup=0, queue-hash mismatch=0, atp=360.
      81 тест (ArticleMetaTest +4: linkHash, UNIQUE, findByLinkHash, findOldArticlesExcludesReported).
  ⚙ Бэклог: остальные find* в ArticleDAOImpl глотают исключения — исправить на Этапе 3.
- [x] V9  report + reason_code.
      V9_1 Java: проверка — ни одной непустой причины в report (0 строк); V9_2 SQL:
      ADD reason_code VARCHAR(30) NULL, CHECK rating IN(-1,0,1), CHECK reason_code IS NULL OR rating=-1,
      CHECK reason_code IN enum, DROP reason. Report.java: reasonCode + @JdbcTypeCode(VARCHAR);
      два конструктора Report(A,U,int) и Report(A,U,int,ReportReason); ReportService обновлён.
      db_use_test v9.2: report=8, reason_code все NULL ✓, все 3 CHECK присутствуют.
      3 новых теста (saveComplaint, invalidCode→CHECK, rating=1+reason→CHECK); 84 теста, 0 failures.
- [x] V10 user_block, refresh_token, audit_log (SET NULL).
      V10_1: user_block (id, user_id FK CASCADE, block_type VARCHAR(10) CHECK, tag_id FK RESTRICT,
      region_key FK RESTRICT, publisher, created_at, block_key VIRTUAL GENERATED, UNIQUE(user_id,block_key),
      CHECK exactly-one-target). V10_2: refresh_token (token_hash CHAR(64) UNIQUE, family_id,
      created_at/expires_at NOT NULL, revoked_at NULL, replaced_by_id FK SET NULL). V10_3: audit_log
      (actor_user_id FK SET NULL, action, target_type/id NULL, INDEX created_at/actor).
      UserBlock/RefreshToken/AuditLog entities + DAOs; HibernateUtil обновлён.
      db_use_test v10.3: user_block=0, refresh_token=0, audit_log=0; все FK/CHECK/UNIQUE ✓.
      5 новых тестов (double block→UNIQUE, TAG+publisher→CHECK, delete tag→RESTRICT,
      delete user→CASCADE blocks+tokens/SET NULL audit, dup token hash→UNIQUE); 89 тестов, 0 failures.
  ⚙ Бэклог: удаление тега/региона — сначала удалить user_block (FK RESTRICT, не CASCADE).
- [ ] V11 entity + entity_alias — после того как будет seed/entities.yaml.
⚠ **Ограничение до Этапа 3:** `scheduler.enabled` должен оставаться `false`.
  Старый пайплайн пишет `article_region` свободным текстом («Russia», «United States»)
  и `article_language` словом («Russian», «German»). После V1_3/V1_4 эти поля хранят
  taxonomy-ключи и ISO 639-1 коды. Запуск старого пайплайна снова нарушит схему данных.
  Проверено: в `email.properties` `scheduler.enabled=false`.

- [x] ~~Убрать каскадное удаление report при удалении article~~ — V2: CascadeType.ALL
      убран из Article.reports; оценки — история весов пользователя.
- [x] ~~Слить дубль Der Spiegel~~ — V1_1 (merge_link_duplicates): оценки и теги
      перенесены в каноническую запись, дубль удалён.
- [x] ~~user_tag_percent.nick varchar(255) → user_id~~ — сделано в V2.
- [x] ~~notification_frequency: NULL у 5 пользователей~~ — V2: 'never', NOT NULL, DEFAULT 'never'.
- [x] ~~Разовая миграция, исправляющая испорченную кодировку в сохранённых заголовках.~~
      Mojibake оказался артефактом восстановления копии (Get-Content | mysql перекодировал
      UTF-8), а не дефектом данных. db_use_test пересоздана из db_use — байты верны.
- [x] ~~Нормализация регионов и языков старых статей~~ — V1_2: значения → 12 ключей
      taxonomy.yaml (без ИИ); V1_3: языки → ISO 639-1.
Критерии количество статей, пользователей, оценок до и после совпадает;
нет осиротевших FK; вход под старыми учётками работает с хешами.

Технический долг (выявлен при V2/V3):
- calculateArticleScore требует предзагруженных articleTagPercents на Article;
  LazyInitializationException на detached-сущности. Решить через JOIN FETCH
  или передавать List<ArticleTagPercent> отдельно (этап 4, сервер).
- [x] ~~Все find* в DAO глотают Exception → Optional.empty()~~ — исправлено в V3:
      все find* теперь бросают RuntimeException.
- user AUTO_INCREMENT сдвинут тестом v2_tmp (следующий id = 12, не 11) —
  не баг, AUTO_INCREMENT не откатывается; для сведения.

Бэклог (выявлен при V3):
- docs/sql/seed-test-users.sql содержит пароли открытым текстом в git; сид
  устарел — не учитывает password_history. Заменить на скрипт, вызывающий
  UserService.registerUserNoEmail, или вынести за пределы репозитория.
- Все дампы в C:\newsdove_backups, снятые до 12:46 30.10 (включая pre-V2
  от 28.10 и 30.10 12-28, 12-31, 12-45), содержат пароли открытым текстом
  (password или firstPassword). Удалить/зашифровать после переноса
  миграций на db_use.
- db_use (основная БД) всё ещё хранит пароли открытым текстом — до переноса
  миграций V3 на неё.

Бэклог (выявлен при V5):
- Три ENTITY-тега имеют искажённые имена (militârparade, sparmaønahmen,
  vera balaхнова — смешанные кодировки/опечатки при импорте). После того как
  V5d article_queue введён и старый пайплайн отключён — найти и либо исправить
  имя (если тег используется), либо удалить (если нет article_tag_percent).
- user.preferred_region хранит произвольные строки (NULL, Asia, EU, Europe,
  Moldova, US), не совпадающие с 12 ключами таблицы region. Привести значения
  к ключам региона и добавить FK user.preferred_region → region(region_key).
  Решать вместе с V5c user_language: вероятно, объединить в отдельную таблицу
  user_region (user_id, region_key) — один пользователь, несколько регионов.

### Этап 3. ИИ-пайплайн
- [ ] Интерфейс ArticleClassifier; реализация OllamaClassifier с JSON Schema:
      is_article (boolean), основная категория + подтема, до 2 доп. категорий,
      уровень релевантности high/medium/low для каждой.
- [ ] Метаданные через Jsoup ogtitle  title, ogsite_name  домен,
      articlepublished_time.
- [ ] Очистка заголовков от HTML (Jsoup, Safelist.none) до записи в БД.
- [ ] Правильное определение кодировки страниц при загрузке (заголовок Content-Type,
      meta charset). **Низкий приоритет:** mojibake оказался артефактом восстановления
      копии БД, не дефектом пайплайна получения данных.
- [ ] Воронка отсева мусора
      1) ссылки mailto, отписка (+ заголовок List-Unsubscribe), соцсети,
         картинкипиксели, дубли;
      2) страница ogtype=article, articlepublished_time, schema.org NewsArticle,
         минимальная длина текста;
      3) сомнительные — ИИ-проверка is_article (тот же классификатор, схема boolean).
- [ ] processed_email идемпотентность по Message-ID, счётчики воронки.
- [ ] Логирование правок тегов админом (audit_log) — задел под набор данных
      для будущей оценки моделей.
- [ ] Дешёвые фильтры до ИИ: перед нормализацией URL — пройти HTTP-редиректы до
      финального URL (пример: article 138 — newsletter-tracking.dw.com переходит на
      настоящий URL DW), затем отсев по адресу, дедупликация по хешу финального URL
      до загрузки страницы, проверка разметки страницы.
      ⚠ Наблюдение (T-7b): LinkFilter лимит 3 ссылки на домен отсекает настоящие
      статьи (дайджест Spiegel: 202 → 4). Заменить на проверку по правилам и
      разметке страницы; лимит по домену убрать или сделать параметром.
- [ ] Параметры Ollama: format (JSON Schema), temperature 0, ограничение длины
      ответа, keep_alive.
- [ ] Очередь article_queue с повторами: 1 повтор при ошибке ответа ИИ;
      после — статус FAILED в очереди, статья не теряется молча.
- [ ] Повторная классификация: admin-команда возвращает выбранные статьи
      в article_queue со статусом PENDING (после смены модели / промпта / таксономии).
- [ ] Словарный поиск сущностей и ключевых слов подтем в заголовке и начале текста
      (entity_alias + keywords из tag); новые кандидаты сущностей одобряет админ.
- [ ] Расчёт процентов категорий по формуле: вес уровня (high=3, medium=2, low=1)
      + бонус совпадений ключевых слов, нормализация к 100; формула задокументирована.
- [ ] Параметр scheduler.max.age.days — ограничение возраста писем при запросе
      к IMAP (поиск по дате на стороне сервера); заменяет scheduler.max.emails (T-7).
- [ ] Перевести пайплайн (EmailScheduler, ArticleSaver, ArticleExtractor и др.)
      с System.out.println на SLF4J при переработке этапа 3; до этого кодировку
      обеспечивает -Dstdout.encoding=UTF-8 в applicationDefaultJvmArgs (T-9).
- [ ] Замеры времени каждого шага пайплайна (для диплома: таблица «было/стало»).
- [ ] Политика хранения старых статей — зафиксировать (кандидат: старше 30 дней,
      кроме статей с оценками или жалобами).
Критерии 0 ошибок парсинга ответа ИИ за прогон; у новых статей есть хотя бы
одна CATEGORY; статистика воронки видна в БД.

### Этап 4. Ktor-сервер
- [ ] Эндпоинты
      auth register, login, refresh, logout;
      me профиль, настройки, веса интересов, смена пароля;
      feed лента (пагинация, фильтры языкрегионизданиекатегория, поиск);
      articles{id} rate, report, opened;
      tags список категорий;
      notificationsnew-count; health;
      admin статьи (поиск, правка, удаление), пользователи (поиск, правка,
      роль, удаление), жалобы, журнал аудита, журнал ошибок, статистика воронки.
- [ ] JWT access (15 мин) + refresh-токены с ротацией (таблица refresh_token).
- [ ] RBAC проверка роли на сервере для каждого admin-эндпоинта; тесты на 401403.
- [ ] Валидация входных DTO (длины, форматы, диапазоны).
- [ ] StatusPages пользователю — понятное сообщение и код ошибки без технических
      деталей; полная информация — в error.log.
- [ ] Защита входа ограничение частоты запросов, блокировка после N неудачных попыток.
- [ ] Политика паролей (минимум) длина ≥ 8, не совпадает с ником,
      не совпадает с последними паролями (password_history).
- [ ] audit_log вход, выход, неудачные входы, смена пароля, все действия админа.
      Только обезличенные данные, без паролей и токенов.
- [ ] TLS собственный CA через OpenSSL → ключ сервера → CSR → сертификат;
      Ktor sslConnector.
- [ ] OpenAPI-описание API (документация + используется ZAP API Scan в этапе 6).
Критерии все сценарии проходят через HTTPS; USER получает 403 на admin-эндпоинтах;
в логах нет паролей и токенов.

### Этап 5. Android-приложение
- [ ] Стек Jetpack Compose, Navigation, Ktor Client, kotlinx.serialization,
      Room, WorkManager, DataStore.
- [ ] Токены — в зашифрованном хранилище с ключом из Android Keystore.
- [ ] Доверие только нашему CA (network_security_config); поле адреса сервера в настройках.
- [ ] Экраны пользователя вход, регистрация (3 шага), лента (карточки заголовок,
      теги, издание, язык, скор; фильтры, поиск), оценка, жалоба, настройки
      (частота уведомлений, смена пароля), выход.
- [ ] Экраны админа статьи, пользователи, жалобы, журналы, статистика воронки.
      Навигация по роли (сервер всё равно проверяет роль сам).
- [ ] Room cached_article, cached_tag, my_profile, pending_action.
      Админские данные не кешируются.
- [ ] Outbox оценки, жалобы, события «открыл» — отправляются при появлении сети.
- [ ] Открытие статьи через Custom Tabs (браузер сам предупреждает об опасных ссылках).
- [ ] Уведомления WorkManager + new-count + локальное уведомление.
- [ ] Обработка ошибок понятные сообщения, повтор; общий экран-«убежище»
      при сбое с возвратом на главный экран.
Критерии полный сценарий гость → регистрация → лента → оценка → жалоба →
админ разбирает жалобу; оценка без сети доходит до сервера после восстановления сети.

### Этап 6. Безопасность доводка и проверка
- [ ] SAST detekt, Android Lint, OWASP Dependency-Check.
- [ ] SpotBugs + FindSecBugs для Java-модуля core (detekt Java не проверяет).
- [ ] DAST OWASP ZAP (baseline scan) против API.
- [ ] Резервное копирование mysqldump → подпись закрытым ключом (openssl dgst -sign)
      → шифрование открытым ключом → офлайн-носитель. Скрипт восстановления
      проверяет подпись перед восстановлением.
- [ ] Просмотр журналов в админке (только ADMIN), ротация файлов логов.
- [ ] Проверка обновлений зависимостей.
- [ ] Документ план аварийного восстановления.
Критерии отчёты SASTDAST без критических замечаний; восстановление из бэкапа
проверено на копии базы.

### Этап 7. Диплом и демонстрация
- [ ] Сценарий демонстрации (ноутбук + телефон в одной сети  раздаче).
- [ ] Метрики для текста статистика воронки, точность тегов на выборке,
      время обработки.
- [ ] Актуализация текста курсовой (см. отдельный список).

## 3. Соответствие мерам безопасности

 Мера  Где реализуется 
------
 CIA-триада, OWASP Top 10  Текст диплома + этапы 4, 6 
 Обязательная аутентификация  Этап 4 (JWT на всех закрытых эндпоинтах) 
 Хеширование bcryptArgon2, соль, отказ от MD5SHA1, запрет открытых паролей  Этап 2 (V3), этап 4 
 Политика надёжных паролей  Этап 4 (минимум) 
 Ротация паролей  Этап 4 смена пароля + запрет повторов; принудительная — в «Позже» 
 Сессии и контроль входа  Этап 4 access + refresh_token 
 Защита админки от прямого доступа  Этап 4 проверка роли на сервере 
 Выход со сбросом сессии  Этап 4 отзыв refresh-токена; этап 5 очистка хранилища, переход на вход 
 Доступ только к своим ресурсам, RBAC  Этап 4 
 Перенаправление по роли после входа  Этап 5 
 Проверка роли перед просмотромизменениемудалением  Этап 4 
 Валидация ввода  Этап 4 
 SQL Injection  Этапы 1, 4 только параметризованный HQL 
 XSS  Этап 3 очистка заголовков; Compose не исполняет HTML 
 Command Injection  Внешние команды не вызываются; контроль в SAST (этап 6) 
 Журналирование действий и событий безопасности  Этап 4 audit_log 
 Только обезличенные данные в логах  Этап 4 
 Анализ и ротация журналов, просмотр только админом  Этапы 0, 4, 5, 6 
 Понятные ошибки без технических деталей, отдельный файл ошибок  Этапы 4, 5 
 Страница-«убежище»  Этап 5 
 Шифрование хранимых данных  Этап 5 (токены, Keystore); этап 6 (бэкапы) 
 TLS  Этап 4 
 Ключи, CSR, самоподписанный сертификат через OpenSSL  Этап 4 
 Подпись файла и проверка подписи  Этап 6 (бэкапы) 
 Шифрование открытым ключом  Этап 6 (бэкапы) 
 Обновления ПО  Этап 6 
 Офлайн-бэкап, план восстановления  Этап 6 
 Обучение против фишинга  Только текст диплома 
 SASTDAST  Этап 6 

## 4. Позже  под вопросом (обратить внимание)

- [ ] Эксперимент с decision-моделями (Julia-1, Laya, новые) через
      llama.cpp /v1/systemone за интерфейсом ArticleClassifier.
- [ ] Закрыть все ИИ-потребности агрегатора только внутренними моделями
      (включая мультиязычность).
- [ ] Роль MODERATOR (разбор жалоб без управления пользователями). ← обратить внимание
- [ ] 2FA (коды по email через Jakarta Mail).
- [ ] Принудительная периодическая ротация паролей как настраиваемая политика.
- [ ] Эмбеддинги / векторный профиль пользователя — направление развития.
- [ ] Доступ вне локальной сети (Tailscale).