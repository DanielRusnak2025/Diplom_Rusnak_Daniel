package org.example.pipeline;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Logger;

public final class ArticlePreFilter {

    private static final Logger LOG = Logger.getLogger(ArticlePreFilter.class.getName());
    static final int MIN_TEXT_CHARS = 200;

    private ArticlePreFilter() {}

    /**
     * Checks whether the article should be skipped before AI classification.
     * @return null if the article should be classified normally; a skip-reason string otherwise
     */
    public static String check(ParseResult parsed, String finalUrl) {
        String path = extractPath(finalUrl);
        if (path.isEmpty() || path.equals("/")) {
            return "root-url";
        }

        String title = parsed.title() != null ? parsed.title() : "";
        if (title.contains("404") || title.toLowerCase().contains("page not found")) {
            return "not-found";
        }

        String ogType = parsed.ogType();
        if (ogType != null && !ogType.equalsIgnoreCase("article")) {
            LOG.info("[PreFilter] og:type=\"" + ogType + "\" (not \"article\") — classifying anyway: " + finalUrl);
        }

        if (parsed.textChars() < MIN_TEXT_CHARS) {
            return "too-short";
        }

        return null;
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
}
