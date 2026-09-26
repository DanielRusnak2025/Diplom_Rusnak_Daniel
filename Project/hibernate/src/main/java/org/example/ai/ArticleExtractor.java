package org.example.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;
import okhttp3.*;

import java.util.List;

/**
 * Получает URL статьи, скачивает её текст, отправляет в локальную Ollama
 * и возвращает структурированные данные.
 */
public class ArticleExtractor {

    private final String apiUrl;
    private final String model;
    private final int    maxTextLength;

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final LanguageDetector LANG_DETECTOR = LanguageDetectorBuilder
            .fromLanguages(
                    Language.ENGLISH,    Language.GERMAN,     Language.RUSSIAN,
                    Language.FRENCH,     Language.SPANISH,    Language.ITALIAN,
                    Language.PORTUGUESE, Language.ROMANIAN,   Language.UKRAINIAN,
                    Language.ARABIC,     Language.HEBREW,     Language.CHINESE,
                    Language.JAPANESE
            )
            .build();

    private final OkHttpClient   httpClient;
    private final ObjectMapper   objectMapper;
    private final ArticleFetcher articleFetcher;
    private final LinkFilter     linkFilter;

    public ArticleExtractor() {
        this.apiUrl        = AiConfig.getOllamaUrl() + "/api/chat";
        this.model         = AiConfig.getOllamaModel();
        this.maxTextLength = AiConfig.getMaxTextLength();

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        this.objectMapper   = new ObjectMapper();
        this.articleFetcher = new ArticleFetcher();
        this.linkFilter     = new LinkFilter();
    }

