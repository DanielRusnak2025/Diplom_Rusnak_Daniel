package org.example.pipeline;

import java.util.Arrays;
import java.util.List;

public final class KeywordMatcher {

    private KeywordMatcher() {}

    public static String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase()
                   .replace('ё', 'е')
                   .replaceAll("\\s+", " ")
                   .trim();
    }

    static String[] tokenize(String normalized) {
        if (normalized.isEmpty()) return new String[0];
        return Arrays.stream(normalized.split("[^\\p{L}\\p{N}]+"))
                     .filter(s -> !s.isEmpty())
                     .toArray(String[]::new);
    }

    private static boolean patternWordMatchesToken(String token, String patternWord) {
        if (patternWord.startsWith("*") && patternWord.endsWith("*") && patternWord.length() > 2) {
            String core = patternWord.substring(1, patternWord.length() - 1);
            return token.contains(core);
        }
        if (patternWord.endsWith("*")) {
            String prefix = patternWord.substring(0, patternWord.length() - 1);
            return !prefix.isEmpty() && token.startsWith(prefix);
        }
        return token.equals(patternWord);
    }

    private static boolean matchPhrase(String[] tokens, String[] patternWords) {
        if (patternWords.length == 0) return false;
        outer:
        for (int start = 0; start <= tokens.length - patternWords.length; start++) {
            for (int i = 0; i < patternWords.length; i++) {
                if (!patternWordMatchesToken(tokens[start + i], patternWords[i])) continue outer;
            }
            return true;
        }
        return false;
    }

    /**
     * Returns the number of distinct patterns that match at least once in normalizedText.
     * Each pattern is counted at most once regardless of how many times it matches.
     * Multi-word patterns require consecutive tokens.
     */
    public static int countMatches(String normalizedText, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) return 0;
        String[] tokens = tokenize(normalizedText);
        if (tokens.length == 0) return 0;
        int count = 0;
        for (String pattern : patterns) {
            String[] patternWords = normalize(pattern).split("\\s+");
            if (patternWords.length == 0 || (patternWords.length == 1 && patternWords[0].isEmpty())) continue;
            if (matchPhrase(tokens, patternWords)) count++;
        }
        return count;
    }
}
