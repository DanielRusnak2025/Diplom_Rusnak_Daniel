package org.example.scheduler;

import org.example.ai.LinkExtractor;
import org.example.ai.LinkFilter;
import org.example.email.EmailConfig;
import org.example.email.EmailMessage;
import org.example.email.EmailReader;
import org.example.pipeline.ArticleQueueEnqueuer;
import org.example.pipeline.ProcessedEmailLog;
import org.example.service.ArticleService;
import org.example.util.HibernateUtil;
import org.example.util.UrlNormalizer;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Запускает pipeline по таймеру:
 * Gmail → извлечь ссылки → отфильтровать → article_queue (AI-обработка идёт отдельно через --process-queue)
 *
 * Each email is processed in a single transaction:
 *   1. INSERT processed_email (placeholder with links_saved = 0)
 *   2. INSERT article_queue rows with that email_id
 *   3. UPDATE processed_email.links_saved = actual inserted count
 * On any failure the whole transaction is rolled back; the email will be retried on the next run.
 */
public class EmailScheduler {

    private final int intervalMinutes;
    private final int cleanupDays;

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
    private final ArticleQueueEnqueuer    enqueuer;
    private final ProcessedEmailLog       processedLog;
    private final ArticleService          articleService;
    private final ScheduledExecutorService scheduler;

    public EmailScheduler() {
        this.intervalMinutes = EmailConfig.getSchedulerIntervalMinutes();
        this.cleanupDays     = EmailConfig.getCleanupDays();

        this.emailReader  = new EmailReader(EmailConfig.getEmail(), EmailConfig.getAppPassword());
        this.linkExtractor = new LinkExtractor();
        this.linkFilter    = new LinkFilter();
        this.enqueuer      = new ArticleQueueEnqueuer();
        this.processedLog  = new ProcessedEmailLog();
        this.articleService = new ArticleService();
        this.scheduler     = Executors.newSingleThreadScheduledExecutor();
    }

    public void start() {
        System.out.println("EmailScheduler запущен. Интервал: " + intervalMinutes + " мин");
        scheduler.scheduleAtFixedRate(
                this::runPipeline,
                0,
                intervalMinutes,
                TimeUnit.MINUTES
        );
    }

    public void stop() {
        scheduler.shutdown();
        System.out.println("EmailScheduler остановлен");
    }

    /**
     * Computes a stable hash for email deduplication.
     * Uses Message-ID header when present; falls back to SHA-256 of (from|receivedDateISO|subject).
     */
    public static String computeMessageHash(EmailMessage email) {
        String mid = email.getMessageId();
        if (mid != null && !mid.isBlank()) {
            return UrlNormalizer.hash(mid);
        }
        String receivedIso = email.getReceivedDate() != null
                ? email.getReceivedDate().toInstant().toString() : "null";
        String subject = email.getSubject() != null ? email.getSubject() : "";
        return UrlNormalizer.hash(email.getFrom() + "|" + receivedIso + "|" + subject);
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
                int cleaned = articleService.deleteOldArticles(cleanupDays);
                if (cleaned > 0) {
                    System.out.println("Автоочистка: удалено " + cleaned
                            + " статей старше " + cleanupDays + " дней");
                }
            }

            AtomicInteger totalEnqueued = new AtomicInteger(0);
            AtomicInteger totalSkipped  = new AtomicInteger(0);

            int totalEmails = emailReader.readAndProcess(email -> {
                if (isSystemEmail(email)) {
                    System.out.println("  Пропускаем системное письмо: " + email.getSubject());
                    totalSkipped.incrementAndGet();
                    return;
                }

                String hash = computeMessageHash(email);
                if (processedLog.isAlreadyProcessed(hash)) {
                    System.out.println("  Письмо уже обработано (пропуск): " + email.getSubject());
                    totalSkipped.incrementAndGet();
                    return;
                }

                List<String> allLinks     = linkExtractor.extractLinks(email);
                List<String> articleLinks = linkFilter.filter(allLinks);
                System.out.println("  Ссылок: " + allLinks.size()
                        + " → после фильтра: " + articleLinks.size());

                if (articleLinks.isEmpty()) {
                    System.out.println("  Нет статей — пропускаем письмо");
                    return;
                }

                String domain = extractSenderDomain(email.getFrom());

                try (Session s = HibernateUtil.getSessionFactory().openSession()) {
                    Transaction tx = s.beginTransaction();
                    try {
                        Integer emailId = processedLog.insertPlaceholder(
                                hash, domain, email.getReceivedDate(),
                                allLinks.size(), allLinks.size() - articleLinks.size(), s);
                        int inserted = enqueuer.enqueue(articleLinks, emailId, s);
                        processedLog.updateSavedCount(emailId, inserted, s);
                        tx.commit();
                        System.out.println("  Добавлено в очередь: " + inserted + " новых URL");
                        totalEnqueued.addAndGet(inserted);
                    } catch (Exception e) {
                        tx.rollback();
                        System.err.println("  Ошибка обработки письма '"
                                + email.getSubject() + "': " + e.getMessage()
                                + " — будет повторена");
                    }
                }
            });

            if (totalEmails < 0) {
                System.err.println("Не удалось подключиться к Gmail — pipeline прерван");
                return;
            }
            if (totalEmails == 0) {
                System.out.println("Новых писем нет");
                return;
            }

            System.out.println("\n=== Итог: писем " + totalEmails
                    + ", добавлено в очередь " + totalEnqueued.get()
                    + ", пропущено " + totalSkipped.get() + " ===");

        } catch (Throwable e) {
            System.err.println("Критическая ошибка pipeline: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String extractSenderDomain(String from) {
        if (from == null) return "unknown";
        int atIdx = from.indexOf('@');
        if (atIdx < 0) return from.toLowerCase().trim();
        String rest = from.substring(atIdx + 1).trim();
        int gtIdx = rest.indexOf('>');
        return (gtIdx >= 0 ? rest.substring(0, gtIdx) : rest).toLowerCase();
    }
}
