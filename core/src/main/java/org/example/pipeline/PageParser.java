package org.example.pipeline;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class PageParser {

    private static final int TEXT_LIMIT = 2000;

    private PageParser() {}

    private record TextResult(String text, String source) {}

    public static ParseResult parse(String html, String finalUrl) {
        Document doc = Jsoup.parse(html, finalUrl);

        String title         = extractTitle(doc);
        String publisher     = extractPublisher(doc, finalUrl);
        String publishedDate = extractPublishedDate(doc);
        String canonical     = extractCanonical(doc, finalUrl);
        String metaDesc      = extractMetaDescription(doc);
        String ogType        = extractOgType(doc);
        TextResult tr        = extractBodyText(doc);

        String source = tr.text().isEmpty() && metaDesc != null && !metaDesc.isEmpty()
                ? "meta-only" : tr.source();
        String combined = buildTextForAi(finalUrl, title, metaDesc, tr.text());
        int textChars = computeTextChars(title, metaDesc, tr.text());
        return new ParseResult(title, publisher, publishedDate, canonical,
                combined, textChars, source, ogType);
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

    private static String extractMetaDescription(Document doc) {
        String og = doc.select("meta[property=og:description]").attr("content").trim();
        if (!og.isEmpty()) return og;
        String desc = doc.select("meta[name=description]").attr("content").trim();
        return desc.isEmpty() ? null : desc;
    }

    private static String extractOgType(Document doc) {
        String val = doc.select("meta[property=og:type]").attr("content").trim();
        return val.isEmpty() ? null : val;
    }

    private static TextResult extractBodyText(Document doc) {
        Document work = doc.clone();
        work.select("nav, header, footer, aside, script, style").remove();

        Element article = work.selectFirst("article");
        if (article != null) {
            String text = joinParagraphs(article);
            if (!text.isEmpty()) return new TextResult(text, "article");
        }

        Element best = findBestPContainer(work);
        if (best != null) {
            String text = joinDirectParagraphs(best);
            if (!text.isEmpty()) return new TextResult(text, "p-container");
        }

        Element body = work.body();
        String text = body != null ? body.text().trim() : "";
        return new TextResult(text, "fallback");
    }

    private static String joinParagraphs(Element el) {
        return el.select("p").stream()
                .map(p -> p.text().trim())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" "));
    }

    private static String joinDirectParagraphs(Element el) {
        return el.children().stream()
                .filter(c -> c.tagName().equals("p"))
                .map(p -> p.text().trim())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" "));
    }

    private static Element findBestPContainer(Document work) {
        Element best = null;
        int bestLen = 0;
        for (Element el : work.getAllElements()) {
            String tag = el.tagName();
            if (tag.equals("html") || tag.equals("head") || tag.equals("body") || tag.equals("p"))
                continue;
            int total = el.children().stream()
                    .filter(c -> c.tagName().equals("p"))
                    .mapToInt(p -> p.text().length())
                    .sum();
            if (total > bestLen) {
                bestLen = total;
                best = el;
            }
        }
        return bestLen > 0 ? best : null;
    }

    private static int computeTextChars(String title, String metaDesc, String body) {
        List<String> parts = new ArrayList<>();
        if (title != null && !title.isEmpty()) parts.add(title);
        if (metaDesc != null && !metaDesc.isEmpty()) parts.add(metaDesc);
        if (body != null && !body.isEmpty()) parts.add(body);
        return String.join("\n", parts).length();
    }

    private static String buildTextForAi(String finalUrl, String title, String metaDesc, String body) {
        List<String> parts = new ArrayList<>();
        if (finalUrl != null && !finalUrl.isEmpty()) parts.add("URL: " + finalUrl);
        if (title != null && !title.isEmpty()) parts.add(title);
        if (metaDesc != null && !metaDesc.isEmpty()) parts.add(metaDesc);
        if (body != null && !body.isEmpty()) parts.add(body);
        String combined = String.join("\n", parts);
        return combined.length() > TEXT_LIMIT ? combined.substring(0, TEXT_LIMIT) : combined;
    }
}
