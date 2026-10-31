package org.example.pipeline;

import org.example.ai.RelevanceLevel;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ArticleScorerTest {

    private static final int MAX_BONUS = 2;

    @Test
    void threeTagsNoBonus_sumsTo100() {
        // HIGH(3)+MED(2)+LOW(1)=6; exact 50.0, 33.33, 16.67
        // largest remainders: LOW gets +1 → 50, 33, 17
        Map<Integer, RelevanceLevel> assignments = new LinkedHashMap<>();
        assignments.put(1, RelevanceLevel.HIGH);
        assignments.put(2, RelevanceLevel.MEDIUM);
        assignments.put(3, RelevanceLevel.LOW);

        Map<Integer, Integer> result = ArticleScorer.score(assignments, null, "", MAX_BONUS);

        assertEquals(100, result.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(50, result.get(1));
        assertEquals(33, result.get(2));
        assertEquals(17, result.get(3));
    }

    @Test
    void keywordBonus_added() {
        // HIGH(3) + 1 match = 4; LOW(1) = 1; total=5 → 80%, 20%
        Map<Integer, RelevanceLevel> assignments = new LinkedHashMap<>();
        assignments.put(1, RelevanceLevel.HIGH);
        assignments.put(2, RelevanceLevel.LOW);

        Map<Integer, List<String>> keywords = Map.of(1, List.of("machine* learn*"));
        String text = KeywordMatcher.normalize("machine learning revolution");

        Map<Integer, Integer> result = ArticleScorer.score(assignments, keywords, text, MAX_BONUS);

        assertEquals(100, result.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(80, result.get(1));
        assertEquals(20, result.get(2));
    }

    @Test
    void bonusCapped_atMaxBonus() {
        // 4 patterns match but maxBonus=2 → HIGH(3)+2=5; LOW(1)=1; total=6 → 83%, 17%
        Map<Integer, RelevanceLevel> assignments = new LinkedHashMap<>();
        assignments.put(1, RelevanceLevel.HIGH);
        assignments.put(2, RelevanceLevel.LOW);

        Map<Integer, List<String>> keywords = Map.of(1, List.of("apple", "juice", "fresh", "organic"));
        String text = KeywordMatcher.normalize("apple juice fresh organic drink");

        Map<Integer, Integer> result = ArticleScorer.score(assignments, keywords, text, MAX_BONUS);

        assertEquals(100, result.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(83, result.get(1));
        assertEquals(17, result.get(2));
    }

    @Test
    void nonAiTag_notInResult() {
        // tag 99 has keywords, but is not in assignments → must not appear in output
        Map<Integer, RelevanceLevel> assignments = Map.of(1, RelevanceLevel.HIGH);
        Map<Integer, List<String>> keywords = Map.of(
                1, List.of(),
                99, List.of("apple"));
        String text = KeywordMatcher.normalize("apple news");

        Map<Integer, Integer> result = ArticleScorer.score(assignments, keywords, text, MAX_BONUS);

        assertFalse(result.containsKey(99), "tag absent from assignments must not appear in result");
        assertEquals(100, result.get(1));
    }

    @Test
    void singleTag_alwaysHundred() {
        Map<Integer, Integer> result = ArticleScorer.score(
                Map.of(1, RelevanceLevel.MEDIUM), null, "", MAX_BONUS);
        assertEquals(100, result.get(1));
    }

    @Test
    void roundingHandled_exactlyTo100() {
        // Verify that no matter how rounding falls, sum is always exactly 100
        Map<Integer, RelevanceLevel> assignments = new LinkedHashMap<>();
        assignments.put(1, RelevanceLevel.HIGH);
        assignments.put(2, RelevanceLevel.MEDIUM);
        assignments.put(3, RelevanceLevel.LOW);
        Map<Integer, Integer> result = ArticleScorer.score(
                assignments, Collections.emptyMap(), "", MAX_BONUS);
        assertEquals(100, result.values().stream().mapToInt(Integer::intValue).sum());
    }
}
