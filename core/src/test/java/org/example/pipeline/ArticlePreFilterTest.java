package org.example.pipeline;

import org.junit.jupiter.api.Test;

import java.util.Set;

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
                NORMAL_URL, 200));
    }

    @Test
    void rootPath_returnsRootUrl() {
        assertEquals("root-url",
                ArticlePreFilter.check(
                        pr("Home", LONG_TEXT, LONG_TEXT.length(), null),
                        "https://example.com/", 200));
    }

    @Test
    void emptyPath_returnsRootUrl() {
        assertEquals("root-url",
                ArticlePreFilter.check(
                        pr("Home", LONG_TEXT, LONG_TEXT.length(), null),
                        "https://example.com", 200));
    }

    @Test
    void http404_returnsNotFound() {
        assertEquals("not-found",
                ArticlePreFilter.check(
                        pr("Whatever Page", LONG_TEXT, LONG_TEXT.length(), null),
                        NORMAL_URL, 404));
    }

    @Test
    void http410_returnsNotFound() {
        assertEquals("not-found",
                ArticlePreFilter.check(
                        pr("Gone Page", LONG_TEXT, LONG_TEXT.length(), null),
                        NORMAL_URL, 410));
    }

    @Test
    void titleContains404PageNotFound_returnsNotFound() {
        assertEquals("not-found",
                ArticlePreFilter.check(
                        pr("404 - Page Not Found", LONG_TEXT, LONG_TEXT.length(), null),
                        NORMAL_URL, 200));
    }

    @Test
    void titleContainsPageNotFound_caseInsensitive_returnsNotFound() {
        assertEquals("not-found",
                ArticlePreFilter.check(
                        pr("Error: page not found", LONG_TEXT, LONG_TEXT.length(), null),
                        NORMAL_URL, 200));
    }

    @Test
    void title404MigrantsRescued_notAFalsePositive_returnsNull() {
        // "404" in title that is NOT a 404 error page
        assertNull(ArticlePreFilter.check(
                pr("404 migrants rescued from Mediterranean", LONG_TEXT, LONG_TEXT.length(), null),
                NORMAL_URL, 200));
    }

    @Test
    void ogTypeProduct_returnsNull() {
        assertNull(ArticlePreFilter.check(
                pr("Product Page", LONG_TEXT, LONG_TEXT.length(), "product"),
                NORMAL_URL, 200));
    }

    @Test
    void ogTypeArticle_returnsNull() {
        assertNull(ArticlePreFilter.check(
                pr("News Article", LONG_TEXT, LONG_TEXT.length(), "article"),
                NORMAL_URL, 200));
    }

    @Test
    void textCharsTooShort_returnsTooShort() {
        assertEquals("too-short",
                ArticlePreFilter.check(
                        pr("Short", "Short text.", 150, null),
                        NORMAL_URL, 200));
    }

    @Test
    void textCharsAtThreshold_returnsNull() {
        String text = "x".repeat(ArticlePreFilter.MIN_TEXT_CHARS);
        assertNull(ArticlePreFilter.check(
                pr("Threshold Article", text, ArticlePreFilter.MIN_TEXT_CHARS, null),
                NORMAL_URL, 200));
    }

    @Test
    void rootUrlCheckedBeforeNotFound() {
        assertEquals("root-url",
                ArticlePreFilter.check(
                        pr("404 Page Not Found", LONG_TEXT, LONG_TEXT.length(), null),
                        "https://example.com/", 200));
    }

    @Test
    void rootUrlWithTrackingParamsOnly_returnsRootUrl() {
        // utm_source is a tracking param → stripped by UrlNormalizer → still root-url
        assertEquals("root-url",
                ArticlePreFilter.check(
                        pr("Home", LONG_TEXT, LONG_TEXT.length(), null),
                        "https://example.com/?utm_source=newsletter", 200));
    }

    @Test
    void rootUrlWithNonTrackingParam_allowsThrough() {
        // id=123 is NOT a tracking param → real article page → not root-url
        assertNull(ArticlePreFilter.check(
                pr("Real Article", LONG_TEXT, LONG_TEXT.length(), null),
                "https://example.com/?id=123", 200));
    }

    @Test
    void newsletterHost_exactMatch_returnsNewsletterHost() {
        assertEquals("newsletter-host",
                ArticlePreFilter.check(
                        pr("Newsletter", LONG_TEXT, LONG_TEXT.length(), null),
                        "https://mailchi.mp/abc/issue-42", 200,
                        Set.of("mailchi.mp")));
    }

    @Test
    void newsletterHost_subdomainMatch_returnsNewsletterHost() {
        // us18.campaign-archive.com ends with ".campaign-archive.com"
        assertEquals("newsletter-host",
                ArticlePreFilter.check(
                        pr("Newsletter", LONG_TEXT, LONG_TEXT.length(), null),
                        "https://us18.campaign-archive.com/some-path", 200,
                        Set.of("campaign-archive.com")));
    }

    @Test
    void newsletterHost_emptySet_allowsThrough() {
        // No newsletter hosts configured → 3-arg overload → allowed through
        assertNull(ArticlePreFilter.check(
                pr("Real Article", LONG_TEXT, LONG_TEXT.length(), null),
                "https://mailchi.mp/abc/issue-42", 200));
    }

    @Test
    void newsletterHost_nonMatchingHost_allowsThrough() {
        assertNull(ArticlePreFilter.check(
                pr("Real Article", LONG_TEXT, LONG_TEXT.length(), null),
                "https://www.reuters.com/article/123", 200,
                Set.of("mailchi.mp", "campaign-archive.com")));
    }
}
