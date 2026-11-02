package org.example.pipeline;

import org.example.Article;
import org.example.QueueStatus;
import org.example.RejectReason;
import org.example.ai.ArticleClassification;
import org.example.ai.TagInfo;
import org.example.dao.impl.ArticleDAOImpl;
import org.example.dao.impl.ArticleQueueDAOImpl;
import org.example.test.TestDb;
import org.example.util.HibernateUtil;
import org.example.util.UrlNormalizer;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for QueueWorker against db_use_test_it.
 * Classifier and page loader are always stubs — no real HTTP or Ollama calls.
 * NEVER run against db_use_test.
 */
@ExtendWith(TestDb.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QueueWorkerTest {

    private static final String BASE = "https://test.invalid/qwt-";

    private List<TagInfo>             taxonomy;
    private int                       firstCategoryId;
    private ArticleLanguageDetector   langDetectorEnOnly;
    private ArticleDAOImpl            articleDAO;

    @BeforeAll
    void setup() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            taxonomy = QueueWorker.loadTaxonomyFrom(session);
        }
        if (taxonomy.isEmpty()) throw new IllegalStateException(
                "No CATEGORY tags in db_use_test_it — taxonomy seed (V5_4) may not have run");
        firstCategoryId    = taxonomy.get(0).id();
        langDetectorEnOnly = new ArticleLanguageDetector(Set.of("en"));
        articleDAO         = new ArticleDAOImpl();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private int insertPending(String url) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            String norm = UrlNormalizer.normalize(url);
            String hash = UrlNormalizer.hash(norm);
            s.createNativeMutationQuery(
                    "INSERT INTO article_queue (url, url_hash, status, created_at) " +
                    "VALUES (:url, :hash, 'PENDING', NOW(6))")
                    .setParameter("url", url)
                    .setParameter("hash", hash)
                    .executeUpdate();
            int id = ((Number) s.createNativeQuery(
                    "SELECT LAST_INSERT_ID()", Integer.class).getSingleResult()).intValue();
            tx.commit();
            return id;
        }
    }

    private int insertPendingWithArticle(String url, int articleId) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            String norm = UrlNormalizer.normalize(url);
            String hash = UrlNormalizer.hash(norm);
            s.createNativeMutationQuery(
                    "INSERT INTO article_queue (url, url_hash, status, article_id, created_at) " +
                    "VALUES (:url, :hash, 'PENDING', :aid, NOW(6))")
                    .setParameter("url", url)
                    .setParameter("hash", hash)
                    .setParameter("aid", articleId)
                    .executeUpdate();
            int id = ((Number) s.createNativeQuery(
                    "SELECT LAST_INSERT_ID()", Integer.class).getSingleResult()).intValue();
            tx.commit();
            return id;
        }
    }

    private QueueStatus queueStatus(int queueId) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            return QueueStatus.valueOf((String) s.createNativeQuery(
                    "SELECT status FROM article_queue WHERE id=:id", String.class)
                    .setParameter("id", queueId).getSingleResult());
        }
    }

    private RejectReason rejectReason(int queueId) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            String raw = (String) s.createNativeQuery(
                    "SELECT reject_reason FROM article_queue WHERE id=:id", String.class)
                    .setParameter("id", queueId).getSingleResult();
            return raw != null ? RejectReason.valueOf(raw) : null;
        }
    }

    private Integer queueArticleId(int queueId) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Object raw = s.createNativeQuery(
                    "SELECT article_id FROM article_queue WHERE id=:id", Object.class)
                    .setParameter("id", queueId).getSingleResult();
            return raw != null ? ((Number) raw).intValue() : null;
        }
    }

    private int atpCount(int articleId) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            return s.createNativeQuery(
                    "SELECT COUNT(*) FROM article_tag_percent WHERE article_id=:id", Long.class)
                    .setParameter("id", articleId).getSingleResult().intValue();
        }
    }

    private int insertArticle(String link) {
        Article a = new Article();
        a.setLink(link);
        a.setTitle("Test Article");
        a.setPublicationDate(new Date());
        return articleDAO.save(a).getId();
    }

    private QueueWorker worker(
            org.example.ai.ArticleClassifier classifier,
            PageLoader loader) {
        return new QueueWorker(
                classifier, loader,
                new ArticleQueueDAOImpl(), articleDAO,
                new EntityMatcher(List.of()),
                taxonomy, "stub-model", "v0",
                langDetectorEnOnly);
    }

    private static PageLoader engLoader(String finalUrl) {
        return url -> new PageLoader.Result(
                new ParseResult("Test Title", "test.invalid", null, finalUrl,
                        "The prime minister announced a new economic policy today. " +
                        "Parliament approved the budget after weeks of debate."),
                finalUrl);
    }

    private static org.example.ai.ArticleClassifier isArticleClassifier(int categoryId) {
        return (text, tax) -> new ArticleClassification(true, categoryId, null, List.of());
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void happyPath_newItem_doneWithAtpRows() {
        String url = BASE + "1";
        int qid = insertPending(url);

        int processed = worker(isArticleClassifier(firstCategoryId), engLoader(url))
                .processQueue(1);

        assertEquals(1, processed);
        assertEquals(QueueStatus.DONE, queueStatus(qid));

        Integer articleId = queueArticleId(qid);
        assertNotNull(articleId, "queue.article_id must be set after DONE");
        assertTrue(atpCount(articleId) >= 1, "at least one article_tag_percent row expected");
    }

    @Test
    void fetchFailed_rejectsWithFetchFailed() {
        int qid = insertPending(BASE + "2");

        worker(isArticleClassifier(firstCategoryId), url -> null).processQueue(1);

        assertEquals(QueueStatus.REJECTED, queueStatus(qid));
        assertEquals(RejectReason.FETCH_FAILED, rejectReason(qid));
    }

    @Test
    void notArticle_rejectsWithNotArticle() {
        String url = BASE + "3";
        int qid = insertPending(url);

        worker((text, tax) -> ArticleClassification.notAnArticle(), engLoader(url))
                .processQueue(1);

        assertEquals(QueueStatus.REJECTED, queueStatus(qid));
        assertEquals(RejectReason.NOT_ARTICLE, rejectReason(qid));
    }

    @Test
    void languageNotSupported_rejectsWithLanguageNotSupported() {
        String url = BASE + "4";
        int qid = insertPending(url);

        // Romanian text — not in en-only detector set
        PageLoader roLoader = u -> new PageLoader.Result(
                new ParseResult("Știre", "test.invalid", null, u,
                        "Premierul a anunțat astăzi o nouă politică economică importantă pentru România."),
                u);

        worker(isArticleClassifier(firstCategoryId), roLoader).processQueue(1);

        assertEquals(QueueStatus.REJECTED, queueStatus(qid));
        assertEquals(RejectReason.LANGUAGE_NOT_SUPPORTED, rejectReason(qid));
    }

    @Test
    void duplicateLinkHash_rejectsWithDuplicate() {
        String url = BASE + "5";
        insertArticle(url); // pre-existing article with same link_hash
        int qid = insertPending(url);

        worker(isArticleClassifier(firstCategoryId), engLoader(url)).processQueue(1);

        assertEquals(QueueStatus.REJECTED, queueStatus(qid));
        assertEquals(RejectReason.DUPLICATE, rejectReason(qid));
    }

    @Test
    void reclassification_happyPath_updatesArticleAndTags() {
        String url = BASE + "6";
        int existingArticleId = insertArticle(url);
        int qid = insertPendingWithArticle(url, existingArticleId);

        // Verify there are no ATPs before processing
        assertEquals(0, atpCount(existingArticleId));

        worker(isArticleClassifier(firstCategoryId), engLoader(url)).processQueue(1);

        assertEquals(QueueStatus.DONE, queueStatus(qid));
        assertEquals(existingArticleId, (int) queueArticleId(qid));
        assertTrue(atpCount(existingArticleId) >= 1, "reclassified article must have ATP rows");
    }

    @Test
    void reclassification_finalUrlMatchesDifferentArticle_rejectsDuplicate() {
        String urlA = BASE + "7a"; // article being reclassified
        String urlB = BASE + "7b"; // a different article

        int articleA = insertArticle(urlA);
        insertArticle(urlB); // B exists with its own hash

        // Queue item points to articleA but the page loader returns urlB as the final URL
        // → hash of urlB matches articleB → DUPLICATE
        int qid = insertPendingWithArticle(urlA, articleA);

        worker(isArticleClassifier(firstCategoryId), engLoader(urlB)).processQueue(1);

        assertEquals(QueueStatus.REJECTED, queueStatus(qid));
        assertEquals(RejectReason.DUPLICATE, rejectReason(qid));
    }
}
