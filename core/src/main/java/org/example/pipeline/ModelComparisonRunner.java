package org.example.pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.example.ai.AiConfig;
import org.example.ai.ArticleClassification;
import org.example.ai.OllamaClassifier;
import org.example.ai.SubtopicInfo;
import org.example.ai.TagInfo;
import org.example.util.HibernateUtil;
import org.hibernate.Session;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Runs a multi-model comparison benchmark.
 *
 * For each URL in article_queue (read-only — status, article_id and all queue fields unchanged):
 *   1. Fetch + parse the page once (shared across models).
 *   2. Per model: one warmup call with a fixed dummy text (excluded from stats),
 *      then one measured call per article.
 * Output: docs/benchmarks/model-comparison-<date>.md + .csv
 */
public class ModelComparisonRunner {

    private static final Logger log = Logger.getLogger(ModelComparisonRunner.class.getName());

    /** Fixed text used for warmup — not from the queue, not counted in statistics. */
    private static final String WARMUP_TEXT =
            "This is a warmup classification request to initialise the model. " +
            "The quick brown fox jumps over the lazy dog. " +
            "Technology and science advance human knowledge in meaningful ways.";

    // ── Internal data types ───────────────────────────────────────────────────

    record ArticleInput(String url, String title, String domain, String lang, String text,
                        int textChars, String extractSource, String skipReason, String ogType) {}

    /**
     * Result of a single model call.
     *
     * @param parseError              final state is a failure (after all retries)
     * @param firstAttemptParseFailed first parse attempt threw IOException (retry was triggered)
     * @param thinkFallback           model does not support the think field (retried without it)
     * @param timeout                 at least one HTTP call timed out during this result
     * @param tokensPerSecond         eval_count / eval_duration from Ollama response, or null
     */
    record CallResult(long latencyMs, boolean isArticle, String mainCategory, String mainSubtopic,
                      boolean parseError, boolean firstAttemptParseFailed,
                      boolean thinkFallback, boolean timeout, Double tokensPerSecond,
                      boolean preFiltered) {}

    /** Per-model benchmark result. measurements.get(i) corresponds to benchArticles.get(i). */
    record ModelRunResult(String model, boolean thinkNotSupported, List<CallResult> measurements,
                          int subtopicMismatches) {}

    private record HttpResult(int code, String body, boolean timedOut) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private final List<String> models;
    private final int limit;          // 0 = no limit (load all PENDING URLs)
    private final List<String> testUrls;
    private final PageLoader pageLoader;
    private final ArticleLanguageDetector langDetector;
    private final List<TagInfo> taxonomy;
    private final String ollamaUrl;
    private final OkHttpClient httpClient;
    private final boolean skipMetadata; // true in test constructor (skips /api/version + /api/tags)
    private final java.util.Set<String> newsletterHosts;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Production constructor — reads URLs from article_queue, loads taxonomy from DB. */
    public ModelComparisonRunner(List<String> models, int limit) {
        this(models, limit, null,
                new PageFetcherLoader(),
                new ArticleLanguageDetector(AiConfig.getLanguagesAllowed()),
                loadTaxonomy(),
                AiConfig.getOllamaUrl(),
                new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(AiConfig.getBenchmarkTimeoutSeconds(), TimeUnit.SECONDS)
                        .build(),
                false,
                AiConfig.getNewsletterHosts());
    }

    /** Test constructor — injectable components, no DB access, no metadata fetches. */
    ModelComparisonRunner(List<String> models, List<String> testUrls,
                          PageLoader pageLoader, ArticleLanguageDetector langDetector,
                          List<TagInfo> taxonomy, String ollamaUrl, OkHttpClient httpClient) {
        this(models, testUrls.size(), testUrls, pageLoader, langDetector,
                taxonomy, ollamaUrl, httpClient, true, java.util.Set.of());
    }

