package org.example.ai;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Фильтрует ссылки из письма — убирает очевидно нерелевантные.
 * Мягкий подход: лучше пропустить лишнее и дать AI отфильтровать,
 * чем заблокировать настоящие статьи.
 */
public class LinkFilter {

    // Максимум URL одного домена из одного письма — навигационные ссылки повторяются
    private static final int MAX_PER_DOMAIN = 3;

    // Максимум URL на одно письмо — ограничение нагрузки на AI
    private static final int MAX_PER_EMAIL = 25;

    // Домены за пейволлом — контент недоступен без подписки
    private static final Set<String> PAYWALL_DOMAINS = Set.of(
            "haaretz.com"
    );

    // Домены социальных сетей и инфраструктуры рассылок
    private static final List<String> BLOCKED_DOMAINS = List.of(
            "twitter.com", "x.com", "facebook.com", "linkedin.com",
            "instagram.com", "youtube.com", "t.co", "vk.com", "tiktok.com",
            "accounts.google.com", "mail.google.com", "security.google.com",
            "sendgrid.net", "mandrillapp.com", "constantcontact.com",
            "campaignmonitor.com", "mailgun.org", "klaviyo.com",
            "play.google.com", "apps.apple.com",
            "whatsapp.com", "telegram.org", "t.me"
    );

    // Пути служебных страниц
    private static final List<String> BLOCKED_PATH_SEGMENTS = List.of(
            "/unsubscribe", "/unsub", "/optout", "/opt-out", "/opt_out",
            "/privacy", "/terms", "/tos", "/cookie", "/gdpr",
            "/login", "/signin", "/signup", "/register",
            "/account", "/settings", "/preferences",
            "/share", "/donate"
    );

    // Расширения файлов — точно не статьи
    private static final List<String> BLOCKED_EXTENSIONS = List.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg",
            ".pdf", ".doc", ".docx", ".xls", ".xlsx",
            ".mp4", ".mp3", ".avi", ".mov", ".webm",
            ".zip", ".rar", ".tar", ".gz",
            ".css", ".js", ".ico", ".woff", ".woff2"
    );

    /**
     * Фильтрует список URL и ограничивает количество для одного письма.
     */
    public List<String> filter(List<String> urls) {
        // Сначала базовая фильтрация
        List<String> filtered = urls.stream()
                .filter(this::isLikelyArticle)
                .distinct()
                .collect(Collectors.toList());

        // Ограничиваем по доменам: не более MAX_PER_DOMAIN ссылок с одного домена
        Map<String, Integer> domainCount = new HashMap<>();
        List<String> limited = new ArrayList<>();

        for (String url : filtered) {
            String domain = extractDomain(url);
            if (domain == null) continue;

            int count = domainCount.getOrDefault(domain, 0);
            if (count < MAX_PER_DOMAIN) {
                limited.add(url);
                domainCount.put(domain, count + 1);
            }
        }

        // Глобальный лимит на письмо
        if (limited.size() > MAX_PER_EMAIL) {
            limited = limited.subList(0, MAX_PER_EMAIL);
        }

        return limited;
    }

    public boolean isLikelyArticle(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            String path = uri.getPath();

            if (host == null) return false;

            String lowerHost = host.toLowerCase();

            // Блокируем пейволл-домены
            for (String paywall : PAYWALL_DOMAINS) {
                if (lowerHost.equals(paywall) || lowerHost.endsWith("." + paywall)) return false;
            }

            // Блокируем по доменам
            for (String blocked : BLOCKED_DOMAINS) {
                if (lowerHost.contains(blocked)) return false;
            }

            if (path != null) {
                String lowerPath = path.toLowerCase();

                // Блокируем служебные пути
                for (String segment : BLOCKED_PATH_SEGMENTS) {
                    if (lowerPath.contains(segment)) return false;
                }

                // Блокируем файлы (изображения, документы, медиа)
                for (String ext : BLOCKED_EXTENSIONS) {
                    if (lowerPath.endsWith(ext)) return false;
                }
            }

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    private String extractDomain(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null) return null;
            // Убираем www. для группировки
            return host.toLowerCase().replaceFirst("^www\\.", "");
        } catch (Exception e) {
            return null;
        }
    }
}
