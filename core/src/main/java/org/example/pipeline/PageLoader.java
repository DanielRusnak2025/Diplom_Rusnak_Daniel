package org.example.pipeline;

public interface PageLoader {

    class SsrfViolationException extends Exception {
        public SsrfViolationException(String msg) { super(msg); }
    }

    record Result(ParseResult parsed, String finalUrl, int httpStatus) {}

    /**
     * Fetches and parses the page at the given URL.
     * Returns {@code null} if the page could not be fetched (FETCH_FAILED).
     * Throws {@link SsrfViolationException} if the URL is blocked by SSRF policy (INVALID_URL).
     */
    Result load(String url) throws SsrfViolationException;
}
