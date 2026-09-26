# Список улучшений проекта NewsDove

---

## 🔴 КРИТИЧНО — Безопасность

### 1. Пароль к БД в открытом XML-файле
**Файл:** `hibernate.cfg.xml` строки 12–13  
Учётные данные БД (логин и пароль) вписаны прямо в конфиг. Файл добавлен в `.gitignore`, но при случайном коммите или передаче проекта — учётные данные утекут.  
**Решение:** вынести в отдельный `db.properties` (по образцу `email.properties`), загружать программно в `HibernateUtil`.


### 3. Пробел в URL подключения к БД
**Файл:** `hibernate.cfg.xml` строка 11  
`jdbc:mysql://localhost:3306/db_use ?useSSL=false` — пробел перед `?` вызывает ошибку парсинга URL на некоторых версиях MySQL Connector.  
**Решение:** убрать пробел → `db_use?useSSL=false`.

---

## 🔴 КРИТИЧНО — Утечки ресурсов (Session Leak)

### 4. Session не закрывается при исключении в `TagPercentService`
**Файл:** `TagPercentService.java` строки 76, 146, 173, 316  
Методы `getTopUserTags()`, `getTopArticleTags()`, `getHighlyRelevantArticles()`, `recommendArticlesForUser()` открывают `Session` через `try { ... } finally { session.close() }`, но если исключение возникнет ДО блока `finally` при создании сессии — утечка.  
Хуже: `session.close()` не вызывается при `RuntimeException` внутри `try`.  
**Решение:** заменить на `try (Session session = ...) { ... }`.

### 5. Session не закрывается в `ArticleService.searchArticlesByKeyword()`
**Файл:** `ArticleService.java` строки 211–221  
Используется `Session session = HibernateUtil.getSessionFactory().openSession()` без try-with-resources. При исключении сессия не закроется.  
**Решение:** `try (Session session = ...) { ... }` + перенести запрос в `ArticleDAOImpl`.

---

## 🟠 ПРОИЗВОДИТЕЛЬНОСТЬ

### 6. N+1 запрос к БД при сохранении тегов
**Файл:** `ArticleSaver.java` метод `saveTags()` строки 459–490  
Для каждого тега статьи вызывается `tagDAO.findByName(tagName)` — отдельный SELECT на каждый тег. Если статья имеет 10 тегов — 10 SELECT + 10 INSERT/SKIP.  
**Решение:** перед циклом загрузить все нужные теги одним запросом `tagDAO.findByNames(List<String>)`, заполнить Map `name → Tag`, затем итерировать по Map без обращения к БД.

### 7. Все теги загружаются из БД на каждое сохранение статьи
**Файл:** `ArticleSaver.java` строка 142  
`tagDAO.findAll()` вызывается внутри `save()` — при параллельной обработке 25 URL это 25 одновременных полных загрузок таблицы тегов.  
**Решение:** передавать уже загруженный `List<Tag>` (или Map) снаружи, как это сделано для `existingTagNames` в `EmailScheduler`, но также внутри `ArticleSaver` — принять список готовых объектов `Tag`, не только имён.

### 8. Алгоритм Левенштейна без кэша, квадратичный по числу тегов
**Файл:** `ArticleSaver.java` метод `normalizeAndRemapTags()` строки 296–347  
При 1000 тегов в БД и 10 тегах статьи: 10 × 1000 = 10 000 вызовов `levenshtein()`, каждый выделяет матрицу `int[la+1][lb+1]`.  
**Решение:** при холодном старте (мало тегов) — приемлемо. При росте — хранить теги в `TrieMap` или использовать Jaro-Winkler (быстрее Levenshtein). Минимум: заранее строить `Set<String>` для точного совпадения и запускать Levenshtein только если точного нет.

