# T-8 Baseline — первый сквозной прогон пайплайна

Дата прогона: **2026-10-28**, начало 00:22:23, конец 00:24:31.

## Конфигурация

| Ключ | Значение |
|---|---|
| scheduler.enabled | true |
| scheduler.cleanup.enabled | false |
| scheduler.max.age.days | 1 |
| scheduler.max.emails.per.cycle | 2 |
| scheduler.parallel.requests | 5 |
| ollama.model | llama3.2 |
| DB | db_use_test (newsdove_dev) |

## Исходное состояние БД

| Таблица | Строк до |
|---|---|
| article | 72 |
| tag | 257 |
| article_tag_percent | 363 |
| user | 10 |
| report | 8 |
| user_tag_percent | 70 |

## Воронка (email-уровень)

| | Письмо 1 | Письмо 2 |
|---|---|---|
| Отправитель | DER SPIEGEL – Der Tag (`newsletter@angebote.spiegel.de`) | DER SPIEGEL – Die Lage am Morgen (`newsletter@angebote.spiegel.de`) |
| Тема | "Der Tag 5 Uhr" | "Hat Israel genug von Netanyahu?" |
| Ссылок извлечено | 223 | 45 |
| После LinkFilter | 4 | 4 |
| Загружено текста | 4 | 4 |
| Вызовов Ollama (первичных) | 4 | 4 |
| Вызовов Ollama (перевод) | 2 | 1 |
| Новых статей в БД | 0 | 2 |
| «Дублей / отклонено» | 4 | 2 |

**Итог:** 2 письма, 8 ссылок, **2 статьи** сохранено, 6 отклонено.
`=== Итог: писем 2, обработано 2, отклонено 6 ===`

## Письмо 1 — разбор по ссылкам

Все 4 ссылки — трекинговые редиректы angebote.spiegel.de; 3 click-ссылки ведут к одной spiegel.de-статье о Флавио Болсонаро.

| Поток | Исходный URL | Разрешился в | Результат |
|---|---|---|---|
| pool-2-thread-4 | `click.angebote.spiegel.de/...jaOX7UGf...` | `spiegel.de/.../brasilien-flavio-bolsonaro...` | **Ошибка JSON** (`\u00ep`) |
| pool-2-thread-3 | `click.angebote.spiegel.de/...jaOTeMv57...` | то же spiegel.de | **Ошибка JSON** (`\u00ei`) |
| pool-2-thread-2 | `click.angebote.spiegel.de/...jaONiSGMu...` | то же spiegel.de | Перевод → "Flavio Bolsonaro Must Pass on to Leave Brazil, Report Says" → **дубль** |
| pool-2-thread-1 | `view.angebote.spiegel.de/...jaOL61lq...` | `view.angebote.spiegel.de/...` | → "Bolsonaro Loses Election in Brazil, Exiled from Country" → **дубль** |

Итог письма 1: `✅ успешно сохранено: создано 0, дублей 4`

### Тексты ошибок парсинга JSON

**Ошибка 1** (pool-2-thread-4, URL `...jaOX7UGf...`):
```
Ответ модели (первые ~220 символов):
{"title": "Flavio Bolsonaro muss P\u00epsse abgeben und im Land bleiben",
 "url": "https://www.spiegel.de/ausland/brasilien-flavio-bolsonaro-muss-paesse-abgeben-...",
 "language": "German", "region": "Germany", "publication": "Der Spiegel", ...

Jackson: Unexpected character ('p' (code 112)): expected a hex-digit
  for character escape sequence
  at [line: 1, column: 40] (through reference chain: ExtractedArticle["title"])
```