    private ModelComparisonRunner(List<String> models, int limit, List<String> testUrls,
                                   PageLoader pageLoader, ArticleLanguageDetector langDetector,
                                   List<TagInfo> taxonomy, String ollamaUrl,
                                   OkHttpClient httpClient, boolean skipMetadata,
                                   java.util.Set<String> newsletterHosts) {
        this.models           = models;
        this.limit            = limit;
        this.testUrls         = testUrls;
        this.pageLoader       = pageLoader;
        this.langDetector     = langDetector;
        this.taxonomy         = taxonomy;
        this.ollamaUrl        = ollamaUrl;
        this.httpClient       = httpClient;
        this.skipMetadata     = skipMetadata;
        this.newsletterHosts  = newsletterHosts;
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public void run(Path outputDir) throws IOException {
        List<String> rawUrls = testUrls != null ? testUrls : loadUrlsFromQueue();

        if (rawUrls.isEmpty()) {
            log.warning("[compare-models] No PENDING URLs available. Aborting.");
            return;
        }

        // Deduplicate by normalized input URL before fetching
        List<String> urls = deduplicateByInputUrl(rawUrls);
        int inputDeduped = rawUrls.size() - urls.size();
        if (inputDeduped > 0)
            log.info("[compare-models] Deduped " + inputDeduped + " duplicate input URL(s)");
        log.info("[compare-models] URLs to benchmark: " + urls.size());

        // Fetch + parse each page once (all go into stats — warmup uses dummy text)
        List<ArticleInput> benchArticles = fetchPages(urls);
        if (benchArticles.isEmpty()) {
            log.warning("[compare-models] No articles could be fetched. Aborting.");
            return;
        }
        int fetchFailed = urls.size() - benchArticles.size();
        log.info("[compare-models] Fetched: " + benchArticles.size() + " / " + urls.size()
                + (fetchFailed > 0 ? " (" + fetchFailed + " failed)" : ""));

        // Ollama metadata for the config section
        String ollamaVersion      = skipMetadata ? "n/a" : fetchOllamaVersion();
        Map<String, String> modelSizes = skipMetadata ? Map.of() : fetchModelSizes();

        // Run each model
        Map<String, ModelRunResult> results = new LinkedHashMap<>();
        for (String model : models) {
            log.info("[compare-models] Benchmarking: " + model);
            OllamaClassifier classifier = OllamaClassifier.forModel(model, baseUrl(), httpClient);

            // Warmup with fixed dummy text — not counted in statistics
            CallResult warmup = callModel(classifier, WARMUP_TEXT);
            log.info("[compare-models]   warmup done (" + warmup.latencyMs() + "ms"
                    + (warmup.thinkFallback() ? ", think field NOT supported" : "") + ")");

            boolean thinkNotSupported = warmup.thinkFallback();
            List<CallResult> measurements = new ArrayList<>(benchArticles.size());
            for (int i = 0; i < benchArticles.size(); i++) {
                ArticleInput a = benchArticles.get(i);
                CallResult r;
                if (a.skipReason() != null) {
                    r = new CallResult(0, false, null, null, false, false, false, false, null, true);
                    log.info("[compare-models]   [" + (i + 1) + "/" + benchArticles.size() + "] "
                            + model + " SKIP(" + a.skipReason() + ")");
                } else {
                    r = callModel(classifier, a.text());
                    log.info("[compare-models]   [" + (i + 1) + "/" + benchArticles.size() + "] "
                            + model + " " + r.latencyMs() + "ms"
                            + (r.parseError() ? " PARSE_ERROR" : "")
                            + (r.timeout() ? " TIMEOUT" : "")
                            + (r.firstAttemptParseFailed() && !r.parseError() ? " RETRIED_OK" : "")
                            + (!r.parseError() && r.isArticle() ? " article=" + r.mainCategory() : "")
                            + (!r.parseError() && !r.isArticle() ? " not-article" : "")
                            + (r.tokensPerSecond() != null
                                    ? String.format(" %.1f tok/s", r.tokensPerSecond()) : "")
                            + (r.thinkFallback() ? " THINK_FALLBACK" : ""));
                }
                if (r.thinkFallback()) thinkNotSupported = true;
                measurements.add(r);
            }
            results.put(model, new ModelRunResult(model, thinkNotSupported, measurements,
                    classifier.subtopicMismatchCount()));
        }

        // Write output
        Files.createDirectories(outputDir);
        String date = LocalDate.now().toString();
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmm"));
        String stem = "model-comparison-" + date + "-" + time + "-" + OllamaClassifier.PROMPT_VERSION;
        Path csvPath = outputDir.resolve(stem + ".csv");
        Path mdPath  = outputDir.resolve(stem + ".md");
        writeCsv(benchArticles, results, csvPath);
        writeMd(benchArticles, results, mdPath, urls.size(), fetchFailed, ollamaVersion, modelSizes);
        log.info("[compare-models] Report: " + mdPath);
        log.info("[compare-models] CSV:    " + csvPath);
    }

    // ── Ollama call ───────────────────────────────────────────────────────────

    private CallResult callModel(OllamaClassifier classifier, String text) {
        String url = baseUrl() + "/api/chat";
        boolean thinkFallback = false;

        // Build request with think=false
        String reqBody = classifier.buildRequestBody(text, taxonomy, true);

        // First HTTP call
        long start = System.currentTimeMillis();
        HttpResult resp = httpPost(url, reqBody);

        // think-fallback: 400 with "think" in body → retry without think field, restart timer
        if (resp.code() == 400 && resp.body().toLowerCase().contains("think")) {
            reqBody = classifier.buildRequestBody(text, taxonomy, false);
            start = System.currentTimeMillis();
            resp = httpPost(url, reqBody);
            thinkFallback = true;
        }

        long latency = System.currentTimeMillis() - start;

        if (resp.code() < 0) {
            return new CallResult(latency, false, null, null, true, false,
                    thinkFallback, resp.timedOut(), null, false);
        }
        if (resp.code() < 200 || resp.code() >= 300) {
            return new CallResult(latency, false, null, null, true, false,
                    thinkFallback, false, null, false);
        }

        Double tps = parseTokensPerSecond(resp.body());

        // Try to parse response; on failure retry once (same policy as OllamaClassifier)
        try {
            ArticleClassification ac = classifier.parseResponse(resp.body(), taxonomy);
            return buildCallResult(latency, ac, thinkFallback, false, false, tps);
        } catch (IOException e) {
            log.fine("[compare-models] parse error (attempt 1): " + e.getMessage() + "; retrying");

            long retryStart = System.currentTimeMillis();
            HttpResult retryResp = httpPost(url, reqBody);
            latency += System.currentTimeMillis() - retryStart;

            if (retryResp.code() < 0) {
                return new CallResult(latency, false, null, null, true, true,
                        thinkFallback, retryResp.timedOut(), null, false);
            }
            if (retryResp.code() < 200 || retryResp.code() >= 300) {
                return new CallResult(latency, false, null, null, true, true,
                        thinkFallback, false, null, false);
            }

            Double retryTps = parseTokensPerSecond(retryResp.body());
            try {
                ArticleClassification ac = classifier.parseResponse(retryResp.body(), taxonomy);
                return buildCallResult(latency, ac, thinkFallback, true, false, retryTps);
            } catch (IOException e2) {
                log.warning("[compare-models] parse error (attempt 2): " + e2.getMessage());
                return new CallResult(latency, false, null, null, true, true,
                        thinkFallback, false, null, false);
            }
        }
    }

    private CallResult buildCallResult(long latencyMs, ArticleClassification ac,
                                       boolean thinkFallback, boolean firstAttemptParseFailed,
                                       boolean timeout, Double tps) {
        String cat = null, sub = null;
        if (ac.isArticle()) {
            cat = taxonomy.stream()
                    .filter(t -> t.id() == ac.mainCategoryId())
                    .map(TagInfo::name)
                    .findFirst().orElse("id:" + ac.mainCategoryId());
            if (ac.mainSubtopicId() != null) {
                sub = taxonomy.stream()
                        .flatMap(t -> t.subtopics().stream())
                        .filter(s -> s.id() == ac.mainSubtopicId())
                        .map(SubtopicInfo::name)
                        .findFirst().orElse("id:" + ac.mainSubtopicId());
            }
        }
        return new CallResult(latencyMs, ac.isArticle(), cat, sub, false,
                firstAttemptParseFailed, thinkFallback, timeout, tps, false);
    }

    private Double parseTokensPerSecond(String responseJson) {
        try {
            JsonNode root = mapper.readTree(responseJson);
            JsonNode evalCount    = root.path("eval_count");
            JsonNode evalDuration = root.path("eval_duration"); // nanoseconds
            if (!evalCount.isMissingNode() && !evalDuration.isMissingNode()) {
                long count = evalCount.asLong();
                long durNs = evalDuration.asLong();
                if (count > 0 && durNs > 0) return count * 1_000_000_000.0 / durNs;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private HttpResult httpPost(String url, String jsonBody) {
        Request req = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonBody.getBytes(StandardCharsets.UTF_8), JSON_TYPE))
                .build();
        try (Response resp = httpClient.newCall(req).execute()) {
            ResponseBody rb = resp.body();
            String body = rb != null ? rb.string() : "";
            return new HttpResult(resp.code(), body, false);
        } catch (SocketTimeoutException e) {
            log.warning("[compare-models] timeout: " + e.getMessage());
            return new HttpResult(-1, "", true);
        } catch (IOException e) {
            log.warning("[compare-models] HTTP error: " + e.getMessage());
            return new HttpResult(-1, "", false);
        }
    }

    // ── Ollama metadata ───────────────────────────────────────────────────────

    private String fetchOllamaVersion() {
        try {
            Request req = new Request.Builder().url(baseUrl() + "/api/version").get().build();
            try (Response resp = httpClient.newCall(req).execute()) {
                if (resp.isSuccessful() && resp.body() != null) {
                    return mapper.readTree(resp.body().string()).path("version").asText("unknown");
                }
            }
        } catch (Exception e) {
            log.warning("[compare-models] fetchOllamaVersion failed: " + e.getMessage());
        }
        return "unknown";
    }

    private Map<String, String> fetchModelSizes() {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            Request req = new Request.Builder().url(baseUrl() + "/api/tags").get().build();
            try (Response resp = httpClient.newCall(req).execute()) {
                if (resp.isSuccessful() && resp.body() != null) {
                    JsonNode list = mapper.readTree(resp.body().string()).path("models");
                    if (list.isArray()) {
                        Map<String, String> exact   = new LinkedHashMap<>();
                        Map<String, String> fallback = new LinkedHashMap<>();
                        for (JsonNode m : list) {
                            String apiName = m.path("name").asText();
                            long size = m.path("size").asLong(0);
                            for (String model : models) {
                                if (apiName.equals(model)) {
                                    exact.put(model, formatBytes(size));
                                } else if (!model.contains(":") && apiName.equals(model + ":latest")) {
                                    fallback.put(model, formatBytes(size));
                                }
                            }
                        }
                        result.putAll(fallback);
                        result.putAll(exact); // exact match wins over :latest
                    }
                }
            }
        } catch (Exception e) {
            log.warning("[compare-models] fetchModelSizes failed: " + e.getMessage());
        }
        for (String m : models) result.putIfAbsent(m, "unknown");
        return result;
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) return "unknown";
        return String.format("%.1f GB", bytes / 1_000_000_000.0);
    }

