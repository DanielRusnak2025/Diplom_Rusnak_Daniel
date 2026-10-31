package org.example.util;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Normalizes HTTP/HTTPS URLs and computes a SHA-256 hex hash (64 chars).
 *
 * Rules applied:
 *   - scheme and host are lowercased; host is IDN-encoded (Punycode) when needed
 *   - default ports (80/443) are removed
 *   - fragment (#…) is stripped
 *   - trailing "/" in path is removed (except root "/")
 *   - bare non-ASCII chars in path/query are percent-encoded as UTF-8 %XX sequences
 *     (already-encoded %XX sequences are preserved)
 *   - utm_* (case-insensitive), fbclid, gclid, mc_cid, mc_eid params are removed
 *   - remaining query params are sorted alphabetically
 *
 * Truly broken URLs (no scheme, spaces, illegal chars) → IllegalArgumentException.
 */
public final class UrlNormalizer {

    private static final Set<String> REMOVE_PARAMS = Set.of("fbclid", "gclid", "mc_cid", "mc_eid");

    // Splits a URL into scheme / host / port / path / query.
    // Host allows non-ASCII (IDN); path/query allow non-ASCII (will be encoded).
    // Whitespace anywhere → no match → malformed.
    private static final Pattern URL_SPLIT = Pattern.compile(
            "([a-zA-Z][a-zA-Z0-9+\\-.]*)" +   // 1: scheme
            "://" +
            "(?:[^@/]*@)?" +                    // optional userinfo (not captured)
            "([^/:?#\\[\\]\\s]+)" +             // 2: host (no whitespace / brackets)
            "(?::(\\d+))?" +                    // 3: port digits (optional)
            "(/[^?#\\s]*)?" +                   // 4: path (optional, starts with /)
            "(?:\\?([^#\\s]*))?" +              // 5: query (optional)
            "(?:#.*)?"                           // fragment (ignored)
    );

    private UrlNormalizer() {}

    public static String normalize(String rawUrl) {
        String trimmed = rawUrl.trim();
        Matcher m = URL_SPLIT.matcher(trimmed);
        if (!m.matches()) {
            // Let java.net.URI produce a meaningful error for truly malformed input
            try { new URI(trimmed); } catch (URISyntaxException e) {
                throw new IllegalArgumentException("Malformed URL: " + trimmed, e);
            }
            throw new IllegalArgumentException("Malformed URL: " + trimmed);
        }

        String scheme   = m.group(1).toLowerCase(Locale.ROOT);
        String rawHost  = m.group(2);
        String rawPort  = m.group(3);                           // null if absent
        String rawPath  = m.group(4) != null ? m.group(4) : "";
        String rawQuery = m.group(5);                           // null if absent

        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Unsupported scheme '" + scheme + "': " + trimmed);
        }
        if (rawHost.isEmpty()) {
            throw new IllegalArgumentException("No host in URL: " + trimmed);
        }

        // IDN: convert Unicode hostname to ASCII-Compatible Encoding (Punycode).
        // For already-ASCII hostnames this is a no-op.
        String host;
        try {
            host = IDN.toASCII(rawHost.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid host in URL: " + trimmed, e);
        }

        int port = rawPort != null ? Integer.parseInt(rawPort) : -1;
        boolean defaultPort = (scheme.equals("http")  && port == 80)
                           || (scheme.equals("https") && port == 443);

        // Encode bare non-ASCII chars; preserve existing %XX sequences.
        String path  = normalizePath(encodeNonAscii(rawPath));
        String query = normalizeQuery(encodeNonAscii(rawQuery));

        StringBuilder sb = new StringBuilder();
        sb.append(scheme).append("://").append(host);
        if (port != -1 && !defaultPort) sb.append(':').append(port);
        if (!path.isEmpty()) sb.append(path);
        if (query != null)   sb.append('?').append(query);
        return sb.toString();
    }

    public static String hash(String normalizedUrl) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(normalizedUrl.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) hex.append(String.format("%02x", b & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Percent-encodes bare non-ASCII code points as UTF-8 %XX sequences.
     * Already-encoded %XX sequences are preserved as-is.
     */
    private static String encodeNonAscii(String raw) {
        if (raw == null) return null;
        boolean needsEncoding = false;
        for (int i = 0; i < raw.length(); i++) {
            if (raw.charAt(i) > 0x7F) { needsEncoding = true; break; }
        }
        if (!needsEncoding) return raw;

        StringBuilder sb = new StringBuilder(raw.length() * 3);
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '%' && i + 2 < raw.length()
                    && isHexChar(raw.charAt(i + 1)) && isHexChar(raw.charAt(i + 2))) {
                // Preserve existing %XX sequence
                sb.append(raw, i, i + 3);
                i += 3;
            } else if (c <= 0x7F) {
                sb.append(c);
                i++;
            } else {
                // Encode non-ASCII code point as UTF-8 %XX bytes (uppercase hex)
                int cp = raw.codePointAt(i);
                for (byte b : new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8)) {
                    sb.append(String.format("%%%02X", b & 0xFF));
                }
                i += Character.charCount(cp);
            }
        }
        return sb.toString();
    }

    private static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static String normalizePath(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        if (raw.equals("/")) return "/";
        return raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
    }

    private static String normalizeQuery(String raw) {
        if (raw == null || raw.isEmpty()) return null;

        String result = Arrays.stream(raw.split("&"))
                .filter(p -> !p.isEmpty())
                .filter(p -> {
                    String key = p.contains("=") ? p.substring(0, p.indexOf('=')) : p;
                    try {
                        String decoded = URLDecoder.decode(key, StandardCharsets.UTF_8)
                                .toLowerCase(Locale.ROOT);
                        return !decoded.startsWith("utm_") && !REMOVE_PARAMS.contains(decoded);
                    } catch (IllegalArgumentException e) {
                        return true;
                    }
                })
                .sorted()
                .collect(Collectors.joining("&"));

        return result.isEmpty() ? null : result;
    }
}
