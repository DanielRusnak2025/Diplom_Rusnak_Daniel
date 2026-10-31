package org.example.pipeline;

import org.example.ai.RelevanceLevel;

import java.util.*;

public final class ArticleScorer {

    private ArticleScorer() {}

    /**
     * Computes a percent distribution (sum = 100) for AI-assigned tags.
     *
     * @param assignments  tagId → level from the AI classifier
     * @param tagKeywords  tagId → list of keyword patterns (from tag_keyword table, same language as text)
     * @param normalizedText  pre-normalized title + beginning of article text (KeywordMatcher.normalize applied)
     * @param maxBonus     ceiling on the keyword bonus per tag (from ai.properties keyword.bonus.max, default 2)
     */
    public static Map<Integer, Integer> score(
            Map<Integer, RelevanceLevel> assignments,
            Map<Integer, List<String>> tagKeywords,
            String normalizedText,
            int maxBonus) {

        if (assignments == null || assignments.isEmpty()) return Collections.emptyMap();

        List<Integer> tagIds = new ArrayList<>(assignments.keySet());

        int[] raw = new int[tagIds.size()];
        for (int i = 0; i < tagIds.size(); i++) {
            int tagId = tagIds.get(i);
            int baseWeight = assignments.get(tagId).weight;
            List<String> patterns = (tagKeywords != null)
                    ? tagKeywords.getOrDefault(tagId, Collections.emptyList())
                    : Collections.emptyList();
            int bonus = patterns.isEmpty() ? 0
                    : Math.min(KeywordMatcher.countMatches(normalizedText, patterns), maxBonus);
            raw[i] = baseWeight + bonus;
        }

        int totalRaw = Arrays.stream(raw).sum();
        if (totalRaw == 0) return Collections.emptyMap();

        // Largest-remainder method: guarantees sum = 100
        double[] exact = new double[tagIds.size()];
        int[] floors = new int[tagIds.size()];
        int sumFloors = 0;
        for (int i = 0; i < tagIds.size(); i++) {
            exact[i] = (double) raw[i] * 100.0 / totalRaw;
            floors[i] = (int) exact[i];
            sumFloors += floors[i];
        }

        int remainder = 100 - sumFloors;
        Integer[] indices = new Integer[tagIds.size()];
        for (int i = 0; i < indices.length; i++) indices[i] = i;
        Arrays.sort(indices, (a, b) -> Double.compare(exact[b] - floors[b], exact[a] - floors[a]));
        for (int i = 0; i < remainder; i++) floors[indices[i]]++;

        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < tagIds.size(); i++) result.put(tagIds.get(i), floors[i]);
        return result;
    }
}
