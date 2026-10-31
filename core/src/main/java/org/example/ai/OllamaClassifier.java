package org.example.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class OllamaClassifier implements ArticleClassifier {

    private static final Logger LOG = Logger.getLogger(OllamaClassifier.class.getName());
    private static final AtomicInteger SUBTOPIC_MISMATCH_COUNT = new AtomicInteger();
    static final String PROMPT_VERSION = "v1";

    private static final String PROMPT_TEMPLATE;
    static {
        try (InputStream in = OllamaClassifier.class
                .getResourceAsStream("/prompts/classify_v1.txt")) {
            if (in == null) throw new ExceptionInInitializerError(
                    "classpath resource prompts/classify_v1.txt not found");
            PROMPT_TEMPLATE = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final OkHttpClient client;
    private final String ollamaUrl;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Production constructor — reads config from AiConfig. */
    public OllamaClassifier() {
        this(AiConfig.getOllamaUrl(), AiConfig.getOllamaModel(),
                new OkHttpClient.Builder()
                        .connectTimeout(AiConfig.getFetchTimeoutSeconds(), TimeUnit.SECONDS)
                        .readTimeout(120, TimeUnit.SECONDS)
                        .build());
    }

    /** Test / custom constructor. */
    OllamaClassifier(String ollamaUrl, String model, OkHttpClient client) {
        this.ollamaUrl = ollamaUrl;
        this.model = model;
        this.client = client;
    }

    public static int subtopicMismatchCount() { return SUBTOPIC_MISMATCH_COUNT.get(); }

    @Override
    public ArticleClassification classify(String text, Collection<TagInfo> taxonomy)
            throws ClassificationException {
        List<TagInfo> cats = List.copyOf(taxonomy);
        Map<String, TagInfo> byName = indexByName(cats);
        Map<String, Object> schema = buildJsonSchema(cats);
        String prompt = buildPrompt(text, cats);
        String body = buildRequestBody(prompt, schema);

        String response = callOllama(body);
        try {
            return parse(response, byName);
        } catch (IOException e) {
            LOG.warning("Classification parse failed (attempt 1): " + e.getMessage() + "; retrying");
            response = callOllama(body);
            try {
                return parse(response, byName);
            } catch (IOException e2) {
                throw new ClassificationException("Classification failed after retry", e2);
            }
        }
    }

    private String callOllama(String requestBody) throws ClassificationException {
        String url = (ollamaUrl.endsWith("/") ? ollamaUrl.substring(0, ollamaUrl.length() - 1) : ollamaUrl)
                + "/api/chat";
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(
                        requestBody.getBytes(StandardCharsets.UTF_8),
                        MediaType.get("application/json; charset=utf-8")))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new ClassificationException("Ollama returned HTTP " + response.code());
            }
            ResponseBody rb = response.body();
            return rb == null ? "" : rb.string();
        } catch (IOException e) {
            throw new ClassificationException("Ollama request failed", e);
        }
    }

    private ArticleClassification parse(String responseJson, Map<String, TagInfo> byName)
            throws IOException {
        JsonNode root = mapper.readTree(responseJson);
        JsonNode contentNode = root.path("message").path("content");
        if (contentNode.isMissingNode()) {
            throw new IOException("Missing message.content in Ollama response");
        }
        JsonNode cls = mapper.readTree(contentNode.asText());

        boolean isArticle = cls.path("is_article").asBoolean(false);
        if (!isArticle) return ArticleClassification.notAnArticle();

        JsonNode mainNode = cls.path("main");
        if (mainNode.isMissingNode() || mainNode.isNull()) {
            throw new IOException("is_article=true but 'main' field missing");
        }

        String catName = mainNode.path("category").asText();
        String subName = mainNode.path("subtopic").asText();

        TagInfo cat = byName.get(catName.toLowerCase());
        if (cat == null) throw new IOException("Unknown category: " + catName);
        SubtopicInfo sub = resolveSubtopic(cat, subName);

        List<ExtraAssignment> extra = parseExtra(cls.path("extra"), byName);
        return new ArticleClassification(true, cat.id(), sub.id(), extra);
    }

    private SubtopicInfo resolveSubtopic(TagInfo cat, String subName) {
        Optional<SubtopicInfo> found = cat.subtopics().stream()
                .filter(s -> s.name().equalsIgnoreCase(subName))
                .findFirst();
        if (found.isPresent()) return found.get();

        int count = SUBTOPIC_MISMATCH_COUNT.incrementAndGet();
        LOG.warning("Subtopic '" + subName + "' not in category '" + cat.name()
                + "' (total mismatches=" + count + "); substituting 'General'");

        return cat.subtopics().stream()
                .filter(s -> s.name().equalsIgnoreCase("General"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Category '" + cat.name() + "' has no 'General' subtopic"));
    }

    private List<ExtraAssignment> parseExtra(JsonNode extraNode, Map<String, TagInfo> byName) {
        if (!extraNode.isArray()) return List.of();
        List<ExtraAssignment> result = new ArrayList<>();
        for (JsonNode item : extraNode) {
            String catName = item.path("category").asText();
            String levelStr = item.path("level").asText();
            TagInfo cat = byName.get(catName.toLowerCase());
            if (cat == null) continue;
            try {
                result.add(new ExtraAssignment(cat.id(), RelevanceLevel.valueOf(levelStr)));
            } catch (IllegalArgumentException ignored) { /* skip unknown level */ }
        }
        return result;
    }

    private String buildPrompt(String text, List<TagInfo> cats) {
        StringBuilder sb = new StringBuilder();
        for (TagInfo cat : cats) {
            sb.append("- ").append(cat.name()).append(": ").append(cat.description()).append('\n');
            if (!cat.subtopics().isEmpty()) {
                sb.append("  Subtopics: ");
                sb.append(cat.subtopics().stream().map(SubtopicInfo::name)
                        .collect(Collectors.joining(", ")));
                sb.append('\n');
            }
        }
        return PROMPT_TEMPLATE
                .replace("{CATEGORIES}", sb.toString().trim())
                .replace("{TEXT}", text);
    }

    Map<String, Object> buildJsonSchema(List<TagInfo> cats) {
        List<String> names = cats.stream().map(TagInfo::name).collect(Collectors.toList());

        Map<String, Object> catEnum = new LinkedHashMap<>();
        catEnum.put("type", "string");
        catEnum.put("enum", names);

        Map<String, Object> levelEnum = new LinkedHashMap<>();
        levelEnum.put("type", "string");
        levelEnum.put("enum", List.of("HIGH", "MEDIUM", "LOW"));

        Map<String, Object> mainProps = new LinkedHashMap<>();
        mainProps.put("category", catEnum);
        mainProps.put("subtopic", Map.of("type", "string"));
        Map<String, Object> mainSchema = new LinkedHashMap<>();
        mainSchema.put("type", "object");
        mainSchema.put("required", List.of("category", "subtopic"));
        mainSchema.put("properties", mainProps);

        Map<String, Object> extraItemProps = new LinkedHashMap<>();
        extraItemProps.put("category", catEnum);
        extraItemProps.put("level", levelEnum);
        Map<String, Object> extraItemSchema = new LinkedHashMap<>();
        extraItemSchema.put("type", "object");
        extraItemSchema.put("required", List.of("category", "level"));
        extraItemSchema.put("properties", extraItemProps);
        Map<String, Object> extraSchema = new LinkedHashMap<>();
        extraSchema.put("type", "array");
        extraSchema.put("maxItems", 2);
        extraSchema.put("items", extraItemSchema);

        Map<String, Object> topProps = new LinkedHashMap<>();
        topProps.put("is_article", Map.of("type", "boolean"));
        topProps.put("main", mainSchema);
        topProps.put("extra", extraSchema);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of("is_article"));
        schema.put("properties", topProps);
        return schema;
    }

    private String buildRequestBody(String prompt, Map<String, Object> schema) {
        Map<String, Object> message = Map.of("role", "user", "content", prompt);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(message));
        body.put("stream", false);
        body.put("think", false);
        body.put("options", Map.of("temperature", 0));
        body.put("format", schema);
        try {
            return mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to build Ollama request body", e);
        }
    }

    private static Map<String, TagInfo> indexByName(List<TagInfo> cats) {
        return cats.stream().collect(Collectors.toMap(t -> t.name().toLowerCase(), t -> t));
    }
}