### 9. Нет пула соединений к БД
**Файл:** `hibernate.cfg.xml`  
Hibernate без явного пула (C3P0 / HikariCP) использует встроенный однопоточный пул — непригоден для параллельных запросов от 5 потоков `articleExecutor`.  
**Решение:** добавить HikariCP в `pom.xml` и настроить в `hibernate.cfg.xml`:
```xml
<property name="hibernate.connection.provider_class">
    org.hibernate.hikaricp.internal.HikariCPConnectionProvider
</property>
<property name="hibernate.hikari.maximumPoolSize">10</property>
```

### 10. `show_sql=true` и `format_sql=true` включены в продакшн-конфиге
**Файл:** `hibernate.cfg.xml` строки 18–19  
SQL-логирование каждого запроса в консоль значительно тормозит производительность при интенсивной работе.  
**Решение:** отключить оба параметра (`false`) в рабочей конфигурации, оставить только для дев-режима.

### 11. Двойной запрос к БД по URL дедупликации
**Файл:** `ArticleSaver.java` строки 179–182  
`articleDAO.findByLink(extracted.getUrl())` — первый запрос. Потом `articleDAO.findByLink(normalizedUrl)` — второй запрос, даже если URL совпадают. В большинстве случаев оба запроса одинаковы.  
**Решение:** сначала нормализовать URL, затем один запрос по нормализованному URL.

### 12. Загрузка тегов в `ArticleExtractor.extract(String url)` (overload)
**Файл:** `ArticleExtractor.java` строки 108–113  
Overload-метод без `existingTagNames` вызывает `tagDAO.findAll()` при каждом вызове. Не используется в основном pipeline, но может вызываться из тестов или других мест.  
**Решение:** кэшировать результат на уровне `ArticleExtractor` с TTL (например, 5 минут), чтобы не дёргать БД при каждом вызове.

### 13. IMAP соединение создаётся и закрывается при каждом запуске pipeline
**Файл:** `EmailReader.java` строки 33–88  
При каждом вызове `readAndProcess()` создаётся новое IMAP-соединение, аутентификация, открытие папки, закрытие. При интервале 30 минут — не критично. При `often` интервале (5 минут) — излишние накладные расходы.  
**Решение:** держать долгоживущее соединение с переподключением при обрыве, либо использовать IMAP IDLE вместо polling.

---

## 🟡 ХАРДКОД — значения которые должны быть в конфиге

### 14. URL и модель Ollama захардкожены
**Файл:** `ArticleExtractor.java` строки 18–19  
```java
private static final String API_URL = "http://localhost:11434/api/chat";
private static final String MODEL   = "llama3.2";
```
При смене модели или переносе Ollama на другой хост — нужно перекомпилировать.  
**Решение:** добавить в `ai.properties`:
```properties
ollama.url=http://localhost:11434
ollama.model=llama3.2
ollama.max_text_length=2000
```

### 15. Интервал и параметры EmailScheduler захардкожены
**Файл:** `EmailScheduler.java` строки 32–35  
```java
private static final int INTERVAL_MINUTES  = 30;
private static final int PARALLEL_REQUESTS = 5;
private static final int CLEANUP_DAYS      = 60;
private static final int CLEANUP_MAX_READS = 0;
```
**Решение:** вынести в `email.properties` или отдельный `scheduler.properties`.

### 16. Системные домены и темы фильтра писем — список внутри метода
**Файл:** `EmailScheduler.java` метод `isSystemEmail()` строки 87–99  
`systemDomains` и `systemSubjects` создаются как `List.of(...)` при каждом вызове метода (а метод вызывается для каждого письма).  
**Решение:** вынести в `private static final List<String>` на уровне класса.

### 17. Размеры окон захардкожены в контроллерах
**Файл:** `MainScreenController.java` строка 808, `SearchByKeywordController.java` строка 312  
`new Scene(root, 520, 932)` и `new Scene(root, 430, 932)`.  
**Решение:** вынести в константы или в `application.properties`.

