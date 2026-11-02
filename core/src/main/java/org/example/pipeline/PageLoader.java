package org.example.pipeline;

public interface PageLoader {

    record Result(ParseResult parsed, String finalUrl) {}

    /**
     * Fetches and parses the page at the given URL.
     * Returns {@code null} if the page could not be fetched.
     */
    Result load(String url);
}