**Ошибка 2** (pool-2-thread-3, URL `...jaOTeMv57...`):
```
Ответ модели (первые ~200 символов):
{"title": "Bolsonaro muss P\u00eigsse abgeben und im Land bleiben",
 "url": "https://www.spiegel.de/ausland/brasilien-flavio-bolsonaro-muss-paesse-abgeben-...",
 "language": "German", "region": "Germany", "publication": "Der Spiegel", ...

Jackson: Unexpected character ('i' (code 105)): expected a hex-digit
  for character escape sequence
  at [line: 1, column: 33] (through reference chain: ExtractedArticle["title"])
```

**Корень проблемы:** Ollama генерирует `\u00ep` и `\u00ei` как попытки закодировать немецкую «ä» (правильно: `ä`). Буквы `p` и `i` — невалидные шестнадцатеричные цифры. Ollama API `format` параметр (JSON Schema) не используется: JSON строится только из подсказки в промпте, что допускает подобные галлюцинации.

## Письмо 2 — разбор по ссылкам

| Поток | Исходный URL | Разрешился в | Результат |
|---|---|---|---|
| pool-2-thread-4 | `click.angebote.spiegel.de/...lAewOF1...` | `spiegel.de/thema/spiegel-die-lage/` (844 символа) | "Die Lage" → перевод → **"The Situation"** → сохранено (id=258) |
| pool-2-thread-2 | `click.angebote.spiegel.de/...lI0x1o...` | `spiegel.de/politik/.../news-wahl-in-israel-...` (3000 символов) | **"Israel Elects New Knesset, With Netanyahu's Future Uncertain"** → сохранено (id=259) |
| pool-2-thread-3 | `click.angebote.spiegel.de/...lHfp7G...` | то же `news-wahl-in-israel-...` | "Israel election: Netanyahu faces challenge from Gadi Eisenkot" (4 тега вместо 5) → **дубль по URL** (id=259 уже в БД) |
| pool-2-thread-5 | `view.angebote.spiegel.de/...k9vZxQ...` | `view.angebote.spiegel.de/...` | "Israel Chooses New Knesset" → **дубль по URL** (уже в БД) |

Итог письма 2: `✅ успешно сохранено: создано 2, дублей 2`

## Сохранённые статьи

| id | title | link | language | region | publisher |
|---|---|---|---|---|---|
| 258 | The Situation | `spiegel.de/thema/spiegel-die-lage/` | German | Germany | Der Spiegel |
| 259 | Israel Elects New Knesset, With Netanyahu's Future Uncertain | `spiegel.de/politik/.../news-wahl-in-israel-...` | German | Germany | Der Spiegel |

### Почему publisher=«Der Spiegel», language=German, region=Germany?

Оба поля берутся **напрямую из JSON-ответа Ollama**:
```json
{"publication": "Der Spiegel", "language": "German", "region": "Germany", ...}
```
Код (ArticleSaver) сохраняет `extractedArticle.getPublication()` → `article.publisher` и аналогично для language/region. Контекст письма (отправитель) не используется.

Обе статьи действительно размещены на spiegel.de и написаны по-немецки, поэтому AI прав.
Для id=259 (`news-wahl-in-israel`): тема — выборы в Израиле, но статья — немецкая, отсюда region=Germany (статья про Израиль, написана для немецкой аудитории). Это известный недостаток текущей схемы без явного разделения «язык публикации» и «регион описываемых событий».

### Новые теги

| id | name |
|---|---|
| 258 | world news |
| 259 | knesset |
| 260 | gadi eisenkot |

### Известные проблемы, выявленные в прогоне

1. **Levenshtein false match**: "Wahl" → "war" (расстояние 2 = допустимо, но смысл неверный). "Politik" → "politic" (OK).
2. **Тематическая страница сохранена как статья**: id=258 — `spiegel.de/thema/spiegel-die-lage/` — это раздел рубрики, не статья. Нужна проверка `og:type=article` (Этап 3).
3. **MAX_PER_DOMAIN=3**: письмо 1 — 223 ссылки → 4 после фильтра (все 4 — трекинговые редиректы, реальные статьи отсечены). Наблюдение зафиксировано в ROADMAP Этап 3.
4. **Ошибки JSON от Ollama**: невалидные Unicode-эскейпы (`\u00ep`, `\u00ei`) — статьи теряются молча. Решение: Ollama `format` (JSON Schema) + очередь с повтором (Этап 3).

