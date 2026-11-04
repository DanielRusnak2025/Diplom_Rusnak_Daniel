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

    @Test
    void extractSource_pContainer_whenNoArticle() {
        String html = """
                <html><body>
                  <div id="small"><p>Short text.</p></div>
                  <div id="main">
                    <p>First paragraph with substantial content about the topic.</p>
                    <p>Second paragraph continuing the story with more details here.</p>
                    <p>Third paragraph wrapping up the main points of the article.</p>
                  </div>
                </body></html>""";
        ParseResult r = PageParser.parse(html, BASE_URL);
        assertEquals("p-container", r.extractSource());
        assertTrue(r.textForAi().contains("First paragraph"), "text must come from the best p-container");
    }

    @Test
    void findBestPContainer_usesDirectChildrenOnly_outerWrapperDoesNotWin() {
        // Outer wrapper has 0 direct-child <p> — only nested divs.
        // main-div: 3 long direct-child <p> (~50 chars each) → highest direct-child score.
        // sidebar-div: 5 short direct-child <p> (~10 chars each) → lower direct-child score.
        // Expected: main-div wins; sidebar text must NOT appear in output.
        String mainPara = "This is a substantial paragraph from the main article content area here.";
        String html = "<html><body>" +
                "<div id=\"outer\">" +
                "  <div id=\"main\">" +
                "    <p>" + mainPara + " One.</p>" +
                "    <p>" + mainPara + " Two.</p>" +
                "    <p>" + mainPara + " Three.</p>" +
                "  </div>" +
                "  <div id=\"sidebar\">" +
                "    <p>Related 1</p>" +
                "    <p>Related 2</p>" +
                "    <p>Related 3</p>" +
                "    <p>Related 4</p>" +
                "    <p>Related 5</p>" +
                "  </div>" +
                "</div>" +
                "</body></html>";
        ParseResult r = PageParser.parse(html, BASE_URL);
        assertEquals("p-container", r.extractSource());
        assertTrue(r.textForAi().contains(mainPara), "main article text must be present");
        assertFalse(r.textForAi().contains("Related 1"), "sidebar text must NOT appear");
    }

    @Test
    void extractSource_metaOnly_whenBodyEmpty() {
        String html = """
                <html><head>
                  <meta property="og:description" content="A great article about technology."/>
                </head><body></body></html>""";
        ParseResult r = PageParser.parse(html, BASE_URL);
        assertEquals("meta-only", r.extractSource());
        assertTrue(r.textForAi().contains("great article"), "text must come from og:description");
    }

    @Test
    void extractSource_article_navAndFooterStripped() {
        String html = """
                <html><body>
                  <nav><p>Nav link one</p><p>Nav link two</p></nav>
                  <article><p>Real article body text here.</p></article>
                  <footer><p>Footer copyright text</p></footer>
                </body></html>""";
        ParseResult r = PageParser.parse(html, BASE_URL);
        assertEquals("article", r.extractSource());
        assertFalse(r.textForAi().contains("Nav link"), "nav must be stripped");
        assertFalse(r.textForAi().contains("Footer copyright"), "footer must be stripped");
        assertTrue(r.textForAi().contains("Real article body"), "article body must be present");
    }

    @Test
    void ogType_extractedFromMeta() {
        String html = """
                <html><head>
                  <meta property="og:type" content="article"/>
                </head><body></body></html>""";
        ParseResult r = PageParser.parse(html, BASE_URL);
        assertEquals("article", r.ogType());
    }

    @Test
    void ogType_nullWhenAbsent() {
        String html = "<html><head></head><body></body></html>";
        ParseResult r = PageParser.parse(html, BASE_URL);
        assertNull(r.ogType());
    }

    @Test
    void textForAi_urlPrependedAsFirstLine() {
        String html = "<html><body><article><p>Article text here.</p></article></body></html>";
        ParseResult r = PageParser.parse(html, BASE_URL);
        assertTrue(r.textForAi().startsWith("URL: " + BASE_URL),
                "text-for-AI must start with 'URL: <finalUrl>'");
    }

    @Test
    void textChars_excludesUrlPrefix_emptyBodyIsTooShort() {
        // URL is 300+ chars; no title/meta/body → textChars=0 (URL prefix not counted)
        String longUrl = "https://example.com/" + "a".repeat(300);
        String html = "<html><head></head><body></body></html>";
        ParseResult r = PageParser.parse(html, longUrl);
        assertTrue(r.textForAi().startsWith("URL: " + longUrl),
                "textForAi must still have the URL prefix");
        assertEquals(0, r.textChars(),
                "textChars must be 0 when body/title/meta are empty (URL prefix excluded)");
    }

    @Test
    void textForAi_includesTitleAndMetaDescription() {
        String html = """
                <html><head>
                  <title>My Article</title>
                  <meta name="description" content="A brief summary of the article."/>
                </head><body>
                  <article><p>The full article body text goes here.</p></article>
                </body></html>""";
        ParseResult r = PageParser.parse(html, BASE_URL);
        assertTrue(r.textForAi().contains("My Article"), "title must be included");
        assertTrue(r.textForAi().contains("brief summary"), "meta description must be included");
        assertTrue(r.textForAi().contains("full article body"), "body text must be included");
    }
}
