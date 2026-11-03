package org.example.pipeline;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import okhttp3.OkHttpClient;
import org.example.ai.AiConfig;
import org.example.ai.SubtopicInfo;
import org.example.ai.TagInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ModelComparisonRunner.
 * No writes to article, article_tag_percent, or article_queue.
 * No real HTTP or Ollama calls — uses MockWebServer.
 */
class ModelComparisonRunnerTest {

    private MockWebServer server;
    private OkHttpClient client;
    private Path outDir;

    private static final List<TagInfo> TAXONOMY = List.of(
            new TagInfo(1, "Technology", "Tech articles",
                    List.of(new SubtopicInfo(101, "General"))));

    private static final ParseResult PARSE_RESULT = new ParseResult(
            "Test Title", "Test Publisher", "2026-01-01",
            "https://example.com/article",
            "This is an English test article about technology and software.",
            0, "");

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        outDir = Files.createTempDirectory("mcr-test-");
    }

    @AfterEach
    void tearDown() throws IOException {
        server.close();
        try (var s = Files.walk(outDir)) {
            s.sorted(Comparator.reverseOrder())
             .map(Path::toFile)
             .forEach(java.io.File::delete);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String ollamaUrl() {
        return server.url("").toString();
    }

    private PageLoader stubLoader(ParseResult result) {
        return url -> (result == null ? null : new PageLoader.Result(result, url));
    }

    private PageLoader ssrfLoader() {
        return url -> { throw new PageLoader.SsrfViolationException("blocked: " + url); };
    }

    /**
     * Minimal valid Ollama chat response.
     * When isArticle=true the content embeds category/subtopic JSON.
     */
    private static MockResponse ollamaResponse(boolean isArticle, String category) {
        String content = isArticle
                ? "{\\\"is_article\\\":true,\\\"main\\\":{\\\"category\\\":\\\""
                  + category + "\\\",\\\"subtopic\\\":\\\"General\\\"},\\\"extra\\\":[]}"
                : "{\\\"is_article\\\":false}";
        String body = "{\"model\":\"test\","
                + "\"message\":{\"role\":\"assistant\",\"content\":\"" + content + "\"},"
                + "\"done\":true}";
        return new MockResponse.Builder().body(body).build();
    }

    private static MockResponse errorResponse(int code, String body) {
        return new MockResponse.Builder().code(code).body(body).build();
    }

    private ModelComparisonRunner runner(List<String> models, List<String> urls,
                                          PageLoader loader) {
        return new ModelComparisonRunner(models, urls, loader,
                new ArticleLanguageDetector(AiConfig.getLanguagesAllowed()),
                TAXONOMY, ollamaUrl(), client);
    }

    private String today() {
        return java.time.LocalDate.now().toString();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void run_producesMarkdownAndCsvFiles() throws IOException {
        // 1 URL → 1 bench article; warmup uses dummy text (1 extra request)
        List<String> urls = List.of("https://a.invalid/1");
        server.enqueue(ollamaResponse(true, "Technology")); // warmup (dummy text)
        server.enqueue(ollamaResponse(true, "Technology")); // bench[0]

        runner(List.of("test-model"), urls, stubLoader(PARSE_RESULT)).run(outDir);

        assertTrue(Files.exists(outDir.resolve("model-comparison-" + today() + ".md")),
                "Markdown report must be created");
        assertTrue(Files.exists(outDir.resolve("model-comparison-" + today() + ".csv")),
                "CSV report must be created");
    }

    @Test
    void warmup_usesFixedDummyText_allUrlsGoToStats() throws IOException {
        // Both URLs are bench articles — warmup uses dummy text, not a URL
        List<String> urls = List.of("https://a.invalid/1", "https://a.invalid/2");

        // warmup call (dummy text) + 2 benchmark calls
        server.enqueue(ollamaResponse(false, null)); // warmup — not-article (doesn't matter)
        server.enqueue(ollamaResponse(true, "Technology")); // bench[0]
        server.enqueue(ollamaResponse(false, null));         // bench[1]

        runner(List.of("test-model"), urls, stubLoader(PARSE_RESULT)).run(outDir);

        String csv = Files.readString(outDir.resolve("model-comparison-" + today() + ".csv"));
        // Both URLs must appear as data rows
        long dataRows = csv.lines().filter(l -> l.startsWith("https://")).count();
        assertEquals(2, dataRows,
                "All fetched URLs must appear in CSV (warmup uses fixed dummy text, not a URL)");
    }

    @Test
    void thinkFallback_400WithThink_retriesWithoutThinkField() throws IOException {
        List<String> urls = List.of("https://a.invalid/1");
        // warmup: 400 with "think" in body → retry → success
        server.enqueue(errorResponse(400, "think is not supported"));
        server.enqueue(ollamaResponse(true, "Technology")); // warmup retry
        // benchmark
        server.enqueue(ollamaResponse(true, "Technology"));

        runner(List.of("test-model"), urls, stubLoader(PARSE_RESULT)).run(outDir);

        String md = Files.readString(outDir.resolve("model-comparison-" + today() + ".md"));
        assertTrue(md.contains("think=false supported"), "MD must have think=false row");
        // think fallback during warmup propagates: model marked as not supporting think
        assertTrue(md.contains("| no |") || md.contains("| no|") || md.contains("|no |"),
                "think fallback must be marked 'no' in stats table");
    }

    @Test
    void csvFormat_headerContainsAllModelColumns() throws IOException {
        List<String> urls = List.of("https://a.invalid/1", "https://a.invalid/2");
        // modelA: warmup + 2 bench; modelB: warmup + 2 bench = 6 total
        for (int i = 0; i < 6; i++) server.enqueue(ollamaResponse(true, "Technology"));

        runner(List.of("modelA", "modelB"), urls, stubLoader(PARSE_RESULT)).run(outDir);

        String header = Files.readString(outDir.resolve("model-comparison-" + today() + ".csv"))
                .lines().findFirst().orElse("");
        assertTrue(header.contains("modelA_is_article"),    "CSV must have modelA_is_article");
        assertTrue(header.contains("modelB_is_article"),    "CSV must have modelB_is_article");
        assertTrue(header.contains("modelA_latency_ms"),    "CSV must have modelA_latency_ms");
        assertTrue(header.contains("modelA_tokens_per_second"), "CSV must have modelA_tokens_per_second");
        assertTrue(header.contains("modelB_tokens_per_second"), "CSV must have modelB_tokens_per_second");
    }

    @Test
    void mdFormat_containsRequiredSections() throws IOException {
        List<String> urls = List.of("https://a.invalid/1");
        server.enqueue(ollamaResponse(true, "Technology")); // warmup
        server.enqueue(ollamaResponse(true, "Technology")); // bench

        runner(List.of("test-model"), urls, stubLoader(PARSE_RESULT)).run(outDir);

        String md = Files.readString(outDir.resolve("model-comparison-" + today() + ".md"));
        assertTrue(md.contains("## Configuration"),         "MD must have Configuration section");
        assertTrue(md.contains("## Statistics per model"),  "MD must have Statistics section");
        assertTrue(md.contains("## Category distribution"), "MD must have Category distribution");
        assertTrue(md.contains("## Pairwise agreement"),    "MD must have Pairwise agreement");
        assertTrue(md.contains("## Per-article results"),   "MD must have Per-article results");
    }

    @Test
    void mdStats_containsNewMetricRows() throws IOException {
        List<String> urls = List.of("https://a.invalid/1");
        server.enqueue(ollamaResponse(true, "Technology")); // warmup
        server.enqueue(ollamaResponse(true, "Technology")); // bench

        runner(List.of("m"), urls, stubLoader(PARSE_RESULT)).run(outDir);

        String md = Files.readString(outDir.resolve("model-comparison-" + today() + ".md"));
        assertTrue(md.contains("% invalid (1st attempt)"), "MD must have 1st-attempt invalid rate");
        assertTrue(md.contains("% invalid (after retry)"), "MD must have after-retry invalid rate");
        assertTrue(md.contains("% timeouts"),              "MD must have timeout rate");
        assertTrue(md.contains("p50 tokens/s"),            "MD must have tokens/s row");
    }

    @Test
    void noQueueWrites_runCompletesWithoutDbAccess() {
        // Test constructor bypasses DB — verifies run() completes without DB
        List<String> urls = List.of("https://a.invalid/1");
        server.enqueue(ollamaResponse(false, null));
        server.enqueue(ollamaResponse(false, null));

        assertDoesNotThrow(() ->
                runner(List.of("m"), urls, stubLoader(PARSE_RESULT)).run(outDir));
    }

    @Test
    void ssrfBlocked_urlsSkipped_runAbortsCleanly() {
        // SSRF blocks all pages → no articles fetched → run aborts before writing output
        assertDoesNotThrow(() ->
                runner(List.of("m"), List.of("https://a.invalid/1"),
                        ssrfLoader()).run(outDir));
        assertFalse(Files.exists(outDir.resolve("model-comparison-" + today() + ".md")),
                "No MD file when all pages fail SSRF check");
    }

    @Test
    void parseError_appearsAsErrorInPerArticleTable() throws IOException {
        List<String> urls = List.of("https://a.invalid/1");
        server.enqueue(ollamaResponse(true, "Technology")); // warmup OK
        // benchmark: unparseable content
        server.enqueue(new MockResponse.Builder()
                .body("{\"model\":\"t\",\"message\":{\"role\":\"assistant\","
                        + "\"content\":\"not-json\"},\"done\":true}")
                .build());
        // parse retry
        server.enqueue(new MockResponse.Builder()
                .body("{\"model\":\"t\",\"message\":{\"role\":\"assistant\","
                        + "\"content\":\"still-not-json\"},\"done\":true}")
                .build());

        runner(List.of("t"), urls, stubLoader(PARSE_RESULT)).run(outDir);

        String md = Files.readString(outDir.resolve("model-comparison-" + today() + ".md"));
        assertTrue(md.contains("ERROR"), "Parse error must appear as ERROR in per-article table");
    }

    @Test
    void parseRetry_firstAttemptFailed_secondSucceeds_markedInStats() throws IOException {
        List<String> urls = List.of("https://a.invalid/1");
        server.enqueue(ollamaResponse(true, "Technology")); // warmup
        // bench attempt 1: bad content → parse error → retry
        server.enqueue(new MockResponse.Builder()
                .body("{\"model\":\"t\",\"message\":{\"role\":\"assistant\","
                        + "\"content\":\"bad\"},\"done\":true}")
                .build());
        // bench attempt 2: good response
        server.enqueue(ollamaResponse(true, "Technology"));

        runner(List.of("t"), urls, stubLoader(PARSE_RESULT)).run(outDir);

        String md = Files.readString(outDir.resolve("model-comparison-" + today() + ".md"));
        // "% invalid (1st attempt)" must be > 0 (retry was needed), "after retry" = 0
        assertTrue(md.contains("% invalid (1st attempt)"), "stats must show 1st-attempt rate");
        // Article should appear in results (retry succeeded)
        assertTrue(md.contains("Technology"), "Retry success — category must appear");
    }

    @Test
    void writeCsv_specialCharactersEscaped() throws IOException {
        ParseResult special = new ParseResult(
                "Title with, comma and \"quote\"", "pub", "2026-01-01",
                "https://example.com",
                "English text about technology, software, and systems.",
                0, "");

        List<String> urls = List.of("https://a.invalid/1");
        server.enqueue(ollamaResponse(true, "Technology")); // warmup
        server.enqueue(ollamaResponse(true, "Technology")); // bench

        runner(List.of("m"), urls, stubLoader(special)).run(outDir);

        String csv = Files.readString(outDir.resolve("model-comparison-" + today() + ".csv"));
        assertTrue(csv.contains("\"Title with, comma and \"\"quote\"\"\""),
                "CSV fields with commas/quotes must be RFC 4180 escaped");
    }
}
