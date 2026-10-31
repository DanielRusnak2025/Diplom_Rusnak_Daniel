package org.example.pipeline;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Fetches HTML pages with SSRF protection, redirect following, and size limits.
 *
 * Production use: {@link #PageFetcher(int, int, int)} — blocks loopback/private IPs, ports 80/443 only.
 * Tests use the package-private constructor with {@code allowLoopback=true}.
 */
public class PageFetcher {

    public static class SsrfException extends IOException {
        SsrfException(String msg) { super(msg); }
    }

    public static class NonHtmlException extends IOException {
        NonHtmlException(String ct) { super("Non-HTML content type: " + ct); }
    }

    public static class ContentTooLargeException extends IOException {
        ContentTooLargeException(int limit) { super("Response exceeds " + limit + " bytes"); }
    }

    public static class TooManyRedirectsException extends IOException {
        TooManyRedirectsException(int max) { super("Too many redirects (> " + max + ")"); }
    }

    private final OkHttpClient client;
    private final int maxBytes;
    private final int maxRedirects;
    private final boolean allowLoopback;
    private final Set<Integer> allowedPorts;

    /** Production: blocks loopback/private IPs, restricts ports to 80/443. */
    public PageFetcher(int timeoutSeconds, int maxBytes, int maxRedirects) {
        this(timeoutSeconds, maxBytes, maxRedirects, false, Set.of(80, 443));
    }

    /** Package-private for tests. {@code allowedPorts=null} skips port check. */
    PageFetcher(int timeoutSeconds, int maxBytes, int maxRedirects,
                boolean allowLoopback, Set<Integer> allowedPorts) {
        this.allowLoopback = allowLoopback;
        this.allowedPorts = allowedPorts;
        this.maxBytes = maxBytes;
        this.maxRedirects = maxRedirects;
        this.client = new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();
    }

    public FetchResult fetch(String rawUrl) throws IOException {
        URL initial = parseUrl(rawUrl);
        validateSchemeAndPort(initial);
        checkSsrf(initial);

        String currentUrl = rawUrl;
        int redirectCount = 0;

        while (true) {
            Request request = new Request.Builder().url(currentUrl).get().build();
            try (Response response = client.newCall(request).execute()) {
                int code = response.code();
                if (code >= 300 && code < 400) {
                    if (redirectCount >= maxRedirects) throw new TooManyRedirectsException(maxRedirects);
                    String location = response.header("Location");
                    if (location == null) throw new IOException("Redirect with no Location header");
                    URL next = resolveUrl(currentUrl, location);
                    validateSchemeAndPort(next);
                    checkSsrf(next);
                    currentUrl = next.toString();
                    redirectCount++;
                    continue;
                }
                if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
                String ct = response.header("Content-Type", "");
                if (!ct.startsWith("text/html")) throw new NonHtmlException(ct);
                ResponseBody body = response.body();
                if (body == null) throw new IOException("Empty response body");
                String html = readWithLimit(body, maxBytes);
                return new FetchResult(currentUrl, html);
            }
        }
    }

    private void validateSchemeAndPort(URL url) throws SsrfException {
        String scheme = url.getProtocol();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new SsrfException("Unsupported scheme: " + scheme);
        }
        if (allowedPorts != null) {
            int port = url.getPort();
            int effective = (port == -1) ? ("https".equals(scheme) ? 443 : 80) : port;
            if (!allowedPorts.contains(effective)) {
                throw new SsrfException("Blocked port: " + effective);
            }
        }
    }

    private void checkSsrf(URL url) throws SsrfException {
        InetAddress[] addrs;
        try {
            addrs = InetAddress.getAllByName(url.getHost());
        } catch (UnknownHostException e) {
            return; // real DNS failure — let OkHttp handle and report it
        }
        for (InetAddress addr : addrs) {
            if (isBlockedAddress(addr)) {
                throw new SsrfException("SSRF blocked: " + addr.getHostAddress());
            }
        }
    }

    private boolean isBlockedAddress(InetAddress addr) {
        if (!allowLoopback && addr.isLoopbackAddress()) return true;  // 127/8, ::1
        if (addr.isSiteLocalAddress()) return true;                    // 10/8, 172.16/12, 192.168/16
        if (addr.isLinkLocalAddress()) return true;                    // 169.254/16, fe80::/10
        if (addr.isAnyLocalAddress()) return true;                     // 0.0.0.0
        byte[] raw = addr.getAddress();
        if (raw.length == 4 && raw[0] == 0) return true;              // 0.0.0.0/8
        if (raw.length == 16 && (raw[0] & 0xFE) == 0xFC) return true; // fc00::/7 unique local IPv6
        return false;
    }

    private static URL parseUrl(String url) throws IOException {
        try {
            return new URI(url).toURL();
        } catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
            throw new IOException("Malformed URL: " + url, e);
        }
    }

    private static URL resolveUrl(String base, String location) throws IOException {
        try {
            URI resolved = new URI(base).resolve(location);
            return resolved.toURL();
        } catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
            throw new IOException("Invalid redirect URL: " + location, e);
        }
    }

    private static String readWithLimit(ResponseBody body, int maxBytes) throws IOException {
        try (InputStream is = body.byteStream()) {
            byte[] buf = new byte[8192];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int total = 0, read;
            while ((read = is.read(buf)) != -1) {
                total += read;
                if (total > maxBytes) throw new ContentTooLargeException(maxBytes);
                baos.write(buf, 0, read);
            }
            MediaType mt = body.contentType();
            Charset charset = (mt != null && mt.charset() != null) ? mt.charset() : StandardCharsets.UTF_8;
            return baos.toString(charset);
        }
    }

    public void close() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
