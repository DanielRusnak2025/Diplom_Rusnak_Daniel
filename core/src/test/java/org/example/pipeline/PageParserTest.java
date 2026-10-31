package org.example.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PageParserTest {

    private static final String BASE_URL = "https://example.com/article";

    @Test
    void title_usesOgTitle_whenPresent() {
        String html = """
                <html><head>
                  <meta property="og:title" content="OG Title Here"/>
                  <title>Plain Title</title>
                </head><body></body></html>""";
        ParseResult r = PageParser.parse(html, BASE_URL);
        assertEquals("OG Title Here", r.title());
    }

    @Test
    void title_fallsBackToTagTitle_whenNoOg() {
        String html = "<html><head><title>Plain Title</title></head><body></body></html>";
        ParseResult r = PageParser.parse(html, BASE_URL);
        assertEquals("Plain Title", r.title());
    }

    @Test
    void title_nullWhenNone() {
        String html = "<html><head></head><body></body></html>";
        ParseResult r = PageParser.parse(html, BASE_URL);
        assertNull(r.title());
    }

    @Test
    void publisher_usesOgSiteName_whenPresent() {
        String html = """
                <html><head>
                  <meta property="og:site_name" content="Acme News"/>
                </head><body></body></html>""";
        ParseResult r = PageParser.parse(html, BASE_URL);
        assertEquals("Acme News", r.publisher());
    }

    @Test
    void publisher_derivesFromDomain_whenNoOg() {
        String html = "<html><head></head><body></body></html>";
        ParseResult r = PageParser.parse(html, "https://www.example.com/path");
        assertEquals("example.com", r.publisher()); // www. stripped
    }

    @Test
    void publishedDate_extractedFromMeta() {
        String html = """
                <html><head>
                  <meta property="article:published_time" content="2026-10-31T10:00:00Z"/>
                </head><body></body></html>""";
        ParseResult r = PageParser.parse(html, BASE_URL);
        assertEquals("2026-10-31T10:00:00Z", r.publishedDate());
    }

    @Test
    void publishedDate_nullWhenAbsent() {
        ParseResult r = PageParser.parse("<html><head></head><body></body></html>", BASE_URL);
        assertNull(r.publishedDate());
    }

    @Test
    void canonical_usesLinkTag_whenPresent() {
        String html = """
                <html><head>
                  <link rel="canonical" href="https://example.com/canonical"/>
                </head><body></body></html>""";
        ParseResult r = PageParser.parse(html, BASE_URL);
        assertEquals("https://example.com/canonical", r.canonicalUrl());
    }

    @Test
    void canonical_fallsBackToFinalUrl() {
        ParseResult r = PageParser.parse("<html><head></head><body></body></html>", BASE_URL);
        assertEquals(BASE_URL, r.canonicalUrl());
    }

    @Test
    void textForAi_prefersArticleElement() {
        String html = """
                <html><body>
                  <nav>Navigation stuff</nav>
                  <article><p>Article content here</p></article>
                  <footer>Footer stuff</footer>
                </body></html>""";
        ParseResult r = PageParser.parse(html, BASE_URL);
        assertTrue(r.textForAi().contains("Article content"), "should use <article> text");
        assertFalse(r.textForAi().contains("Navigation"), "nav must be stripped");
        assertFalse(r.textForAi().contains("Footer"), "footer must be stripped");
    }

    @Test
    void textForAi_fallsBackToLargestParagraph() {
        String html = """
                <html><body>
                  <p>Short.</p>
                  <p>This is a much longer paragraph with more content that should be selected.</p>
                </body></html>""";
        ParseResult r = PageParser.parse(html, BASE_URL);
        assertTrue(r.textForAi().contains("much longer paragraph"),
                "should use the longest <p> when no <article>");
    }

    @Test
    void textForAi_truncatedAt2000Chars() {
        String html = "<html><body><article><p>" + "x".repeat(3000) + "</p></article></body></html>";
        ParseResult r = PageParser.parse(html, BASE_URL);
        assertEquals(2000, r.textForAi().length());
    }
}
