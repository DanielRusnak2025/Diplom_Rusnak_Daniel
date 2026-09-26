package org.example.ai;

import org.example.email.EmailMessage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Извлекает все ссылки из письма:
 * 1. Видимые URL из plain text тела
 * 2. Скрытые ссылки из href атрибутов HTML
 */
public class LinkExtractor {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[^\\s<>\"',;)(\\[\\]{}]+",
            Pattern.CASE_INSENSITIVE
    );

    public List<String> extractLinks(EmailMessage email) {
        Set<String> links = new LinkedHashSet<>();

        if (email.getBody() != null) {
            extractFromText(email.getBody(), links);
        }

        if (email.getHtmlBody() != null) {
            extractFromHtml(email.getHtmlBody(), links);
        }

        return new ArrayList<>(links);
    }

    private void extractFromText(String text, Set<String> links) {
        Matcher m = URL_PATTERN.matcher(text);
        while (m.find()) {
            String url = m.group().replaceAll("[)>\"',;.\\]]+$", "");
            links.add(url);
        }
    }

    private void extractFromHtml(String html, Set<String> links) {
        try {
            Document doc = Jsoup.parse(html);
            for (Element a : doc.select("a[href]")) {
                String href = a.attr("href");
                if (href.startsWith("http")) {
                    links.add(href);
                }
            }
        } catch (Exception e) {
            System.err.println("LinkExtractor: ошибка парсинга HTML: " + e.getMessage());
        }
    }
}