    // ── Page fetching ─────────────────────────────────────────────────────────

    private List<ArticleInput> fetchPages(List<String> urls) {
        List<ArticleInput> result = new ArrayList<>();
        Set<String> seenFinalUrls = new HashSet<>();
        for (String url : urls) {
            try {
                PageLoader.Result res = pageLoader.load(url);
                if (res == null) { log.warning("[compare-models] fetch=null: " + url); continue; }
                String finalNorm = tryNormalize(res.finalUrl());
                if (!seenFinalUrls.add(finalNorm)) {
                    log.info("[compare-models] dedup by redirect: " + url + " → " + res.finalUrl());
                    continue;
                }
                String skipReason = ArticlePreFilter.check(res.parsed(), res.finalUrl(), res.httpStatus(), newsletterHosts);
                String lang   = langDetector.detect(res.parsed().textForAi());
                String domain = extractDomain(url);
                result.add(new ArticleInput(url, res.parsed().title(), domain, lang,
                        res.parsed().textForAi(),
                        res.parsed().textChars(), res.parsed().extractSource(), skipReason,
                        res.parsed().ogType()));
            } catch (PageLoader.SsrfViolationException e) {
                log.warning("[compare-models] SSRF blocked: " + url);
            }
        }
        return result;
    }

    // ── Output writers ────────────────────────────────────────────────────────