## Хронология цикла

| Событие | Время | Δ от старта |
|---|---|---|
| Старт pipeline | 00:22:23 | 0 с |
| Письмо 1: загрузка текстов всех 4 ссылок | ~00:22:51 | ~28 с |
| Письмо 1: последний AI-ответ (thread-1, перевод) | ~00:23:56 | ~93 с |
| Письмо 2: первый AI-результат (thread-4, Die Lage) | 00:24:23 | ~120 с |
| Письмо 2: сохранено id=259, теги knesset + gadi eisenkot | 00:24:30 | ~127 с |
| Письмо 2: последняя проверка дубля (thread-5, view URL) | 00:24:31 | ~128 с |

**Итого: 128 секунд ≈ 2 мин 8 сек** для 2 писем / 8 ссылок.

| Метрика | Значение |
|---|---|
| Время цикла | 128 с |
| Ссылок обработано | 8 |
| Среднее на ссылку (wall, serial-эквивалент) | 16 с |
| Письмо 1 (4 ссылки параллельно) | 93 с стена |
| Письмо 2 (4 ссылки параллельно) | 35 с стена |
| Сохранено / обработано | 2 / 8 = 25% |
| Вызовов Ollama | 8 первичных + 3 перевода = 11 |

> Письмо 1 значительно медленнее письма 2 (93 с vs 35 с): 3 потока из 4 обращались к одному spiegel.de URL, и Ollama обрабатывал похожие тексты трижды. В письме 2 URL-ы разнообразнее.

## Итоговое состояние БД

| Таблица | Строк после | Δ |
|---|---|---|
| article | 74 | +2 ✅ |
| tag | 260 | +3 ✅ |
| article_tag_percent | 373 | +10 ✅ |
| user | 10 | 0 ✅ |
| report | 8 | 0 ✅ |
| user_tag_percent | 70 | 0 ✅ |

## Выводы

- **25% ответов ИИ — невалидный JSON**: 2 из 8 первичных вызовов вернули несуществующие escape-последовательности (`\u00ep`, `\u00ei` вместо `ä` для «ä»). Статьи теряются молча. Ожидаемое решение: параметр `format` (JSON Schema) в Ollama API + очередь с повтором — Этап 3.
- **Регион из страны издания, а не события**: модель вернула `region=Germany` (страна издания Der Spiegel) вместо `Israel` (место событий). Язык `German` — верный; именно поэтому заголовок переведён вторым вызовом ИИ. Потребуется явное разделение полей «язык публикации» и «регион события» — Этап 3.
- **Одна ссылка обработана двумя потоками одновременно**: два click-редиректа указывали на один и тот же spiegel.de URL; оба потока независимо загрузили страницу и отправили запросы к Ollama. Дубль обнаружен только при попытке сохранения. Решение: дедупликация по хешу финального URL до загрузки + поле `UNIQUE url_hash` в таблице `article_queue` — Этап 3.
- **Тематическая страница сохранена как статья**: `spiegel.de/thema/spiegel-die-lage/` — раздел рубрики, не статья. Нужна проверка «статья ли это» по разметке (`og:type=article`, `article:published_time`, минимальная длина текста) — Этап 3.
- **Производительность (базовая линия)**: 128 с на 8 ссылок, 11 вызовов ИИ, ~16 с/ссылку в последовательном эквиваленте. Для сравнения после перехода на JSON Schema и очередь.
- **Немецкие умлауты — источник escape-ошибок**: текущий пайплайн передаёт в промпт исходный немецкий текст; Ollama сериализует «ä/ö/ü» вручную без гарантий валидности. Устраняется переходом на `format` JSON Schema.
