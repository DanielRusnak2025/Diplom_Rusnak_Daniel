package org.example.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArticlePreFilterTest {

    private static final String NORMAL_URL  = "https://example.com/article/some-news";
    private static final String LONG_TEXT   =
            "This article covers recent developments in artificial intelligence and machine learning. " +
            "Researchers have made significant breakthroughs in natural language processing over the past year. " +
            "These advances are expected to transform industries from healthcare to finance.";

    private static ParseResult pr(String title, String text, int chars, String ogType) {
        return new ParseResult(title, "pub", null, NORMAL_URL, text, chars, "article", ogType);
    }

    @Test
    void normalArticle_returnsNull() {
        assertNull(ArticlePreFilter.check(
                pr("Good Article", LONG_TEXT, LONG_TEXT.length(), null),
                NORMAL_URL));
    }

    @Test
    void rootPath_returnsRootUrl() {
        assertEquals("root-url",
                ArticlePreFilter.check(
                        pr("Home", LONG_TEXT, LONG_TEXT.length(), null),
                        "https://example.com/"));
    }

    @Test
    void emptyPath_returnsRootUrl() {
        assertEquals("root-url",
                ArticlePreFilter.check(
                        pr("Home", LONG_TEXT, LONG_TEXT.length(), null),
                        "https://example.com"));
    }

    @Test
    void titleContains404_returnsNotFound() {
        assertEquals("not-found",
                ArticlePreFilter.check(
                        pr("404 - Page Not Found", LONG_TEXT, LONG_TEXT.length(), null),
                        NORMAL_URL));
    }

    @Test
    void titleContainsPageNotFound_caseInsensitive_returnsNotFound() {
        assertEquals("not-found",
                ArticlePreFilter.check(
                        pr("Error: page not found", LONG_TEXT, LONG_TEXT.length(), null),
                        NORMAL_URL));
    }

    @Test
    void ogTypeProduct_returnsNull() {
        assertNull(ArticlePreFilter.check(
                pr("Product Page", LONG_TEXT, LONG_TEXT.length(), "product"),
                NORMAL_URL));
    }

    @Test
    void ogTypeArticle_returnsNull() {
        assertNull(ArticlePreFilter.check(
                pr("News Article", LONG_TEXT, LONG_TEXT.length(), "article"),
                NORMAL_URL));
    }

    @Test
    void textCharsTooShort_returnsTooShort() {
        assertEquals("too-short",
                ArticlePreFilter.check(
                        pr("Short", "Short text.", 150, null),
                        NORMAL_URL));
    }

    @Test
    void textCharsAtThreshold_returnsNull() {
        String text = "x".repeat(ArticlePreFilter.MIN_TEXT_CHARS);
        assertNull(ArticlePreFilter.check(
                pr("Threshold Article", text, ArticlePreFilter.MIN_TEXT_CHARS, null),
                NORMAL_URL));
    }

    @Test
    void rootUrlCheckedBeforeNotFound() {
        assertEquals("root-url",
                ArticlePreFilter.check(
                        pr("404 Page Not Found", LONG_TEXT, LONG_TEXT.length(), null),
                        "https://example.com/"));
    }
}
