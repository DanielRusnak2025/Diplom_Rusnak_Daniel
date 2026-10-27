package org.example.scheduler;

import org.example.Tag;
import org.example.ai.ArticleExtractor;
import org.example.ai.ArticleSaver;
import org.example.ai.ExtractedArticle;
import org.example.ai.LinkExtractor;
import org.example.ai.LinkFilter;
import org.example.dao.impl.TagDAOImpl;
import org.example.email.EmailConfig;
import org.example.email.EmailMessage;
import org.example.email.EmailReader;
import org.example.service.ArticleService;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Запускает pipeline по таймеру:
 * Gmail → извлечь все ссылки → отфильтровать → AI параллельно → БД
 */
public class EmailScheduler {

    // Читаем из email.properties (с дефолтами на случай старого файла без этих ключей)
    private final int intervalMinutes;
    private final int parallelRequests;
    private final int cleanupDays;
    private final int cleanupMaxReads;

    // Системные письма — фильтруем при чтении
    private static final List<String> SYSTEM_DOMAINS = List.of(
            "accounts.google.com", "security.google.com", "mail.google.com",
            "facebook.com", "twitter.com", "linkedin.com", "github.com"
    );
    private static final List<String> SYSTEM_SUBJECTS = List.of(
            "оповещение безопасности", "security alert", "verify your",
            "подтвердите", "password reset", "unsubscribe"
    );

    private final EmailReader             emailReader;
    private final LinkExtractor           linkExtractor;
    private final LinkFilter              linkFilter;
    private final ArticleExtractor        extractor;
    private final ArticleSaver            saver;
    private final TagDAOImpl              tagDAO;
    private final ArticleService          articleService;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService          articleExecutor;

    public EmailScheduler() {
        this.intervalMinutes  = EmailConfig.getSchedulerIntervalMinutes();
        this.parallelRequests = EmailConfig.getParallelRequests();
        this.cleanupDays      = EmailConfig.getCleanupDays();
        this.cleanupMaxReads  = EmailConfig.getCleanupMaxReads();

        this.emailReader    = new EmailReader(EmailConfig.getEmail(), EmailConfig.getAppPassword());
        this.linkExtractor  = new LinkExtractor();
        this.linkFilter     = new LinkFilter();
        this.extractor      = new ArticleExtractor();
        this.saver          = new ArticleSaver();
        this.tagDAO         = new TagDAOImpl();
        this.articleService = new ArticleService();
        this.scheduler      = Executors.newSingleThreadScheduledExecutor();
        this.articleExecutor = Executors.newFixedThreadPool(parallelRequests);
    }

    public void start() {
        System.out.println("EmailScheduler запущен. Интервал: " + intervalMinutes
                + " мин, параллельность: " + parallelRequests);
        scheduler.scheduleAtFixedRate(
                this::runPipeline,
                0,
                intervalMinutes,
                TimeUnit.MINUTES
        );
    }

    public void stop() {
        scheduler.shutdown();
        articleExecutor.shutdown();
        System.out.println("EmailScheduler остановлен");
    }

    private boolean isSystemEmail(EmailMessage email) {
        String from    = email.getFrom().toLowerCase();
        String subject = email.getSubject() != null ? email.getSubject().toLowerCase() : "";

        for (String domain  : SYSTEM_DOMAINS)  if (from.contains(domain))    return true;
        for (String keyword : SYSTEM_SUBJECTS) if (subject.contains(keyword)) return true;

        return false;
    }

    private void runPipeline() {
        System.out.println("\n=== Запуск pipeline: " + java.time.LocalDateTime.now() + " ===");

        try {
            if (EmailConfig.isCleanupEnabled()) {
                int cleaned = articleService.deleteOldArticles(cleanupDays, cleanupMaxReads);
                if (cleaned > 0) {
                    System.out.println("🗑 Автоочистка: удалено " + cleaned
                            + " статей старше " + cleanupDays + " дней с readCount <= " + cleanupMaxReads);
                }
            }

            if (!extractor.isOllamaAvailable()) {
                System.err.println("❌ Ollama недоступна — pipeline пропущен. Запустите: ollama serve");
                return;
            }
            System.out.println("✅ Ollama доступна");

            List<String> existingTagNames = tagDAO.findAll().stream()
                    .map(Tag::getName)
                    .collect(Collectors.toList());
            System.out.println("Тегов в БД: " + existingTagNames.size());

            AtomicInteger totalSaved   = new AtomicInteger(0);
            AtomicInteger totalSkipped = new AtomicInteger(0);

            int totalEmails = emailReader.readAndProcess(email -> {
                if (isSystemEmail(email)) {
                    System.out.println("  Пропускаем системное письмо: " + email.getSubject());
                    totalSkipped.incrementAndGet();
                    return;
                }

                List<String> allLinks     = linkExtractor.extractLinks(email);
                List<String> articleLinks = linkFilter.filter(allLinks);
                System.out.println("  Ссылок: " + allLinks.size() + " → после фильтра: " + articleLinks.size());

                if (articleLinks.isEmpty()) {
                    System.out.println("  Нет статей — пропускаем письмо");
                    return;
                }

                AtomicInteger savedCount   = new AtomicInteger(0);
                AtomicInteger skippedCount = new AtomicInteger(0);

                List<CompletableFuture<Void>> futures = articleLinks.stream()
                        .map(url -> CompletableFuture.runAsync(
                                () -> processUrl(url, existingTagNames, savedCount, skippedCount),
                                articleExecutor))
                        .collect(Collectors.toList());

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                System.out.println("  ✅ Письмо обработано: сохранено " + savedCount.get()
                        + ", пропущено " + skippedCount.get());

                totalSaved.addAndGet(savedCount.get());
                totalSkipped.addAndGet(skippedCount.get());
            });

            if (totalEmails < 0) {
                System.err.println("❌ Не удалось подключиться к Gmail — pipeline прерван");
                return;
            }
            if (totalEmails == 0) {
                System.out.println("Новых писем нет");
                return;
            }

            System.out.println("\n=== Итог: писем " + totalEmails
                    + ", сохранено " + totalSaved.get()
                    + ", пропущено " + totalSkipped.get() + " ===");

        } catch (Throwable e) {
            // Ловим Throwable (не только Exception) — ExceptionInInitializerError тоже Error
            System.err.println("Критическая ошибка pipeline: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void processUrl(String url, List<String> existingTagNames,
                            AtomicInteger savedCount, AtomicInteger skippedCount) {
        String thread = Thread.currentThread().getName();
        System.out.println("  [" + thread + "] → " + url);
        try {
            ExtractedArticle extracted = extractor.extract(url, existingTagNames);

            if (extracted == null) {
                System.out.println("  [" + thread + "] пропущен (не статья или редирект)");
                skippedCount.incrementAndGet();
                return;
            }

            System.out.println("  [" + thread + "] AI: \"" + extracted.getTitle() + "\"");
            boolean ok = saver.save(extracted);
            if (ok) savedCount.incrementAndGet(); else skippedCount.incrementAndGet();

        } catch (Exception e) {
            System.err.println("  [" + thread + "] ошибка [" + url + "]: " + e.getMessage());
            skippedCount.incrementAndGet();
        }
    }
}