    void writeCsv(List<ArticleInput> articles, Map<String, ModelRunResult> results, Path out)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("url,domain,title,lang,text_chars,extract_source,skip_reason,og_type,gold_is_article,gold_category");
        for (String m : models) {
            String p = csvSafe(m);
            sb.append(',').append(p).append("_is_article");
            sb.append(',').append(p).append("_category");
            sb.append(',').append(p).append("_subtopic");
            sb.append(',').append(p).append("_latency_ms");
            sb.append(',').append(p).append("_think_fallback");
            sb.append(',').append(p).append("_tokens_per_second");
        }
        sb.append('\n');

        for (int i = 0; i < articles.size(); i++) {
            ArticleInput a = articles.get(i);
            sb.append(csvField(a.url())).append(',')
              .append(csvField(a.domain())).append(',')
              .append(csvField(a.title())).append(',')
              .append(csvField(a.lang())).append(',')
              .append(a.textChars()).append(',')
              .append(csvField(a.extractSource())).append(',')
              .append(csvField(a.skipReason())).append(',')
              .append(csvField(a.ogType())).append(",,"); // gold_is_article, gold_category (fill by hand)
            for (String m : models) {
                List<CallResult> meas = results.get(m).measurements();
                if (i < meas.size()) {
                    CallResult r = meas.get(i);
                    if (r.preFiltered()) {
                        sb.append(",,,,,,");
                    } else {
                        sb.append(',').append(r.isArticle())
                          .append(',').append(csvField(r.mainCategory()))
                          .append(',').append(csvField(r.mainSubtopic()))
                          .append(',').append(r.latencyMs())
                          .append(',').append(r.thinkFallback())
                          .append(',').append(r.tokensPerSecond() != null
                                  ? String.format(Locale.US, "%.2f", r.tokensPerSecond()) : "");
                    }
                } else {
                    sb.append(",,,,,,");
                }
            }
            sb.append('\n');
        }
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
    }

    void writeMd(List<ArticleInput> articles, Map<String, ModelRunResult> results, Path out,
                 int totalUrls, int fetchFailed,
                 String ollamaVersion, Map<String, String> modelSizes) throws IOException {
        StringBuilder sb = new StringBuilder();
        String date = LocalDate.now().toString();
        sb.append("# Model Comparison — ").append(date).append("\n\n");

        // ── Configuration ─────────────────────────────────────────────────────
        sb.append("## Configuration\n\n");
        sb.append("- **Ollama version:** ").append(ollamaVersion).append('\n');
        sb.append("- **Models:**\n\n");
        sb.append("  | Model | Size |\n  |---|---|\n");
        for (String m : models) {
            sb.append("  | ").append(m).append(" | ")
              .append(modelSizes.getOrDefault(m, "unknown")).append(" |\n");
        }
        sb.append('\n');
        long preFilteredCount = articles.stream().filter(a -> a.skipReason() != null).count();
        sb.append("- **Articles:** ").append(articles.size()).append(" fetched");
        if (fetchFailed > 0) sb.append(", ").append(fetchFailed).append(" failed to fetch");
        if (preFilteredCount > 0) sb.append(", ").append(preFilteredCount).append(" pre-filtered (no AI call)");
        sb.append(" (").append(totalUrls).append(" total URLs from queue)\n");
        sb.append("- **Warmup:** 1 call per model with fixed dummy text"
                + " (excluded from statistics)\n\n");

        // ── Statistics per model ──────────────────────────────────────────────
        sb.append("## Statistics per model\n\n");
        sb.append("| Metric |");
        for (String m : models) sb.append(' ').append(m).append(" |");
        sb.append("\n|---|");
        for (int i = 0; i < models.size(); i++) sb.append("---|");
        sb.append('\n');

        for (String row : List.of(
                "p50 latency (ms)", "p95 latency (ms)",
                "% invalid (1st attempt)", "% invalid (after retry)", "% timeouts",
                "% is_article=true", "% subtopic substituted", "p50 tokens/s",
                "think=false supported")) {
            sb.append("| ").append(row).append(" |");
            for (String m : models) {
                sb.append(' ').append(statCell(row, results.get(m))).append(" |");
            }
            sb.append('\n');
        }
        sb.append('\n');

        // ── Category distribution ─────────────────────────────────────────────
        sb.append("## Category distribution\n\n");
        Set<String> allCats = new LinkedHashSet<>();
        for (String m : models) {
            results.get(m).measurements().stream()
                    .filter(r -> !r.preFiltered() && !r.parseError() && r.isArticle() && r.mainCategory() != null)
                    .map(CallResult::mainCategory)
                    .forEach(allCats::add);
        }
        if (!allCats.isEmpty()) {
            sb.append("| Category |");
            for (String m : models) sb.append(' ').append(m).append(" |");
            sb.append("\n|---|");
            for (int i = 0; i < models.size(); i++) sb.append("---|");
            sb.append('\n');
            for (String cat : allCats) {
                sb.append("| ").append(cat).append(" |");
                for (String m : models) {
                    long n = results.get(m).measurements().stream()
                            .filter(r -> !r.preFiltered() && !r.parseError() && r.isArticle() && cat.equals(r.mainCategory()))
                            .count();
                    sb.append(' ').append(n).append(" |");
                }
                sb.append('\n');
            }
            sb.append('\n');
        }

        // ── Pairwise agreement ────────────────────────────────────────────────
        sb.append("## Pairwise agreement\n\n");
        if (models.size() >= 2) {
            sb.append("| Models | is_article match | main_category match |\n");
            sb.append("|---|---|---|\n");
            for (int i = 0; i < models.size(); i++) {
                for (int j = i + 1; j < models.size(); j++) {
                    String m1 = models.get(i), m2 = models.get(j);
                    String[] ag = pairwiseAgreement(results.get(m1), results.get(m2));
                    sb.append("| ").append(m1).append(" vs ").append(m2).append(" | ")
                      .append(ag[0]).append(" | ").append(ag[1]).append(" |\n");
                }
            }
            sb.append('\n');
        } else {
            sb.append("*(requires ≥ 2 models)*\n\n");
        }

        // ── Per-article results ───────────────────────────────────────────────
        sb.append("## Per-article results\n\n");
        sb.append("| Domain | Title | Lang | text_chars | extract_source | skip_reason | og_type |");
        for (String m : models) sb.append(' ').append(m).append(" |");
        sb.append("\n|---|---|---|---|---|---|---|");
        for (int i = 0; i < models.size(); i++) sb.append("---|");
        sb.append('\n');
        for (int i = 0; i < articles.size(); i++) {
            ArticleInput a = articles.get(i);
            sb.append("| ").append(mdCell(a.domain()))
              .append(" | ").append(mdCell(a.title()))
              .append(" | ").append(mdCell(a.lang()))
              .append(" | ").append(a.textChars())
              .append(" | ").append(mdCell(a.extractSource()))
              .append(" | ").append(mdCell(a.skipReason()))
              .append(" | ").append(mdCell(a.ogType())).append(" |");
            for (String m : models) {
                List<CallResult> meas = results.get(m).measurements();
                if (i < meas.size()) {
                    CallResult r = meas.get(i);
                    if (r.preFiltered()) {
                        sb.append(" SKIP |");
                    } else if (r.parseError()) {
                        sb.append(r.timeout() ? " TIMEOUT |" : " ERROR |");
                    } else if (!r.isArticle()) {
                        sb.append(" not-article |");
                    } else {
                        sb.append(' ').append(r.mainCategory())
                          .append('/').append(r.mainSubtopic()).append(" |");
                    }
                } else {
                    sb.append(" — |");
                }
            }
            sb.append('\n');
        }
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
    }

    // ── Statistics helpers ────────────────────────────────────────────────────

    private static List<CallResult> activeResults(ModelRunResult mr) {
        return mr.measurements().stream().filter(r -> !r.preFiltered()).toList();
    }

    private String statCell(String metric, ModelRunResult mr) {
        List<CallResult> m = activeResults(mr);
        switch (metric) {
            case "p50 latency (ms)" -> { return String.valueOf(latencyPercentile(m, 50)); }
            case "p95 latency (ms)" -> { return String.valueOf(latencyPercentile(m, 95)); }
            case "% invalid (1st attempt)" -> {
                if (m.isEmpty()) return "n/a";
                long bad = m.stream()
                        .filter(r -> r.firstAttemptParseFailed() || r.parseError())
                        .count();
                return String.format(Locale.US, "%.1f%%", bad * 100.0 / m.size());
            }
            case "% invalid (after retry)" -> {
                if (m.isEmpty()) return "n/a";
                long bad = m.stream().filter(CallResult::parseError).count();
                return String.format(Locale.US, "%.1f%%", bad * 100.0 / m.size());
            }
            case "% timeouts" -> {
                if (m.isEmpty()) return "n/a";
                long t = m.stream().filter(CallResult::timeout).count();
                return String.format(Locale.US, "%.1f%%", t * 100.0 / m.size());
            }
            case "% is_article=true" -> {
                if (m.isEmpty()) return "n/a";
                long art = m.stream().filter(r -> !r.parseError() && r.isArticle()).count();
                return String.format(Locale.US, "%.1f%%", art * 100.0 / m.size());
            }
            case "% subtopic substituted" -> {
                long artCount = m.stream().filter(r -> !r.parseError() && r.isArticle()).count();
                if (artCount == 0) return "n/a";
                return String.format(Locale.US, "%.1f%% (%d articles)",
                        mr.subtopicMismatches() * 100.0 / artCount, mr.subtopicMismatches());
            }
            case "p50 tokens/s" -> {
                List<Double> tpsList = m.stream()
                        .filter(r -> !r.parseError() && r.tokensPerSecond() != null)
                        .map(CallResult::tokensPerSecond)
                        .sorted()
                        .toList();
                if (tpsList.isEmpty()) return "n/a";
                int idx = Math.max(0, (int) Math.ceil(tpsList.size() * 50.0 / 100) - 1);
                return String.format(Locale.US, "%.1f", tpsList.get(idx));
            }
            case "think=false supported" -> { return mr.thinkNotSupported() ? "no" : "yes"; }
            default -> { return "—"; }
        }
    }

    private long latencyPercentile(List<CallResult> calls, int pct) {
        List<Long> lat = calls.stream()
                .filter(r -> !r.parseError())
                .map(CallResult::latencyMs)
                .sorted()
                .toList();
        if (lat.isEmpty()) return 0;
        int idx = Math.max(0, (int) Math.ceil(lat.size() * pct / 100.0) - 1);
        return lat.get(idx);
    }

    /** Returns [isArticleMatchFormatted, catMatchFormatted] e.g. ["100% (4/4)", "50% (1/2)"]. */
    private String[] pairwiseAgreement(ModelRunResult r1, ModelRunResult r2) {
        int artTotal = 0, artMatch = 0;
        int catTotal = 0, catMatch = 0;
        int n = Math.min(r1.measurements().size(), r2.measurements().size());
        for (int i = 0; i < n; i++) {
            CallResult c1 = r1.measurements().get(i);
            CallResult c2 = r2.measurements().get(i);
            if (!c1.preFiltered() && !c2.preFiltered() && !c1.parseError() && !c2.parseError()) {
                artTotal++;
                if (c1.isArticle() == c2.isArticle()) artMatch++;
                if (c1.isArticle() && c2.isArticle()) {
                    catTotal++;
                    if (c1.mainCategory() != null && c1.mainCategory().equals(c2.mainCategory()))
                        catMatch++;
                }
            }
        }
        String artStr = artTotal == 0 ? "n/a"
                : (artMatch * 100 / artTotal) + "% (" + artMatch + "/" + artTotal + ")";
        String catStr = catTotal == 0 ? "n/a"
                : (catMatch * 100 / catTotal) + "% (" + catMatch + "/" + catTotal + ")";
        return new String[]{artStr, catStr};
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static List<String> deduplicateByInputUrl(List<String> urls) {
        Set<String> seen = new LinkedHashSet<>();
        for (String url : urls) seen.add(tryNormalize(url));
        if (seen.size() == urls.size()) return new ArrayList<>(urls);
        // Rebuild in original order, keeping first occurrence
        List<String> result = new ArrayList<>(seen.size());
        Set<String> added = new HashSet<>();
        for (String url : urls) {
            if (added.add(tryNormalize(url))) result.add(url);
        }
        return result;
    }

    private static String tryNormalize(String url) {
        try {
            return org.example.util.UrlNormalizer.normalize(url);
        } catch (IllegalArgumentException e) {
            return url; // keep as-is if not a valid HTTP(S) URL
        }
    }

    private List<String> loadUrlsFromQueue() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            if (limit <= 0) {
                return s.createNativeQuery(
                        "SELECT url FROM article_queue WHERE status = 'PENDING'",
                        String.class)
                        .getResultList();
            }
            return s.createNativeQuery(
                    "SELECT url FROM article_queue WHERE status = 'PENDING' LIMIT :n",
                    String.class)
                    .setParameter("n", limit)
                    .getResultList();
        }
    }

    private static List<TagInfo> loadTaxonomy() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            return QueueWorker.loadTaxonomyFrom(s);
        }
    }

    private String baseUrl() {
        return ollamaUrl.endsWith("/") ? ollamaUrl.substring(0, ollamaUrl.length() - 1) : ollamaUrl;
    }

    private static String extractDomain(String url) {
        try {
            String host = new URI(url).getHost();
            return host != null ? host : url;
        } catch (Exception e) {
            return url;
        }
    }

    private static String csvField(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String csvSafe(String model) {
        return model.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private static String mdCell(String s) {
        return s == null ? "" : s.replace("|", "\\|").replace("\n", " ");
    }
}
