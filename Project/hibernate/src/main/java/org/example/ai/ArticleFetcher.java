package org.example.ai;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * Скачивает текст статьи по URL.
 * Возвращает и текст, и финальный URL после всех редиректов.
 */
public class ArticleFetcher {

    private static final int    TIMEOUT_MS = 10_000;
    private static final int    MAX_LENGTH  = 3000;
    private static final String USER_AGENT  =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    /**
     * Результат запроса: текст статьи + финальный URL после редиректов.
     */
    public static class FetchResult {
        public final String text;
        public final String finalUrl;

        public FetchResult(String text, String finalUrl) {
            this.text     = text;
            this.finalUrl = finalUrl;
        }
    }

    /**
     * Скачивает страницу, возвращает текст и финальный URL.
     * Возвращает null если не удалось скачать.
     */
    public FetchResult fetchArticle(String url) {
        if (url == null || url.isBlank()) return null;

        try {
            Connection.Response response = Jsoup.connect(url)
                    .timeout(TIMEOUT_MS)
                    .userAgent(USER_AGENT)
                    .followRedirects(true)
                    .execute();

            String finalUrl = response.url().toString();
            Document doc    = response.parse();

            String text;
            if (!doc.select("article").isEmpty()) {
                text = doc.select("article").first().text();
            } else if (!doc.select("main").isEmpty()) {
                text = doc.select("main").first().text();
            } else {
                text = doc.body() != null ? doc.body().text() : "";
            }

            if (text.length() > MAX_LENGTH) {
                text = text.substring(0, MAX_LENGTH);
            }

            System.out.println("Скачана статья: " + finalUrl + " (" + text.length() + " символов)"
                    + (!finalUrl.equals(url) ? " [редирект от: " + url + "]" : ""));

            return new FetchResult(text, finalUrl);

        } catch (Exception e) {
            System.err.println("Не удалось скачать статью: " + url + " — " + e.getMessage());
            return null;
        }
    }

    @Deprecated
    public String fetchArticleText(String url) {
        FetchResult r = fetchArticle(url);
        return r != null ? r.text : null;
    }
}
