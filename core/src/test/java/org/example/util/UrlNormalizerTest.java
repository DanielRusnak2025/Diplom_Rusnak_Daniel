package org.example.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrlNormalizerTest {

    // ── happy-path cases ──────────────────────────────────────────────────────

    @Test
    void schemeAndHostAreLowercased() {
        assertEquals("http://example.com/path",
                UrlNormalizer.normalize("HTTP://Example.COM/path"));
    }

    @Test
    void trailingSlashInPathRemoved() {
        assertEquals("https://example.com/path",
                UrlNormalizer.normalize("https://example.com/path/"));
    }

    @Test
    void rootSlashPreserved() {
        assertEquals("https://example.com/",
                UrlNormalizer.normalize("https://example.com/"));
    }

    @Test
    void noPathRemainsEmpty() {
        assertEquals("https://example.com",
                UrlNormalizer.normalize("https://example.com"));
    }

    @Test
    void fragmentStripped() {
        assertEquals("https://example.com/path",
                UrlNormalizer.normalize("https://example.com/path#section"));
    }

    @Test
    void utmSourceRemoved() {
        assertEquals("https://example.com/path",
                UrlNormalizer.normalize("https://example.com/path?utm_source=newsletter"));
    }

    @Test
    void utmMixedWithRealParamKept() {
        assertEquals("https://example.com/path?topic=news",
                UrlNormalizer.normalize("https://example.com/path?utm_campaign=x&topic=news"));
    }

    @Test
    void queryParamsSorted() {
        assertEquals("https://example.com/path?a=1&b=2",
                UrlNormalizer.normalize("https://example.com/path?b=2&a=1"));
    }

    @Test
    void fbclidRemoved() {
        assertEquals("https://example.com/path?b=2",
                UrlNormalizer.normalize("https://example.com/path?fbclid=abc123&b=2"));
    }

    @Test
    void gclidRemoved() {
        assertEquals("https://example.com/path",
                UrlNormalizer.normalize("https://example.com/path?gclid=xyz"));
    }

    @Test
    void mcParamsRemoved() {
        assertEquals("https://example.com/path?q=z",
                UrlNormalizer.normalize("https://example.com/path?mc_cid=x&mc_eid=y&q=z"));
    }

    @Test
    void defaultHttpsPortStripped() {
        assertEquals("https://example.com/path",
                UrlNormalizer.normalize("https://example.com:443/path"));
    }

    @Test
    void defaultHttpPortStripped() {
        assertEquals("http://example.com/path",
                UrlNormalizer.normalize("http://example.com:80/path"));
    }

    @Test
    void nonDefaultPortKept() {
        assertEquals("https://example.com:8080/path",
                UrlNormalizer.normalize("https://example.com:8080/path"));
    }

    @Test
    void utmSourceUppercaseRemoved() {
        assertEquals("https://example.com/path",
                UrlNormalizer.normalize("https://example.com/path?UTM_SOURCE=email"));
    }

    @Test
    void emptyQueryRemoved() {
        assertEquals("https://example.com/path",
                UrlNormalizer.normalize("https://example.com/path?"));
    }

    @Test
    void percentEncodedCyrillicPreserved() {
        String input = "https://example.com/%D1%82%D0%B5%D1%81%D1%82";
        assertEquals(input, UrlNormalizer.normalize(input));
    }

    @Test
    void allTrackingParamsRemovedLeaveNoQuery() {
        assertEquals("https://example.com/art",
                UrlNormalizer.normalize("https://example.com/art?utm_source=a&utm_medium=b&fbclid=c"));
    }

    // ── error cases ───────────────────────────────────────────────────────────

    @Test
    void brokenUrlThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlNormalizer.normalize("not-a-url"));
    }

    @Test
    void ftpSchemeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlNormalizer.normalize("ftp://example.com/file.txt"));
    }

    @Test
    void urlWithSpaceThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlNormalizer.normalize("https://example.com/path with space"));
    }

    @Test
    void bareCyrillicInPathEncoded() {
        // Bare non-ASCII in path must be percent-encoded as UTF-8
        assertEquals("https://example.com/%D1%82%D0%B5%D1%81%D1%82",
                UrlNormalizer.normalize("https://example.com/тест"));
    }

    @Test
    void bareCyrillicAndPreEncodedSameResult() {
        // Raw Unicode and its percent-encoded form must normalize to the same URL and hash
        String fromBare    = UrlNormalizer.normalize("https://example.com/тест");
        String fromEncoded = UrlNormalizer.normalize("https://example.com/%D1%82%D0%B5%D1%81%D1%82");
        assertEquals(fromBare, fromEncoded);
        assertEquals(UrlNormalizer.hash(fromBare), UrlNormalizer.hash(fromEncoded));
    }

    @Test
    void idnHostConvertedToPunycode() {
        String result = UrlNormalizer.normalize("http://пример.рф/");
        assertTrue(result.startsWith("http://xn--"), "IDN host must be Punycode");
        assertFalse(result.contains("пример"), "Unicode host must not remain after IDN");
    }

    // ── hash ──────────────────────────────────────────────────────────────────

    @Test
    void hashIs64HexChars() {
        String h = UrlNormalizer.hash("https://example.com/path");
        assertEquals(64, h.length());
        assertTrue(h.matches("[0-9a-f]{64}"), "hash must be lowercase hex");
    }

    @Test
    void hashIsDeterministic() {
        String url = "https://example.com/path?a=1";
        assertEquals(UrlNormalizer.hash(url), UrlNormalizer.hash(url));
    }

    @Test
    void differentUrlsDifferentHashes() {
        assertNotEquals(
                UrlNormalizer.hash("https://example.com/a"),
                UrlNormalizer.hash("https://example.com/b"));
    }
}
