package org.example.pipeline;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KeywordMatcherTest {

    @Test
    void normalize_lowercasesAndReplacesYo() {
        assertEquals("ежик елка", KeywordMatcher.normalize("Ёжик Ёлка"));
    }

    @Test
    void exactWord_matches() {
        assertEquals(1, KeywordMatcher.countMatches(KeywordMatcher.normalize("apple juice"), List.of("apple")));
    }

    @Test
    void exactWord_noPartialMatch_suffix() {
        assertEquals(0, KeywordMatcher.countMatches(KeywordMatcher.normalize("apples"), List.of("apple")));
    }

    @Test
    void exactWord_noPartialMatch_prefix() {
        assertEquals(0, KeywordMatcher.countMatches(KeywordMatcher.normalize("pineapple"), List.of("apple")));
    }

    @Test
    void prefixPattern_matches() {
        assertEquals(1, KeywordMatcher.countMatches(KeywordMatcher.normalize("application"), List.of("appl*")));
    }

    @Test
    void prefixPattern_noMatch_notPrefix() {
        assertEquals(0, KeywordMatcher.countMatches(KeywordMatcher.normalize("pineapple"), List.of("appl*")));
    }

    @Test
    void substringPattern_matchesInfix() {
        assertEquals(1, KeywordMatcher.countMatches(KeywordMatcher.normalize("pineapple"), List.of("*apple*")));
    }

    @Test
    void substringPattern_matchesExactToken() {
        assertEquals(1, KeywordMatcher.countMatches(KeywordMatcher.normalize("apple"), List.of("*apple*")));
    }

    @Test
    void phrasePattern_consecutiveTokens() {
        assertEquals(1, KeywordMatcher.countMatches(
                KeywordMatcher.normalize("machine learning is great"), List.of("machine* learn*")));
    }

    @Test
    void phrasePattern_notConsecutive_noMatch() {
        assertEquals(0, KeywordMatcher.countMatches(
                KeywordMatcher.normalize("machine deep learning"), List.of("machine* learn*")));
    }

    @Test
    void caseInsensitive() {
        assertEquals(1, KeywordMatcher.countMatches(KeywordMatcher.normalize("Apple Juice"), List.of("apple")));
    }

    @Test
    void yoReplacement() {
        assertEquals(1, KeywordMatcher.countMatches(KeywordMatcher.normalize("ёлка стоит"), List.of("елк*")));
    }

    @Test
    void patternCountedOnce() {
        assertEquals(1, KeywordMatcher.countMatches(
                KeywordMatcher.normalize("apple apple apple"), List.of("apple")));
    }

    @Test
    void multiplePatterns_eachCountedOnce() {
        assertEquals(2, KeywordMatcher.countMatches(
                KeywordMatcher.normalize("apple juice"), List.of("apple", "juice")));
    }

    @Test
    void emptyPatterns_returnsZero() {
        assertEquals(0, KeywordMatcher.countMatches(KeywordMatcher.normalize("apple"), List.of()));
    }

    @Test
    void emptyText_returnsZero() {
        assertEquals(0, KeywordMatcher.countMatches("", List.of("apple")));
    }
}
