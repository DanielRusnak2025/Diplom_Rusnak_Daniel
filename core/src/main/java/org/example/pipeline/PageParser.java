package org.example.pipeline;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.net.URISyntaxException;

public final class PageParser {

    private static final int TEXT_LIMIT = 2000;

    private PageParser() {}

    public static ParseResult parse(String html, String finalUrl) {
        Document doc = Jsoup.parse(html, finalUrl);

        String title = extractTitle(doc);
        String publisher = extractPublisher(doc, finalUrl);
        String publishedDate = extractPublishedDate(doc);
        String canonical = extractCanonical(doc, finalUrl);
        String textForAi = extractTextForAi(doc);

        return new ParseResult(title, publisher, publishedDate, canonical, textForAi);
    }

    private static String extractTitle(Document doc) {
        String og = doc.select("meta[property=og:title]").attr("content").trim();
        if (!og.isEmpty()) return og;
        String title = doc.title().trim();
        return title.isEmpty() ? null : title;
    }

    private static String extractPublisher(Document doc, String finalUrl) {
        String og = doc.select("meta[property=og:site_name]").attr("content").trim();
        if (!og.isEmpty()) return og;
        try {
            String host = new URI(finalUrl).getHost();
            if (host == null) return null;
            // Strip www. prefix for a readable domain
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static String extractPublishedDate(Document doc) {
        String date = doc.select("meta[property=article:published_time]").attr("content").trim();
        return date.isEmpty() ? null : date;
    }

    private static String extractCanonical(Document doc, String finalUrl) {
        String href = doc.select("link[rel=canonical]").attr("href").trim();
        return href.isEmpty() ? finalUrl : href;
    }

    private static String extractTextForAi(Document doc) {
        // Work on a clone to avoid mutating the parsed document
        Document work = doc.clone();
        work.select("nav, header, footer, aside, script, style").remove();

        // Prefer <article> element
        Element article = work.selectFirst("article");
        if (article != null) {
            String text = article.text().trim();
            if (!text.isEmpty()) return truncate(text);
        }

        // Fall back to the largest <p> element by text length
        Elements paragraphs = work.select("p");
        Element largest = null;
        int maxLen = 0;
        for (Element p : paragraphs) {
            int len = p.text().length();
            if (len > maxLen) { maxLen = len; largest = p; }
        }
        if (largest != null && maxLen > 0) {
            return truncate(largest.text().trim());
        }

        // Last resort: body text
        Element body = work.body();
        return body != null ? truncate(body.text().trim()) : "";
    }

    private static String truncate(String text) {
        return text.length() > TEXT_LIMIT ? text.substring(0, TEXT_LIMIT) : text;
    }
}
