package org.example.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OllamaClassifierTest {

    private MockWebServer server;
    private OllamaClassifier classifier;
    private final ObjectMapper mapper = new ObjectMapper();

    // Minimal taxonomy: Politics (subtopics General/Elections), Economy (subtopics General/Markets)
    private static final List<TagInfo> TAXONOMY = List.of(
            new TagInfo(1, "Politics", "Political events, governance, elections", List.of(
                    new SubtopicInfo(10, "General"),
                    new SubtopicInfo(11, "Elections")
            )),
            new TagInfo(2, "Economy", "Economic news, markets, trade", List.of(
                    new SubtopicInfo(20, "General"),
                    new SubtopicInfo(21, "Markets")
            ))
    );

    @BeforeEach
    void setup() throws IOException {
        server = new MockWebServer();
        server.start();
        classifier = new OllamaClassifier(
                server.url("").toString(),
                "testmodel",
                new OkHttpClient());
    }

    @AfterEach
    void teardown() throws IOException {
        server.close();
    }

    /** Builds a mock Ollama response whose message.content is the given classification object. */
    private MockResponse ollamaResponse(Object classification) throws Exception {
        Map<String, Object> message = Map.of("role", "assistant",
                "content", mapper.writeValueAsString(classification));
        String body = mapper.writeValueAsString(Map.of("message", message));
        return new MockResponse.Builder()
                .body(body)
                .addHeader("Content-Type", "application/json")
                .build();
    }

    /** Builds a mock Ollama response whose message.content is not valid JSON. */
    private static MockResponse invalidContentResponse() {
        return new MockResponse.Builder()
                .body("{\"message\":{\"role\":\"assistant\",\"content\":\"NOT_VALID_JSON\"}}")
                .addHeader("Content-Type", "application/json")
                .build();
    }

    // ── schema builder ────────────────────────────────────────────────────────

    @Test
    void schemaBuilder_containsCategoryEnumsAndStructure() {
        Map<String, Object> schema = classifier.buildJsonSchema(TAXONOMY);

        assertEquals("object", schema.get("type"));
        assertTrue(((List<?>) schema.get("required")).contains("is_article"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("is_article"));
        assertTrue(props.containsKey("main"));
        assertTrue(props.containsKey("extra"));

        @SuppressWarnings("unchecked")
        Map<String, Object> mainProps = (Map<String, Object>)
                ((Map<?, ?>) props.get("main")).get("properties");
        @SuppressWarnings("unchecked")
        List<String> enumVals = (List<String>)
                ((Map<?, ?>) mainProps.get("category")).get("enum");

        assertEquals(List.of("Politics", "Economy"), enumVals);

        @SuppressWarnings("unchecked")
        Map<String, Object> extra = (Map<String, Object>) props.get("extra");
        assertEquals(2, extra.get("maxItems"));
    }

    // ── valid response ────────────────────────────────────────────────────────

    @Test
    void classify_validResponse_returnsClassification() throws Exception {
        server.enqueue(ollamaResponse(Map.of(
                "is_article", true,
                "main", Map.of("category", "Politics", "subtopic", "Elections"),
                "extra", List.of())));

        ArticleClassification result = classifier.classify("Election news text", TAXONOMY);

        assertTrue(result.isArticle());
        assertEquals(1, result.mainCategoryId());    // Politics id=1
        assertEquals(11, result.mainSubtopicId());   // Elections id=11
        assertTrue(result.extra().isEmpty());
    }

    @Test
    void classify_withExtra_parsesExtraAssignments() throws Exception {
        server.enqueue(ollamaResponse(Map.of(
                "is_article", true,
                "main", Map.of("category", "Politics", "subtopic", "General"),
                "extra", List.of(Map.of("category", "Economy", "level", "MEDIUM")))));

        ArticleClassification result = classifier.classify("Politics and economy text", TAXONOMY);

        assertTrue(result.isArticle());
        assertEquals(1, result.extra().size());
        assertEquals(2, result.extra().get(0).categoryId());  // Economy id=2
        assertEquals(RelevanceLevel.MEDIUM, result.extra().get(0).level());
    }

    // ── is_article=false ─────────────────────────────────────────────────────

    @Test
    void classify_notArticle_returnsIsArticleFalse() throws Exception {
        server.enqueue(ollamaResponse(Map.of("is_article", false)));

        ArticleClassification result = classifier.classify("Subscribe now!", TAXONOMY);

        assertFalse(result.isArticle());
        assertNull(result.mainCategoryId());
        assertNull(result.mainSubtopicId());
    }

    // ── subtopic mismatch → General ───────────────────────────────────────────

    @Test
    void classify_subtopicFromWrongCategory_substitutesGeneral() throws Exception {
        // "Markets" belongs to Economy, not Politics → must be replaced with Politics/General
        server.enqueue(ollamaResponse(Map.of(
                "is_article", true,
                "main", Map.of("category", "Politics", "subtopic", "Markets"),
                "extra", List.of())));

        int before = OllamaClassifier.subtopicMismatchCount();
        ArticleClassification result = classifier.classify("Political news", TAXONOMY);

        assertEquals(1, result.mainCategoryId());   // Politics
        assertEquals(10, result.mainSubtopicId());  // General (id=10)
        assertEquals(before + 1, OllamaClassifier.subtopicMismatchCount());
    }

    // ── retry logic ───────────────────────────────────────────────────────────

    @Test
    void classify_invalidJsonThenValid_retriesExactlyOnce() throws Exception {
        server.enqueue(invalidContentResponse());
        server.enqueue(ollamaResponse(Map.of("is_article", false)));

        ArticleClassification result = classifier.classify("Some text", TAXONOMY);

        assertFalse(result.isArticle());
        assertEquals(2, server.getRequestCount(), "must make exactly 2 requests");
    }

    @Test
    void classify_invalidJsonTwice_throwsClassificationException() throws Exception {
        server.enqueue(invalidContentResponse());
        server.enqueue(invalidContentResponse());

        assertThrows(ClassificationException.class,
                () -> classifier.classify("Some text", TAXONOMY));
        assertEquals(2, server.getRequestCount());
    }

    // ── request body fields ───────────────────────────────────────────────────

    @Test
    void classify_requestBody_containsThinkFalseStreamFalseAndFormat() throws Exception {
        server.enqueue(ollamaResponse(Map.of("is_article", false)));

        classifier.classify("Any text", TAXONOMY);

        RecordedRequest req = server.takeRequest();
        String raw = req.getBody().utf8();
        JsonNode body = mapper.readTree(raw);

        assertFalse(body.path("think").asBoolean(true),  "think must be false");
        assertFalse(body.path("stream").asBoolean(true), "stream must be false");
        assertFalse(body.path("format").isMissingNode(), "format must be present");
        assertEquals("testmodel", body.path("model").asText());
    }
}
