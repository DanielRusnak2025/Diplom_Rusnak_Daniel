package org.example.pipeline;

import org.example.ArticleQueue;
import org.example.QueueStatus;
import org.example.RejectReason;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: verifies the article_queue.reject_reason CHECK constraint
 * accepts 'DW_SECTION_PAGE' after the V13_3 migration.
 */
@ExtendWith(TestDb.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArticleQueueRejectReasonTest {

    private static final String BASE = "https://test.invalid/aqrr-";

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
                    "DELETE FROM article_queue WHERE url LIKE 'https://test.invalid/aqrr-%'")
                    .executeUpdate();
            tx.commit();
        }
    }

    @Test
    void dw_section_page_reject_reason_persists() {
        String url = BASE + "dw-section";
        String urlHash = UrlNormalizer.hash(UrlNormalizer.normalize(url));

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            ArticleQueue aq = new ArticleQueue();
            aq.setUrl(url);
            aq.setUrlHash(urlHash);
            aq.setStatus(QueueStatus.REJECTED);
            aq.setRejectReason(RejectReason.DW_SECTION_PAGE);
            s.persist(aq);
            tx.commit();
        }

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            String raw = s.createNativeQuery(
                    "SELECT reject_reason FROM article_queue WHERE url_hash = :h", String.class)
                    .setParameter("h", urlHash)
                    .getSingleResult();
            assertEquals("DW_SECTION_PAGE", raw,
                    "reject_reason must be persisted as DW_SECTION_PAGE in the DB");
        }
    }
}
