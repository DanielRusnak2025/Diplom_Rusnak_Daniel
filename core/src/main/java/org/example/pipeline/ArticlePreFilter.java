package org.example.pipeline;

import org.example.util.UrlNormalizer;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

public final class ArticlePreFilter {

    static final int MIN_TEXT_CHARS = 200;

    private ArticlePreFilter() {}

    /**
     * Checks whether the article should be skipped before AI classification.
     * @param httpStatus HTTP status returned by the server (0 if unknown)
     * @return null if the article should be classified normally; a skip-reason string otherwise
     */
    public static String check(ParseResult parsed, String finalUrl, int httpStatus) {
        return check(parsed, finalUrl, httpStatus, Set.of());
    }

    /**
     * Checks whether the article should be skipped before AI classification.
     * @param newsletterHosts suffix-match list of newsletter hosts to block
     * @return null if the article should be classified normally; a skip-reason string otherwise
     */
    public static String check(ParseResult parsed, String finalUrl, int httpStatus,
                                Set<String> newsletterHosts) {
        if (!newsletterHosts.isEmpty() && matchesNewsletterHost(finalUrl, newsletterHosts)) {
            return "newsletter-host";
        }

        String path = extractPath(finalUrl);
        if (path.isEmpty() || path.equals("/")) {
            // Allow URLs like "/?id=123" — non-tracking query params indicate a real article page.
            if (!hasNonTrackingQuery(finalUrl)) return "root-url";
        }

        if (httpStatus == 404 || httpStatus == 410) {
            return "not-found";
        }

        String title = parsed.title() != null ? parsed.title() : "";
        String titleLower = title.toLowerCase();
        // Narrow 404 title pattern: matches "404", "404 Not Found", "404 - Page Not Found",
        // "404 Error", etc. — avoids false positives like "404 migrants rescued".
        if (title.matches("(?i)\\s*404\\s*(?:[-|/:]+\\s*)?(?:not\\s+found|error|page\\b.*)?\\s*")
                || titleLower.contains("not found")) {
            return "not-found";
        }

        String ogType = parsed.ogType();
        if (ogType != null && !ogType.equalsIgnoreCase("article")) {
            return "og-type";
        }

        if (parsed.textChars() < MIN_TEXT_CHARS) {
            return "too-short";
        }

        return null;
    }

    private static boolean matchesNewsletterHost(String finalUrl, Set<String> newsletterHosts) {
        try {
            String host = new URI(finalUrl).getHost();
            if (host == null) return false;
            host = host.toLowerCase();
            for (String suffix : newsletterHosts) {
                if (host.equals(suffix) || host.endsWith("." + suffix)) return true;
            }
        } catch (URISyntaxException ignored) {}
        return false;
    }

    private static String extractPath(String url) {
        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            return path != null ? path : "";
        } catch (URISyntaxException e) {
            return "";
        }
    }

    /** Returns true if the URL has any non-tracking query params after normalization. */
    private static boolean hasNonTrackingQuery(String url) {
        try {
            String normalized = UrlNormalizer.normalize(url);
            return normalized.contains("?");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
