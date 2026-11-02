package org.example.pipeline;

import org.jsoup.Connection;
import org.jsoup.Jsoup;

import java.util.logging.Logger;

public class JsoupPageLoader implements PageLoader {

    private static final Logger log = Logger.getLogger(JsoupPageLoader.class.getName());
    private static final int    TIMEOUT_MS = 15_000;
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; NewsDove/1.0)";

    @Override
    public Result load(String url) {
        try {
            Connection.Response response = Jsoup.connect(url)
                    .timeout(TIMEOUT_MS)
                    .userAgent(USER_AGENT)
                    .followRedirects(true)
                    .execute();
            String finalUrl = response.url().toString();
            String html     = response.body();
            ParseResult parsed = PageParser.parse(html, finalUrl);
            return new Result(parsed, finalUrl);
        } catch (Exception e) {
            log.warning("JsoupPageLoader: fetch failed for " + url + " — " + e.getMessage());
            return null;
        }
    }
}
