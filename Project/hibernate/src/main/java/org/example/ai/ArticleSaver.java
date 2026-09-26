package org.example.ai;

import org.example.Article;
import org.example.ArticleTagPercent;
import org.example.ArticleTagId;
import org.example.Tag;
import org.example.dao.impl.ArticleDAOImpl;
import org.example.dao.impl.ArticleTagPercentDAOImpl;
import org.example.dao.impl.TagDAOImpl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Сохраняет данные, извлечённые AI, в базу данных.
 * Включает расширенную валидацию и нормализацию тегов.
 */
public class ArticleSaver {

    // ── Нормализация языка: ISO-коды (639-1 и 639-2) и варианты → полное название ────────────
    private static final Map<String, String> LANGUAGE_MAP = Map.ofEntries(
            Map.entry("en",          "English"),
            Map.entry("eng",         "English"),
            Map.entry("english",     "English"),
            Map.entry("de",          "German"),
            Map.entry("deu",         "German"),
            Map.entry("ger",         "German"),
            Map.entry("german",      "German"),
            Map.entry("ru",          "Russian"),
            Map.entry("rus",         "Russian"),
            Map.entry("russian",     "Russian"),
            Map.entry("he",          "Hebrew"),
            Map.entry("heb",         "Hebrew"),
            Map.entry("hebrew",      "Hebrew"),
            Map.entry("ar",          "Arabic"),
            Map.entry("ara",         "Arabic"),
            Map.entry("arabic",      "Arabic"),
            Map.entry("fr",          "French"),
            Map.entry("fra",         "French"),
            Map.entry("fre",         "French"),
            Map.entry("french",      "French"),
            Map.entry("es",          "Spanish"),
            Map.entry("spa",         "Spanish"),
            Map.entry("spanish",     "Spanish"),
            Map.entry("ro",          "Romanian"),
            Map.entry("ron",         "Romanian"),
            Map.entry("rum",         "Romanian"),
            Map.entry("romanian",    "Romanian"),
            Map.entry("uk",          "Ukrainian"),
            Map.entry("ukr",         "Ukrainian"),
            Map.entry("ukrainian",   "Ukrainian"),
            Map.entry("it",          "Italian"),
            Map.entry("ita",         "Italian"),
            Map.entry("italian",     "Italian"),
            Map.entry("pt",          "Portuguese"),
            Map.entry("por",         "Portuguese"),
            Map.entry("portuguese",  "Portuguese"),
            Map.entry("zh",          "Chinese"),
            Map.entry("zho",         "Chinese"),
            Map.entry("chi",         "Chinese"),
            Map.entry("chinese",     "Chinese"),
            Map.entry("ja",          "Japanese"),
            Map.entry("jpn",         "Japanese"),
            Map.entry("japanese",    "Japanese")
    );

    // ── Нормализация региона: аббревиатуры → полное название ──────────────────
    private static final Map<String, String> REGION_MAP = Map.ofEntries(
            Map.entry("us",            "United States"),
            Map.entry("usa",           "United States"),
            Map.entry("united states", "United States"),
            Map.entry("uk",            "United Kingdom"),
            Map.entry("gb",            "United Kingdom"),
            Map.entry("united kingdom","United Kingdom"),
            Map.entry("eu",            "Europe"),
            Map.entry("de",            "Germany"),
            Map.entry("ru",            "Russia"),
            Map.entry("il",            "Israel"),
            Map.entry("ua",            "Ukraine"),
            Map.entry("fr",            "France"),
            Map.entry("md",            "Moldova"),
            Map.entry("by",            "Belarus"),
            Map.entry("cn",            "China"),
            Map.entry("jp",            "Japan")
    );

    // ── Нормализация издания: известные варианты → каноническое имя ───────────
    private static final Map<String, String> PUBLISHER_MAP = Map.ofEntries(
            Map.entry("newsmaker md",          "Newsmaker"),
            Map.entry("newsmaker.md",          "Newsmaker"),
            Map.entry("newsmaker moldova",     "Newsmaker"),
            Map.entry("bbc news english",      "BBC"),
            Map.entry("bbc news",              "BBC"),
            Map.entry("reuters international", "Reuters"),
            Map.entry("ap news",               "AP"),
            Map.entry("associated press",      "AP"),
            Map.entry("new york times",        "The New York Times"),
            Map.entry("nyt",                   "The New York Times"),
            Map.entry("washington post",       "The Washington Post"),
            Map.entry("the guardian us",       "The Guardian"),
            Map.entry("the guardian uk",       "The Guardian"),
            Map.entry("der spiegel english",   "Der Spiegel"),
            Map.entry("spiegel international", "Der Spiegel"),
            Map.entry("haaretz english",       "Haaretz"),
            Map.entry("haaretz english edition","Haaretz")
    );