### 18. Лимит статей для главного экрана захардкожен
**Файл:** `MainScreenController.java` строка 233  
`articleService.getRecentArticlesWithTags(100)` — магическое число 100.  
**Решение:** `private static final int FEED_LIMIT = 100;`

### 19. User-Agent захардкожен
**Файл:** `ArticleFetcher.java` строка 39–40  
`"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"` — устаревшая версия Chrome.  
**Решение:** вынести в константу, легко менять без поиска по коду.

### 20. Частота уведомлений задаётся магическими строками
**Файл:** `MainScreenController.java` строки 143–160, `SearchByKeywordController.java` строка 280  
Строки `"often"`, `"rarely"`, `"never"` разбросаны по нескольким местам. При опечатке — тихий баг.  
**Решение:** создать `enum NotificationFrequency { OFTEN, RARELY, NEVER }` с методами `getThreshold()` и `getPollIntervalSec()`.

---

## 🟡 АРХИТЕКТУРНЫЕ ПРОБЛЕМЫ

### 21. Singleton-антипаттерн в контроллере JavaFX
**Файл:** `SearchByKeywordController.java` строки 105–108  
```java
private static SearchByKeywordController instance;
public static SearchByKeywordController getInstance() { return instance; }
```
JavaFX контроллеры создаются `FXMLLoader`-ом и имеют свой жизненный цикл. Статическая ссылка может указывать на уничтоженный контроллер.  
**Решение:** передавать callback через `EmailScheduler` (уже принимает `Runnable` / `Consumer`), или использовать Event Bus / JavaFX `Platform.runLater` с прямой ссылкой на актуальный контроллер.

### 22. `ArticleSaver` зависит от JavaFX
**Файл:** `ArticleSaver.java` строка 221  
```java
javafx.application.Platform.runLater(onArticleSaved);
```
Слой сохранения данных знает о JavaFX — нарушение слоёв. При использовании без UI (юнит-тест, командная строка) — ошибка.  
**Решение:** `onArticleSaved` должен быть просто `Runnable` (уже так), а оборачивание в `Platform.runLater()` — ответственность вызывающей стороны (`EmailScheduler`).

### 23. Дублирование кода выхода из системы
**Файл:** `MainScreenController.java` строки 800–813, `SearchByKeywordController.java` строки 306–319  
Методы `onConfirmLogout()` почти идентичны (разные размеры окна, но логика одна).  
**Решение:** вынести в утилитный метод `NavigationUtils.openLoginScreen(Node source, double width, double height)`.

### 24. `ArticleService.searchArticlesByKeyword()` обходит DAO
**Файл:** `ArticleService.java` строки 210–223  
Метод напрямую обращается к Hibernate Session, минуя `ArticleDAOImpl`. Логика БД в сервисном слое.  
**Решение:** перенести запрос в `ArticleDAOImpl.findByKeyword(String)`.

### 25. `TagPercentService` содержит прямые HQL-запросы в методах
**Файл:** `TagPercentService.java` строки 77, 148, 174, 317  
Несколько методов используют прямой Session вместо DAO. Смешанный стиль: часть через DAO, часть — напрямую.  
**Решение:** перенести все запросы в DAO-реализации, сервис работает только с объектами.

### 26. `ArticleExtractor` создаёт `TagDAOImpl` в конструкторе
**Файл:** `ArticleExtractor.java` строка 39  
AI-слой напрямую зависит от DAO. Нарушение dependency rule — DAO должен быть на уровне сервисов.  
**Решение:** передавать `List<String> existingTagNames` всегда снаружи (как уже сделано в pipeline). Убрать `TagDAOImpl` из `ArticleExtractor`, удалить overload `extract(String url)`.