    /**
     * Проверяет, доступна ли Ollama. Возвращает true если отвечает на /api/tags.
     */
    public boolean isOllamaAvailable() {
        try {
            Request request = new Request.Builder()
                    .url(AiConfig.getOllamaUrl() + "/api/tags")
                    .get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Основной метод — принимает URL и список уже загруженных тегов.
     * Возвращает null если статья недоступна или не является новостной.
     */
    public ExtractedArticle extract(String url, List<String> existingTagNames) {
        try {
            ArticleFetcher.FetchResult fetched = articleFetcher.fetchArticle(url);

            String articleText  = fetched != null ? fetched.text     : null;
            String canonicalUrl = fetched != null ? fetched.finalUrl : url;

            // Проверяем финальный URL после редиректа через тот же фильтр, что и для списка ссылок
            if (!linkFilter.isLikelyArticle(canonicalUrl)) {
                System.out.println("  URL после редиректа не прошёл фильтр: " + canonicalUrl);
                return null;
            }

            int textLen = (articleText != null) ? articleText.length() : 0;
            if (textLen < 200) {
                System.out.println("  слишком короткий контент: " + textLen + " символов — " + canonicalUrl);
                return null;
            }

            String prompt = buildPrompt(canonicalUrl, articleText, existingTagNames);
            String rawResponse = callAI(prompt);
            ExtractedArticle extracted = parseResponse(rawResponse);

            if (extracted != null) {
                extracted.setUrl(canonicalUrl);
                if (extracted.getTitle() != null) {
                    extracted.setTitle(translateTitleIfNeeded(extracted.getTitle()));
                }
            }

            return extracted;

        } catch (Exception e) {
            System.err.println("Ошибка AI-извлечения для URL [" + url + "]: " + e.getMessage());
            return null;
        }
    }

    private String translateTitleIfNeeded(String title) {
        if (title == null || title.isBlank()) return title;

        Language detected = LANG_DETECTOR.detectLanguageOf(title);
        if (detected == Language.ENGLISH) return title;

        System.out.println("  Заголовок не на английском (" + detected + "): " + title + " — переводим");

        try {
            String prompt = "Translate the following news headline to English. "
                    + "Return ONLY the translated headline, no explanation:\n\n" + title;
            String rawResponse = callAI(prompt);
            JsonNode root = objectMapper.readTree(rawResponse);
            String translated = cleanTranslation(root.path("message").path("content").asText().trim());

            if (translated.isBlank()) {
                System.out.println("  Перевод пустой — оставляем оригинал");
                return title;
            }

            System.out.println("  Переведён: \"" + title + "\" → \"" + translated + "\"");
            return translated;

        } catch (Exception e) {
            System.err.println("  Ошибка перевода заголовка: " + e.getMessage());
            return title;
        }
    }

    private static String cleanTranslation(String s) {
        if (s == null || s.isBlank()) return s;
        // Strip "Here is the translation:" style prefix
        int colon = s.indexOf(':');
        if (colon > 0 && colon < 25) {
            String before = s.substring(0, colon).toLowerCase();
            if (before.contains("translation") || before.contains("headline")) {
                s = s.substring(colon + 1).trim();
            }
        }
        // Strip leading/trailing quote characters (straight and curly) by char code
        int start = 0, end = s.length();
        while (start < end && isQuoteChar(s.charAt(start)))  start++;
        while (end > start && isQuoteChar(s.charAt(end - 1))) end--;
        return s.substring(start, end).trim();
    }

    private static boolean isQuoteChar(char c) {
        return c == 0x0022 // "  straight double quote
            || c == 0x0027 // '  straight single quote
            || c == 0x201C // left  double curly quote
            || c == 0x201D // right double curly quote
            || c == 0x2018 // left  single curly quote
            || c == 0x2019;// right single curly quote
    }

    private String buildPrompt(String url, String articleText, List<String> existingTags) {
        String textSection = (articleText != null && !articleText.isBlank())
                ? articleText.length() > maxTextLength
                        ? articleText.substring(0, maxTextLength)
                        : articleText
                : "(article text unavailable — determine metadata from URL only)";

        int    dbTagCount     = existingTags.size();
        String existingTagsStr = existingTags.isEmpty()
                ? "(none yet)"
                : String.join(" | ", existingTags);

        String newTagRule = dbTagCount < 20
                ? "DB has few tags (cold start) — you may create up to 10 new tags to populate it."
                : "Maximum 2 new tags per article. Zero new tags is perfectly fine and preferred.";

        return """
                You are a news article data extraction assistant.
                Return ONLY a valid JSON object. No markdown, no code blocks, no explanations.

                REJECT RULE: If the content is NOT a real news article (error page, paywall, "JavaScript disabled",
                "Access denied", "403", "404", "Page not found", login page, advertisement) — return exactly:
                {"title": null, "url": null, "language": null, "region": null, "publication": null, "tags": []}

                LANGUAGE RULE: ALL metadata fields MUST be in English only.
                Translate title, region, publication, and tag names to English. Never use German, Hebrew, Russian, Arabic, etc.

                JSON fields:
                - title: headline translated to English. Must be a real article headline (not an error message).
                - url: the exact URL below — do NOT modify.
                - language: original article language as a FULL name. FORBIDDEN values: "en", "de", "ru", "he", "ar", "fr", "es", "ro", "uk", "it" or any other ISO code.
                  ALLOWED values ONLY: "English" | "German" | "Russian" | "Hebrew" | "Arabic" | "French" | "Spanish" | "Romanian" | "Ukrainian" | "Italian" | "Portuguese" | "Chinese" | "Japanese"
                  If uncertain, default to "English".
                - region: primary geographic region or country as a FULL name. FORBIDDEN abbreviations: "US", "USA", "UK", "EU", "GB", "DE", "RU", "IL", "UA".
                  REQUIRED: "United States" not "US" or "USA", "United Kingdom" not "UK", "Europe" not "EU", "Russia" not "RU", "Germany" not "DE".
                  Examples of correct values: "United States", "United Kingdom", "Europe", "Middle East", "Russia", "Israel", "Ukraine", "Germany", "France"
                - publication: canonical news outlet name. FORBIDDEN: country/language suffixes like " MD", ".md", " Moldova", " English", " News English", " International".
                  WRONG: "Newsmaker MD", "Newsmaker.md", "BBC News English", "Reuters International". RIGHT: "Newsmaker", "BBC", "Reuters", "Der Spiegel", "Haaretz"
                - tags: array of UP TO 10 tags (see rules below)

                ═══ TAG RULES — MANDATORY ═══

                EXISTING TAGS IN DATABASE — count: %d tag(s). You MUST reuse these whenever possible:
                %s

                STRICT REUSE RULES:
                1. Scan ALL existing tags before creating any new one.
                2. If an existing tag semantically covers the concept — USE IT, even if wording differs.
                   Examples: "Putin" → use "Vladimir Putin" | "US" → use "United States" | "Trump" → use "Donald Trump"
                3. You are FORBIDDEN from creating a new tag if any existing tag covers the concept even partially.
                4. Only create a NEW tag if absolutely NO existing tag covers the concept.

                NEW TAG RULES (only when truly necessary):
                - Single concept only. WRONG: "Russian Economic Crisis". RIGHT: "Economy" or "Russia".
                - Full names for people. WRONG: "Trump". RIGHT: "Donald Trump".
                - English only.
                - %s

                TAG FORMAT:
                - MINIMUM 5 tags, MAXIMUM 10 tags total. You MUST provide at least 5 tags.
                - Each tag: {"name": "Tag Name", "percent": NUMBER}
                - The sum of ALL percent values MUST equal exactly 100.
                - Example: [{"name": "Donald Trump", "percent": 40}, {"name": "Ukraine", "percent": 35}, {"name": "War", "percent": 25}]

                ══════════════════════════════

                Article URL: %s

                Article text:
                %s

                Return ONLY the JSON object.
                """.formatted(dbTagCount, existingTagsStr, newTagRule, url, textSection);
    }

    private String callAI(String prompt) throws Exception {
        String requestBody = """
                {
                    "model": "%s",
                    "messages": [
                        {
                            "role": "user",
                            "content": %s
                        }
                    ],
                    "stream": false
                }
                """.formatted(model, objectMapper.writeValueAsString(prompt));

        Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody, JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Ollama вернул ошибку: "
                        + response.code() + " " + response.body().string());
            }
            return response.body().string();
        }
    }

    private ExtractedArticle parseResponse(String rawResponse) throws Exception {
        JsonNode root = objectMapper.readTree(rawResponse);

        String content = root
                .path("message")
                .path("content")
                .asText();

        // Llama иногда оборачивает ответ в ```json ... ``` — чистим
        content = content
                .replaceAll("(?s)```json\\s*", "")
                .replaceAll("(?s)```\\s*", "")
                .trim();

        // Если перед/после JSON есть текст — вырезаем только объект { ... }
        if (!content.startsWith("{")) {
            int start = content.indexOf('{');
            int end   = content.lastIndexOf('}');
            if (start >= 0 && end > start) {
                content = content.substring(start, end + 1);
            }
        }

        System.out.println("Ответ Llama: " + content);
        return objectMapper.readValue(content, ExtractedArticle.class);
    }
}