    // ── Чёрный список заголовков-ошибок ──────────────────────────────────────
    private static final List<String> TITLE_BLACKLIST = List.of(
            "javascript disabled", "access denied", "403", "404",
            "page not found", "enable javascript", "403 forbidden",
            "404 not found", "permission denied", "just a moment",
            "are you a robot", "captcha", "cloudflare", "please enable",
            "browser does not support", "cookies required"
    );

    private final ArticleDAOImpl            articleDAO;
    private final TagDAOImpl                tagDAO;
    private final ArticleTagPercentDAOImpl  articleTagPercentDAO;
    private Runnable                        onArticleSaved;

    public ArticleSaver() {
        this.articleDAO          = new ArticleDAOImpl();
        this.tagDAO              = new TagDAOImpl();
        this.articleTagPercentDAO = new ArticleTagPercentDAOImpl();
    }

    public void setOnArticleSaved(Runnable callback) {
        this.onArticleSaved = callback;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // MAIN SAVE
    // ═════════════════════════════════════════════════════════════════════════

    public boolean save(ExtractedArticle extracted) {
        if (extracted == null) {
            System.err.println("ArticleSaver: получен null — пропускаем");
            return false;
        }

        // 1. Базовая валидация (null-поля)
        ValidationResult basic = validateBasic(extracted);
        if (!basic.isValid()) {
            System.err.println("ArticleSaver [базовая]: " + basic.getReason());
            return false;
        }

        // 1b. Нормализация language / region / publication
        normalizeMetadata(extracted);

        // 2. Загружаем все существующие теги из БД (один раз)
        List<String> allDbTagNames = tagDAO.findAll().stream()
                .map(t -> t.getName().toLowerCase().trim())
                .collect(Collectors.toList());

        // 3. Расширенная валидация (заголовок, язык, проценты)
        ValidationResult advanced = validateAdvanced(extracted);
        if (!advanced.isValid()) {
            System.err.println("ArticleSaver [расширенная]: " + advanced.getReason()
                    + " | Заголовок: " + extracted.getTitle());
            return false;
        }

        // 4. Нормализация и ремаппинг тегов через Levenshtein
        normalizeAndRemapTags(extracted, allDbTagNames);

        // 5. После нормализации — минимум 3 тега (мягче 5, т.к. могли удалить дубли/мусор)
        if (extracted.getTags() == null || extracted.getTags().size() < 3) {
            System.err.println("ArticleSaver: недостаточно тегов после нормализации ("
                    + (extracted.getTags() == null ? 0 : extracted.getTags().size())
                    + ") | " + extracted.getTitle());
            return false;
        }

        // 6. Нормализация суммы процентов → должна быть 100 (допуск ±3)
        normalizePercents(extracted);

        // 7. Проверка количества новых тегов (cold-start учитывается)
        long newTagCount = extracted.getTags().stream()
                .filter(t -> !allDbTagNames.contains(t.getName().toLowerCase().trim()))
                .count();
        int maxNewTags = allDbTagNames.size() < 20 ? 10 : 2;
        if (newTagCount > maxNewTags) {
            System.err.println("ArticleSaver: слишком много неизвестных тегов (" + newTagCount
                    + ", лимит=" + maxNewTags + ") — возможная галлюцинация | " + extracted.getTitle());
            return false;
        }

        // 7. Дедупликация
        try {
            // Нормализуем URL сразу — убираем query-params и сохраняем нормализованную версию
            String normalizedUrl = stripQueryParams(extracted.getUrl());
            extracted.setUrl(normalizedUrl);

            // 7a. По нормализованному URL (один запрос вместо двух)
            Optional<Article> existing = articleDAO.findByLink(normalizedUrl);
            if (existing.isPresent()) {
                System.out.println("Статья уже в БД (по URL): " + normalizedUrl);
                return false;
            }

            // 7b. По точному заголовку
            if (extracted.getTitle() != null) {
                Optional<Article> existingByTitle = articleDAO.findByTitle(extracted.getTitle());
                if (existingByTitle.isPresent()) {
                    System.out.println("Статья уже в БД (по заголовку точно): " + extracted.getTitle());
                    return false;
                }
            }

            // 7c. По похожему заголовку (первые 60 символов — защита от трекинговых дублей)
            if (extracted.getTitle() != null && extracted.getTitle().length() >= 20) {
                int prefixLen = Math.min(60, extracted.getTitle().length());
                String prefix = extracted.getTitle().substring(0, prefixLen);
                List<Article> similar = articleDAO.findByTitlePrefix(prefix);
                for (Article candidate : similar) {
                    if (isTitleSimilar(extracted.getTitle(), candidate.getTitle())) {
                        System.out.println("Статья уже в БД (похожий заголовок): \""
                                + candidate.getTitle() + "\"");
                        return false;
                    }
                }
            }

            // 8. Сохраняем статью
            Article article = buildArticle(extracted);
            Article saved   = articleDAO.save(article);
            System.out.println("Статья сохранена: [" + saved.getId() + "] " + saved.getTitle());

            // 9. Сохраняем теги
            saveTags(saved, extracted.getTags());

            // 10. Уведомляем вызывающую сторону (обёртку в Platform.runLater делает она сама)
            if (onArticleSaved != null) {
                onArticleSaved.run();
            }

            return true;

        } catch (Exception e) {
            System.err.println("Ошибка сохранения статьи: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // VALIDATION
    // ═════════════════════════════════════════════════════════════════════════

    private ValidationResult validateBasic(ExtractedArticle e) {
        if (e.getTitle() == null || e.getTitle().isBlank())
            return ValidationResult.fail("нет заголовка");
        if (e.getTitle().trim().length() < 10)
            return ValidationResult.fail("заголовок слишком короткий: " + e.getTitle());
        if (e.getUrl() == null || e.getUrl().isBlank())
            return ValidationResult.fail("нет URL");
        if (!e.getUrl().trim().startsWith("http"))
            return ValidationResult.fail("невалидный URL: " + e.getUrl());
        if (e.getPublication() == null || e.getPublication().isBlank())
            return ValidationResult.fail("нет издания (publication)");
        if (e.getTags() == null || e.getTags().isEmpty())
            return ValidationResult.fail("AI не определил теги");
        if (e.getTags().size() < 5)
            return ValidationResult.fail("слишком мало тегов: " + e.getTags().size()
                    + " (минимум 5) | publisher: " + e.getPublication());
        return ValidationResult.ok();
    }

    private ValidationResult validateAdvanced(ExtractedArticle e) {
        String titleLower = e.getTitle().toLowerCase().trim();

        // Проверка чёрного списка заголовков
        for (String banned : TITLE_BLACKLIST) {
            if (titleLower.contains(banned))
                return ValidationResult.fail("заголовок из чёрного списка: \"" + e.getTitle() + "\"");
        }

        // Нелатинский скрипт в заголовке — значит перевод не сработал
        if (containsNonLatinScript(e.getTitle()))
            return ValidationResult.fail("заголовок содержит нелатинский скрипт (не переведён): " + e.getTitle());

        // Все проценты одинаковые — признак галлюцинации
        if (e.getTags() != null && e.getTags().size() > 1) {
            long distinctPercents = e.getTags().stream()
                    .map(ExtractedArticle.TagEntry::getPercent)
                    .distinct().count();
            if (distinctPercents == 1)
                return ValidationResult.fail("все проценты тегов одинаковы — галлюцинация");
        }

        return ValidationResult.ok();
    }

    private boolean containsNonLatinScript(String text) {
        if (text == null) return false;
        return text.chars().anyMatch(c -> {
            Character.UnicodeBlock b = Character.UnicodeBlock.of(c);
            return b == Character.UnicodeBlock.CYRILLIC
                || b == Character.UnicodeBlock.ARABIC
                || b == Character.UnicodeBlock.HEBREW
                || b == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || b == Character.UnicodeBlock.HIRAGANA
                || b == Character.UnicodeBlock.KATAKANA
                || b == Character.UnicodeBlock.THAI
                || b == Character.UnicodeBlock.GREEK;
        });
    }

    // ═════════════════════════════════════════════════════════════════════════
    // TAG NORMALIZATION
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Нормализует теги: trim + lowercase, затем пытается сопоставить с существующими
     * тегами через точное совпадение и Levenshtein (расстояние ≤ 2).
     * Отбрасывает теги с percent ≤ 0 и теги длиннее 3 слов без совпадения в БД.
     */
    private void normalizeAndRemapTags(ExtractedArticle extracted, List<String> allDbTagNames) {
        if (extracted.getTags() == null) return;

        List<ExtractedArticle.TagEntry> result = new ArrayList<>();

        for (ExtractedArticle.TagEntry tag : extracted.getTags()) {
            if (tag.getPercent() <= 0) {
                System.out.println("  Тег отброшен (0%): " + tag.getName());
                continue;
            }

            String raw = tag.getName();
            if (raw == null || raw.isBlank()) continue;

            String normalized = raw.trim().toLowerCase();

            // Отбрасываем слишком длинные имена (галлюцинации AI)
            if (normalized.length() > 60) {
                System.out.println("  Тег отброшен (имя длиннее 60 символов): " + raw);
                continue;
            }
            // Отбрасываем чисто числовые теги
            if (normalized.matches("\\d+(\\.\\d+)?")) {
                System.out.println("  Тег отброшен (числовое имя): " + raw);
                continue;
            }

            // Точное совпадение (case-insensitive)
            Optional<String> exact = allDbTagNames.stream()
                    .filter(t -> t.equalsIgnoreCase(normalized))
                    .findFirst();
            if (exact.isPresent()) {
                result.add(new ExtractedArticle.TagEntry(exact.get(), tag.getPercent()));
                continue;
            }

            // Тег длиннее 3 слов — ищем по ключевым словам в БД
            String[] words = normalized.split("\\s+");
            if (words.length > 3) {
                String keywordMatch = findByKeyword(normalized, allDbTagNames);
                if (keywordMatch != null) {
                    System.out.println("  Тег переназначен (keyword): \"" + raw + "\" → \"" + keywordMatch + "\"");
                    result.add(new ExtractedArticle.TagEntry(keywordMatch, tag.getPercent()));
                } else {
                    System.out.println("  Тег отброшен (>3 слов, нет совпадения): " + raw);
                }
                continue;
            }

            // Fuzzy match через Levenshtein (расстояние ≤ 2)
            String fuzzy = findByLevenshtein(normalized, allDbTagNames, 2);
            if (fuzzy != null) {
                System.out.println("  Тег переназначен (Levenshtein): \"" + raw + "\" → \"" + fuzzy + "\"");
                result.add(new ExtractedArticle.TagEntry(fuzzy, tag.getPercent()));
                continue;
            }

            // Новый тег — оставляем в нижнем регистре
            result.add(new ExtractedArticle.TagEntry(normalized, tag.getPercent()));
        }

        // Дедупликация по имени: если AI вернул одно имя дважды — берём с наибольшим %
        Map<String, ExtractedArticle.TagEntry> seen = new LinkedHashMap<>();
        for (ExtractedArticle.TagEntry entry : result) {
            String key = entry.getName().toLowerCase().trim();
            if (!seen.containsKey(key) || entry.getPercent() > seen.get(key).getPercent()) {
                seen.put(key, entry);
            }
        }
        extracted.setTags(new ArrayList<>(seen.values()));
    }

    /** Ищет кандидата по вхождению ключевых слов (для тегов длиннее 3 слов). */
    private String findByKeyword(String tagName, List<String> candidates) {
        String[] words = tagName.split("\\s+");
        for (String word : words) {
            if (word.length() < 3) continue;
            for (String candidate : candidates) {
                if (candidate.contains(word)) return candidate;
            }
        }
        return null;
    }

    /** Возвращает ближайшего кандидата с расстоянием Левенштейна ≤ maxDist. */
    private String findByLevenshtein(String query, List<String> candidates, int maxDist) {
        String bestMatch = null;
        int    bestDist  = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            int dist = levenshtein(query, candidate);
            if (dist <= maxDist && dist < bestDist) {
                bestDist  = dist;
                bestMatch = candidate;
            }
        }
        return bestMatch;
    }

    /**
     * Убирает query-параметры из URL для нормализованного сравнения.
     * https://bbc.com/news?utm_source=email → https://bbc.com/news
     */
    private static String stripQueryParams(String url) {
        if (url == null) return null;
        int q = url.indexOf('?');
        return q >= 0 ? url.substring(0, q) : url;
    }

    /**
     * Возвращает true если два заголовка относятся к одной и той же статье:
     * — один является началом другого (трекинговые дубли с усечёнными заголовками)
     * — ИЛИ Levenshtein < 15% длины более длинного заголовка
     */
    private boolean isTitleSimilar(String a, String b) {
        if (a == null || b == null) return false;
        String al = a.toLowerCase().trim();
        String bl = b.toLowerCase().trim();
        if (al.equals(bl)) return true;

        String shorter = al.length() <= bl.length() ? al : bl;
        String longer  = al.length() <= bl.length() ? bl : al;
        if (shorter.length() >= 30 && longer.startsWith(shorter)) return true;

        int maxLen = Math.max(al.length(), bl.length());
        if (maxLen == 0) return true;
        return (double) levenshtein(al, bl) / maxLen < 0.15;
    }

    /**
     * Проверяет сумму процентов тегов.
     * Если отклонение > ±3 — пересчитывает пропорционально до 100.
     */
    private void normalizePercents(ExtractedArticle extracted) {
        List<ExtractedArticle.TagEntry> tags = extracted.getTags();
        if (tags == null || tags.isEmpty()) return;

        int total = tags.stream().mapToInt(ExtractedArticle.TagEntry::getPercent).sum();
        if (total == 0) return;

        if (Math.abs(total - 100) <= 3) return; // в допуске — не трогаем

        System.out.println("  Сумма процентов = " + total + " — нормализуем до 100");

        List<ExtractedArticle.TagEntry> normalized = new ArrayList<>();
        int assigned = 0;
        for (int i = 0; i < tags.size(); i++) {
            ExtractedArticle.TagEntry t = tags.get(i);
            if (i == tags.size() - 1) {
                // Последний тег получает остаток (минимум 1 чтобы не стать 0 или отрицательным)
                normalized.add(new ExtractedArticle.TagEntry(t.getName(), Math.max(1, 100 - assigned)));
            } else {
                int p = (int) Math.round(t.getPercent() * 100.0 / total);
                p = Math.max(1, p); // минимум 1%
                assigned += p;
                normalized.add(new ExtractedArticle.TagEntry(t.getName(), p));
            }
        }
        extracted.setTags(normalized);
    }

    /** Вычисляет расстояние Левенштейна между двумя строками. */
    private static int levenshtein(String a, String b) {
        int la = a.length(), lb = b.length();
        int[][] dp = new int[la + 1][lb + 1];
        for (int i = 0; i <= la; i++) dp[i][0] = i;
        for (int j = 0; j <= lb; j++) dp[0][j] = j;
        for (int i = 1; i <= la; i++) {
            for (int j = 1; j <= lb; j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1))
                    dp[i][j] = dp[i - 1][j - 1];
                else
                    dp[i][j] = 1 + Math.min(dp[i - 1][j - 1],
                                   Math.min(dp[i - 1][j], dp[i][j - 1]));
            }
        }
        return dp[la][lb];
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SAVE TAGS
    // ═════════════════════════════════════════════════════════════════════════

    private void saveTags(Article article, List<ExtractedArticle.TagEntry> tagEntries) {
        // Один запрос для всех тегов вместо N отдельных SELECT-ов
        List<String> tagNames = tagEntries.stream()
                .map(t -> t.getName().toLowerCase().trim())
                .filter(n -> !n.isEmpty())
                .collect(Collectors.toList());

        Map<String, Tag> tagCache = new HashMap<>(tagDAO.findByNames(tagNames));

        for (ExtractedArticle.TagEntry tagEntry : tagEntries) {
            try {
                String tagName = tagEntry.getName().toLowerCase().trim();
                if (tagName.isEmpty()) continue;
                if (tagEntry.getPercent() <= 0) continue; // страховка после normalizePercents

                Tag tag = tagCache.get(tagName);
                if (tag == null) {
                    tag = tagDAO.save(new Tag(tagName));
                    tagCache.put(tagName, tag);
                    System.out.println("  Создан новый тег: " + tagName);
                }

                ArticleTagId id = new ArticleTagId();
                id.setArticleId(article.getId());
                id.setTagId(tag.getId());

                ArticleTagPercent atp = new ArticleTagPercent();
                atp.setId(id);
                atp.setArticle(article);
                atp.setTag(tag);
                atp.setPercent(tagEntry.getPercent());

                articleTagPercentDAO.save(atp);
                System.out.println("  Тег: " + tagName + " " + tagEntry.getPercent() + "%");

            } catch (Exception e) {
                System.err.println("Ошибка сохранения тега [" + tagEntry.getName() + "]: " + e.getMessage());
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Нормализует language, region и publication через статические карты.
     * Language и region получают дефолт если null/пустое/неизвестное.
     */
    private void normalizeMetadata(ExtractedArticle extracted) {
        // Language — с fallback на "English"
        extracted.setLanguage(normalizeLanguage(extracted.getLanguage()));

        // Region — с fallback на "International"
        extracted.setRegion(normalizeRegion(extracted.getRegion()));

        // Publication — strip known suffixes first, then map
        if (extracted.getPublication() != null) {
            String pub = extracted.getPublication().trim();
            pub = pub.replaceAll("(?i)\\s+(MD|Moldova|English|International|News English)$", "").trim();
            pub = pub.replaceAll("(?i)\\.md$", "").trim();
            String mapped = PUBLISHER_MAP.get(pub.toLowerCase());
            extracted.setPublication(mapped != null ? mapped : pub);
        }
    }

    /** Нормализует язык: ISO коды, варианты → полное название. Fallback: "English". */
    private String normalizeLanguage(String raw) {
        if (raw == null || raw.isBlank()) return "English";
        // Убираем пунктуацию и пробелы (en-US → enus), затем ищем точно
        String stripped = raw.trim().toLowerCase().replaceAll("[^a-z]", "");
        if (stripped.isEmpty()) return "English";

        String mapped = LANGUAGE_MAP.get(stripped);
        if (mapped != null) return mapped;

        // 2-символьный префикс (enus → en → English)
        if (stripped.length() >= 2) {
            mapped = LANGUAGE_MAP.get(stripped.substring(0, 2));
            if (mapped != null) return mapped;
        }

        // Fallback
        System.out.println("  Язык не распознан: \"" + raw + "\" — используем English");
        return "English";
    }

    /** Нормализует регион: аббревиатуры → полное название. Fallback: "International". */
    private String normalizeRegion(String raw) {
        if (raw == null || raw.isBlank()) return "International";
        String trimmed = raw.trim();
        String mapped = REGION_MAP.get(trimmed.toLowerCase());
        if (mapped != null) return mapped;
        // Длиннее 2 символов — скорее всего уже полное название, капитализируем первую букву
        if (trimmed.length() > 2) {
            return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
        }
        // Сокращение не в карте — неизвестный регион
        System.out.println("  Регион не распознан: \"" + raw + "\" — используем International");
        return "International";
    }

    private Article buildArticle(ExtractedArticle extracted) {
        Article article = new Article();
        article.setTitle(truncate(extracted.getTitle(), 500));
        article.setLink(truncate(extracted.getUrl(), 2000));
        article.setArticleLanguage(truncate(extracted.getLanguage(), 50));
        article.setArticleRegion(truncate(extracted.getRegion(), 100));
        article.setPublisher(truncate(extracted.getPublication(), 255));
        article.setReadCount(0);
        article.setPublicationDate(new Date());
        return article;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // VALIDATION RESULT
    // ═════════════════════════════════════════════════════════════════════════

    public static class ValidationResult {
        private final boolean valid;
        private final String  reason;

        private ValidationResult(boolean valid, String reason) {
            this.valid  = valid;
            this.reason = reason;
        }

        public static ValidationResult ok()                { return new ValidationResult(true, null); }
        public static ValidationResult fail(String reason) { return new ValidationResult(false, reason); }

        public boolean isValid()   { return valid; }
        public String  getReason() { return reason; }
    }
}