### 27. `GenericDAOImpl` открывает новую Session для каждой операции
**Файл:** `GenericDAOImpl.java` все методы  
`save`, `update`, `delete`, `deleteById`, `findById`, `findAll`, `count` — каждый открывает и закрывает Session. Если несколько операций нужно выполнить в одной транзакции — нет способа это сделать через DAO.  
**Решение:** для `ArticleSaver.saveTags()`, который сохраняет статью + несколько тегов + ArticleTagPercent — это уже обходится через прямую Session в `adjustUserTagWeights()`. Для пакетного сохранения тегов добавить метод `saveAll(List<T>)` с одной транзакцией.

---

## 🟢 НЕЗНАЧИТЕЛЬНЫЕ УЛУЧШЕНИЯ

### 28. Устаревшие методы без аннотации `@Deprecated`
**Файлы:**  
- `EmailReader.java` строка 95: `readUnreadEmails()` — помечен в javadoc как устаревший, но нет `@Deprecated`  
- `ArticleFetcher.java` строка 72: `fetchArticleText()` — аналогично  
**Решение:** добавить аннотацию `@Deprecated`.

### 29. Создание лишнего `List.of()` при проверке URL после редиректа
**Файл:** `ArticleExtractor.java` строка 71  
```java
if (linkFilter.filter(java.util.List.of(canonicalUrl)).isEmpty())
```
Создаёт новый список на каждую статью только чтобы передать один URL в фильтр.  
**Решение:** добавить в `LinkFilter` метод `boolean isLikelyArticle(String url)` с public доступом — он уже есть как private.

### 30. `MinPercent` = 50 захардкожено в `recommendArticlesForUser`
**Файл:** `TagPercentService.java` строка 340  
**Решение:** вынести в константу `private static final int MIN_RECOMMEND_PERCENT = 50;`

### 31. `SLF4J` объявлен только для `test` scope, логирование идёт через `System.out/err`
**Файл:** `pom.xml` строка 50  
Весь проект использует `System.out.println()` и `System.err.println()`. Нет возможности контролировать уровень логирования, форматирование, запись в файл.  
**Решение:** перевести SLF4J в `compile` scope, заменить `System.out/err` на `Logger` через `LoggerFactory`.

### 32. `List<Article> systemDomains` создаётся при каждом вызове isSystemEmail
Уже описано в п. 16 — для наглядности продублировано.

### 33. Пробел в имени БД в URL соединения
**Файл:** `hibernate.cfg.xml` строка 11  
`db_use ?useSSL` — пробел перед `?`. Это баг, который может не проявляться из-за того что MySQL connector иногда trim-ит URL, но лучше исправить.

### 34. Комментарии "// Добавьте этот метод" остались в коде
**Файл:** `HibernateUtil.java` строка 18  
Технический комментарий из процесса разработки, не несёт смысловой нагрузки.  
**Решение:** удалить.

### 35. Строка запроса `"FROM " + entityClass.getSimpleName()` уязвима к HQL-инъекции
**Файл:** `GenericDAOImpl.java` строки 143, 175  
`entityClass.getSimpleName()` — в данном случае безопасно (это класс, не пользовательский ввод), но выглядит подозрительно.  
**Решение:** использовать Criteria API вместо строковой конкатенации для `findAll()` и `count()`.

---

## Сводная таблица приоритетов

| # | Приоритет | Тип | Сложность |
|---|-----------|-----|-----------|
| 1 | 🔴 Критично | Безопасность | Низкая |
| 3 | 🔴 Критично | Баг | Низкая |
| 4–5 | 🔴 Критично | Утечка ресурсов | Низкая |
| 6–7 | 🟠 Высокий | Производительность | Средняя |
| 8 | 🟠 Высокий | Производительность | Средняя |
| 9 | 🟠 Высокий | Производительность | Средняя |
| 10 | 🟠 Высокий | Производительность | Низкая |
| 11–13 | 🟠 Высокий | Производительность | Низкая–Средняя |
| 14–20 | 🟡 Средний | Хардкод | Низкая |
| 21–27 | 🟡 Средний | Архитектура | Средняя–Высокая |
| 28–35 | 🟢 Низкий | Качество кода | Низкая |
