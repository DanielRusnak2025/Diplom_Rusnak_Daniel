package org.example.pipeline;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PageFetcherTest {

    private MockWebServer server;
    private PageFetcher fetcher;

    @BeforeEach
    void setup() throws IOException {
        server = new MockWebServer();
        server.start();
        // allowLoopback=true so MockWebServer (localhost) is reachable;
        // ports include 80/443 (for redirect targets) and the server's dynamic port
        fetcher = new PageFetcher(5, 2_000_000, 5, true,
                Set.of(80, 443, server.getPort()));
    }

    @AfterEach
    void teardown() throws IOException {
        fetcher.close();
        server.close();
    }

    private static MockResponse htmlResponse(String body) {
        return new MockResponse.Builder()
                .body(body)
                .addHeader("Content-Type", "text/html; charset=utf-8")
                .build();
    }

    @Test
    void fetch_simpleHtml_returnsResult() throws IOException {
        server.enqueue(htmlResponse("<html><body><p>Hello world</p></body></html>"));

        FetchResult result = fetcher.fetch(server.url("/article").toString());

        assertNotNull(result);
        assertTrue(result.html().contains("Hello world"));
        assertTrue(result.finalUrl().contains("/article"));
    }

    @Test
    void fetch_redirect_followsToFinalUrl() throws IOException {
        server.enqueue(new MockResponse.Builder()
                .code(301)
                .addHeader("Location", server.url("/final").toString())
                .build());
        server.enqueue(htmlResponse("<html><body>Final</body></html>"));

        FetchResult result = fetcher.fetch(server.url("/start").toString());

        assertTrue(result.finalUrl().endsWith("/final"), "finalUrl must be the redirect target");
        assertTrue(result.html().contains("Final"));
    }

    @Test
    void fetch_nonHtml_throwsNonHtmlException() {
        server.enqueue(new MockResponse.Builder()
                .body("{\"key\":\"val\"}")
                .addHeader("Content-Type", "application/json")
                .build());

        assertThrows(PageFetcher.NonHtmlException.class,
                () -> fetcher.fetch(server.url("/").toString()));
    }

    @Test
    void fetch_tooLarge_throwsContentTooLargeException() throws IOException {
        PageFetcher tiny = new PageFetcher(5, 10, 5, true, Set.of(server.getPort()));
        server.enqueue(new MockResponse.Builder()
                .body("x".repeat(100))
                .addHeader("Content-Type", "text/html")
                .build());

        assertThrows(PageFetcher.ContentTooLargeException.class,
                () -> tiny.fetch(server.url("/").toString()));
        tiny.close();
    }

    @Test
    void fetch_tooManyRedirects_throwsTooManyRedirectsException() {
        // maxRedirects=5, enqueue 6 hops to exceed the limit
        for (int i = 0; i < 6; i++) {
            server.enqueue(new MockResponse.Builder()
                    .code(301)
                    .addHeader("Location", server.url("/" + (i + 1)).toString())
                    .build());
        }

        assertThrows(PageFetcher.TooManyRedirectsException.class,
                () -> fetcher.fetch(server.url("/0").toString()));
    }

    @Test
    void fetch_ssrf_directLoopback_blocked() {
        // No server needed — SSRF check fires before any connection attempt
        PageFetcher noLoopback = new PageFetcher(5, 2_000_000, 5, false, Set.of(80, 443));
        assertThrows(PageFetcher.SsrfException.class,
                () -> noLoopback.fetch("http://127.0.0.1/"));
        noLoopback.close();
    }

    @Test
    void fetch_ssrf_redirectToPrivateIp_blocked() {
        // MockWebServer (localhost, allowed) redirects to a private IP → must be blocked
        server.enqueue(new MockResponse.Builder()
                .code(301)
                .addHeader("Location", "http://192.168.0.1/")
                .build());

        assertThrows(PageFetcher.SsrfException.class,
                () -> fetcher.fetch(server.url("/").toString()));
    }
}
