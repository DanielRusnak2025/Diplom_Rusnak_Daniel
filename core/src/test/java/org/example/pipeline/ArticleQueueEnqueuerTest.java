package org.example.pipeline;

import org.example.QueueStatus;
import org.example.email.EmailMessage;
import org.example.scheduler.EmailScheduler;
import org.example.test.TestDb;
import org.example.util.HibernateUtil;
import org.example.util.UrlNormalizer;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ArticleQueueEnqueuer and ProcessedEmailLog against db_use_test_it.
 * NEVER run against db_use_test.
 */
@ExtendWith(TestDb.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArticleQueueEnqueuerTest {

    private static final String BASE = "https://test.invalid/aqe-";

    @BeforeEach
    void cleanup() {
        deleteTestRows();
    }

    @AfterAll
    void cleanupAfterAll() {
        deleteTestRows();
    }

    private void deleteTestRows() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.createNativeMutationQuery(
                    "DELETE FROM article_queue WHERE url LIKE 'https://test.invalid/aqe-%'")
                    .executeUpdate();
            s.createNativeMutationQuery(
                    "DELETE FROM processed_email WHERE sender_domain = 'test.invalid'")
                    .executeUpdate();
            tx.commit();
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private QueueStatus queueStatus(String url) {
        try {
            String hash = UrlNormalizer.hash(UrlNormalizer.normalize(url));
            try (Session s = HibernateUtil.getSessionFactory().openSession()) {
                String raw = s.createNativeQuery(
                        "SELECT status FROM article_queue WHERE url_hash = :h", String.class)
                        .setParameter("h", hash)
                        .getSingleResultOrNull();
                return raw != null ? QueueStatus.valueOf(raw) : null;
            }
        } catch (IllegalArgumentException e) {
            return null; // malformed / too-long URL that can't be normalized
        }
    }

    private Integer queryEmailId(String url) {
        try {
            String hash = UrlNormalizer.hash(UrlNormalizer.normalize(url));
            try (Session s = HibernateUtil.getSessionFactory().openSession()) {
                Object raw = s.createNativeQuery(
                        "SELECT email_id FROM article_queue WHERE url_hash = :h", Object.class)
                        .setParameter("h", hash)
                        .getSingleResultOrNull();
                return raw != null ? ((Number) raw).intValue() : null;
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private long countQueueRows(String url) {
        String hash = UrlNormalizer.hash(UrlNormalizer.normalize(url));
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            return s.createNativeQuery(
                    "SELECT COUNT(*) FROM article_queue WHERE url_hash = :h", Long.class)
                    .setParameter("h", hash)
                    .getSingleResult();
        }
    }

    private long countProcessedEmailRows(String messageIdHash) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            return s.createNativeQuery(
                    "SELECT COUNT(*) FROM processed_email WHERE message_id_hash = :h", Long.class)
                    .setParameter("h", messageIdHash)
                    .getSingleResult();
        }
    }

    /** Open a session + transaction, enqueue, commit, return count. */
    private int enqueue(List<String> urls, Integer emailId) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            int n = new ArticleQueueEnqueuer().enqueue(urls, emailId, s);
            tx.commit();
            return n;
        }
    }

    // ── ArticleQueueEnqueuer: basic enqueue ───────────────────────────────────

    @Test
    void enqueue_newUrl_insertsPendingRow() {
        String url = BASE + "1";
        int count = enqueue(List.of(url), null);

        assertEquals(1, count);
        assertEquals(QueueStatus.PENDING, queueStatus(url));
    }

    @Test
    void enqueue_duplicateUrl_silentlySkippedReturnsZero() {
        String url = BASE + "2";
        int first  = enqueue(List.of(url), null);
        int second = enqueue(List.of(url), null);

        assertEquals(1, first,  "first insert must return 1");
        assertEquals(0, second, "duplicate must return 0 silently");
        assertEquals(1, countQueueRows(url));
    }

    @Test
    void enqueue_twoNewOneDuplicate_returnsCountOfNew() {
        String urlA = BASE + "3a";
        String urlB = BASE + "3b";
        String urlC = BASE + "3c";

        int first  = enqueue(List.of(urlA, urlB), null);
        int second = enqueue(List.of(urlA, urlC), null); // urlA is duplicate

        assertEquals(2, first);
        assertEquals(1, second);
        assertEquals(1, countQueueRows(urlC));
        assertEquals(1, countQueueRows(urlA));
    }

    @Test
    void enqueue_emptyList_returnsZero() {
        assertEquals(0, enqueue(List.of(), null));
    }

    // ── ArticleQueueEnqueuer: URL validation ──────────────────────────────────

    @Test
    void enqueue_malformedUrl_skippedOtherUrlInserted() {
        String normal = BASE + "m1";
        int count = enqueue(List.of("not-a-url", normal), null);

        assertEquals(1, count, "only the valid URL should be inserted");
        assertNull(queueStatus("not-a-url"), "malformed URL must not appear in queue");
        assertEquals(QueueStatus.PENDING, queueStatus(normal));
    }

    @Test
    void enqueue_tooLongUrl_skippedOtherUrlInserted() {
        // URL > 2048 chars
        String tooLong = "https://test.invalid/" + "x".repeat(2100);
        String normal  = BASE + "long1";

        int count = enqueue(List.of(tooLong, normal), null);

        assertEquals(1, count, "only the short URL should be inserted");
        // can't call queueStatus(tooLong) via normalize — just verify normal is present
        assertEquals(QueueStatus.PENDING, queueStatus(normal));
    }

    @Test
    void enqueue_tooLongUrl_notInQueue() {
        String tooLong = "https://test.invalid/" + "x".repeat(2100);
        int count = enqueue(List.of(tooLong), null);
        assertEquals(0, count, "URL > 2048 chars must not be inserted");
    }

    // ── email_id linkage ──────────────────────────────────────────────────────

    @Test
    void enqueue_setsEmailId_onQueueRows() {
        String url = BASE + "eid1";
        ProcessedEmailLog log = new ProcessedEmailLog();

        Integer[] emailIdHolder = {null};
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            emailIdHolder[0] = log.insertPlaceholder(
                    UrlNormalizer.hash("email-anchor-" + System.nanoTime()),
                    "test.invalid", new Date(), 1, 0, s);
            new ArticleQueueEnqueuer().enqueue(List.of(url), emailIdHolder[0], s);
            log.updateSavedCount(emailIdHolder[0], 1, s);
            tx.commit();
        }

        assertEquals(emailIdHolder[0], queryEmailId(url),
                "article_queue row must carry the processed_email id");
    }

    // ── ProcessedEmailLog ─────────────────────────────────────────────────────

    @Test
    void processedEmailLog_unknownHash_notAlreadyProcessed() {
        String hash = UrlNormalizer.hash("novel-mid-" + System.nanoTime());
        assertFalse(new ProcessedEmailLog().isAlreadyProcessed(hash));
    }

    @Test
    void processedEmailLog_recordThenCheck_returnsAlreadyProcessed() {
        String hash = UrlNormalizer.hash("test-mid-" + System.nanoTime());
        ProcessedEmailLog plog = new ProcessedEmailLog();

        assertFalse(plog.isAlreadyProcessed(hash));
        Integer id = plog.record(hash, "test.invalid", new Date(), 5, 2, 3);
        assertNotNull(id);
        assertTrue(plog.isAlreadyProcessed(hash));
        assertEquals(1, countProcessedEmailRows(hash));
    }

    @Test
    void processedEmailLog_duplicateHash_secondRecordReturnsNull() {
        String hash = UrlNormalizer.hash("dup-mid-" + System.nanoTime());
        ProcessedEmailLog plog = new ProcessedEmailLog();

        assertNotNull(plog.record(hash, "test.invalid", new Date(), 3, 1, 2));
        assertNull(plog.record(hash, "test.invalid", new Date(), 3, 1, 2),
                "second record with same hash must return null (UNIQUE violation)");
        assertEquals(1, countProcessedEmailRows(hash));
    }

    // ── No Message-ID fallback idempotency ────────────────────────────────────

    @Test
    void processedEmail_fallbackHash_secondRunSkipped() {
        EmailMessage email = new EmailMessage(
                "Test Newsletter", "body text", "sender@test.invalid", new Date());
        // messageId is null — fallback hash will be used

        String hash = EmailScheduler.computeMessageHash(email);

        ProcessedEmailLog plog = new ProcessedEmailLog();
        assertFalse(plog.isAlreadyProcessed(hash), "first run: must not be processed yet");

        // Simulate first run — record the email
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            Integer id = plog.insertPlaceholder(hash, "test.invalid",
                    email.getReceivedDate(), 0, 0, s);
            plog.updateSavedCount(id, 0, s);
            tx.commit();
        }

        // Second run: same EmailMessage object → same fallback hash → must be skipped
        assertTrue(plog.isAlreadyProcessed(hash), "second run: must be recognized as already processed");
    }
}
