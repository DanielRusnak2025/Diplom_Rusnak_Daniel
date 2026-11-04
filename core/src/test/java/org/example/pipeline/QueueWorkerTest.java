package org.example.pipeline;

import org.example.Article;
import org.example.QueueStatus;
import org.example.RejectReason;
import org.example.ai.ArticleClassification;
import org.example.ai.ExtraAssignment;
import org.example.ai.RelevanceLevel;
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

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
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

    private static final String ENG_TEXT =
            "The prime minister announced a new economic policy today affecting all citizens in the country. " +
            "Parliament approved the budget after weeks of heated debate and extensive negotiations with experts. " +
            "These measures are expected to boost economic growth and improve living standards nationally.";

    private static final String RO_TEXT =
            "Premierul a anuntat astazi o noua politica economica importanta pentru cetatenii tarii noastre dragi. " +
            "Parlamentul a aprobat bugetul dupa mai multe saptamani de dezbateri intense si negocieri lungi. " +
            "Masurile sunt asteptate sa aiba un impact pozitiv semnificativ asupra economiei nationale.";

    private List<TagInfo>             taxonomy;
    private int                       firstCategoryId;
    private int                       secondCategoryId;
    private ArticleLanguageDetector   langDetectorEnOnly;
    private ArticleDAOImpl            articleDAO;

    @BeforeAll
    void setup() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            taxonomy = QueueWorker.loadTaxonomyFrom(session);
        }
        if (taxonomy.isEmpty()) throw new IllegalStateException(
                "No CATEGORY tags in db_use_test_it — taxonomy seed (V5_4) may not have run");
        if (taxonomy.size() < 2) throw new IllegalStateException(
                "Need at least 2 CATEGORY tags for keyword bonus test");
        firstCategoryId    = taxonomy.get(0).id();
        secondCategoryId   = taxonomy.get(1).id();
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
            int id = s.createNativeQuery(
                    "SELECT LAST_INSERT_ID()", Integer.class).getSingleResult();
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
            int id = s.createNativeQuery(
                    "SELECT LAST_INSERT_ID()", Integer.class).getSingleResult();
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

    private int atpPercent(int articleId, int tagId) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Object raw = s.createNativeQuery(
                    "SELECT percent FROM article_tag_percent WHERE article_id=:a AND tag_id=:t",
                    Object.class)
                    .setParameter("a", articleId)
                    .setParameter("t", tagId)
                    .getSingleResult();
            return raw != null ? ((Number) raw).intValue() : 0;
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
                taxonomy, Map.of(), 2,
                "stub-model", "v0",
                langDetectorEnOnly, Set.of());
    }

    private static PageLoader engLoader(String finalUrl) {
        return url -> new PageLoader.Result(
                new ParseResult("Test Title", "test.invalid", null, finalUrl,
                        ENG_TEXT, ENG_TEXT.length(), "article", null),
                finalUrl, 200);
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

        org.example.Article saved = articleDAO.findById(articleId).orElseThrow();
        assertEquals("en", saved.getArticleLanguage(),
                "article_language must be ISO code, not full name");
        assertEquals(QueueWorker.TAXONOMY_YAML_VERSION, (int) saved.getTaxonomyVersion(),
                "taxonomy_version must match taxonomy.yaml version field");
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

        PageLoader roLoader = u -> new PageLoader.Result(
                new ParseResult("Stire", "test.invalid", null, u,
                        RO_TEXT, RO_TEXT.length(), "article", null),
                u, 200);

        worker(isArticleClassifier(firstCategoryId), roLoader).processQueue(1);

        assertEquals(QueueStatus.REJECTED, queueStatus(qid));
        assertEquals(RejectReason.LANGUAGE_NOT_SUPPORTED, rejectReason(qid));
    }

    @Test
    void duplicateLinkHash_rejectsWithDuplicate() {
        String url = BASE + "5";
        insertArticle(url);
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

        assertEquals(0, atpCount(existingArticleId));

        worker(isArticleClassifier(firstCategoryId), engLoader(url)).processQueue(1);

        assertEquals(QueueStatus.DONE, queueStatus(qid));
        assertEquals(existingArticleId, (int) queueArticleId(qid));
        assertTrue(atpCount(existingArticleId) >= 1, "reclassified article must have ATP rows");
    }

    @Test
    void reclassification_finalUrlMatchesDifferentArticle_rejectsDuplicate() {
        String urlA = BASE + "7a";
        String urlB = BASE + "7b";

        int articleA = insertArticle(urlA);
        insertArticle(urlB);

        int qid = insertPendingWithArticle(urlA, articleA);

        worker(isArticleClassifier(firstCategoryId), engLoader(urlB)).processQueue(1);

        assertEquals(QueueStatus.REJECTED, queueStatus(qid));
        assertEquals(RejectReason.DUPLICATE, rejectReason(qid));
    }

    @Test
    void ssrf_rejectsWithInvalidUrl() {
        int qid = insertPending(BASE + "8");

        PageLoader ssrfLoader = url -> {
            throw new PageLoader.SsrfViolationException("SSRF blocked: loopback");
        };

        worker(isArticleClassifier(firstCategoryId), ssrfLoader).processQueue(1);

        assertEquals(QueueStatus.REJECTED, queueStatus(qid));
        assertEquals(RejectReason.INVALID_URL, rejectReason(qid));
    }

    @Test
    void publicationDate_usesPublishedDate_whenPresent() {
        String url = BASE + "10";
        int qid = insertPending(url);

        String isoDate = "2024-06-15T12:00:00Z"; // noon UTC — unambiguous in any JVM timezone
        PageLoader datedLoader = u -> new PageLoader.Result(
                new ParseResult("Test", "test.invalid", isoDate, u,
                        ENG_TEXT, ENG_TEXT.length(), "article", null),
                u, 200);

        worker(isArticleClassifier(firstCategoryId), datedLoader).processQueue(1);

        Integer articleId = queueArticleId(qid);
        assertNotNull(articleId);
        org.example.Article saved = articleDAO.findById(articleId).orElseThrow();

        // @Temporal(DATE) stores only year/month/day; Hibernate reads back using JVM default timezone.
        // Noon UTC = June 15 in every timezone from UTC-11 to UTC+11, so no boundary ambiguity.
        Calendar cal = Calendar.getInstance();
        cal.setTime(saved.getPublicationDate());
        assertEquals(2024,         cal.get(Calendar.YEAR),         "year must come from parsedDate");
        assertEquals(Calendar.JUNE, cal.get(Calendar.MONTH),       "month must come from parsedDate");
        assertEquals(15,           cal.get(Calendar.DAY_OF_MONTH), "day must come from parsedDate");
    }

    @Test
    void publicationDate_fallsBackToNow_whenAbsent() {
        String url = BASE + "11";
        int qid = insertPending(url);

        // Capture today's date before processing
        Calendar today = Calendar.getInstance();

        PageLoader noDateLoader = u -> new PageLoader.Result(
                new ParseResult("Test", "test.invalid", null, u,
                        ENG_TEXT, ENG_TEXT.length(), "article", null),
                u, 200);
        worker(isArticleClassifier(firstCategoryId), noDateLoader).processQueue(1);

        Integer articleId = queueArticleId(qid);
        assertNotNull(articleId);

        // @Temporal(DATE) stores only year/month/day — compare at that granularity.
        Calendar stored = Calendar.getInstance();
        stored.setTime(articleDAO.findById(articleId).orElseThrow().getPublicationDate());

        assertEquals(today.get(Calendar.YEAR),         stored.get(Calendar.YEAR),         "year must be today");
        assertEquals(today.get(Calendar.MONTH),        stored.get(Calendar.MONTH),        "month must be today");
        assertEquals(today.get(Calendar.DAY_OF_MONTH), stored.get(Calendar.DAY_OF_MONTH), "day must be today");
    }

    @Test
    void keywordBonus_raisesPercent_cappedAtMax() {
        // Two categories: both assigned HIGH(3).
        // Inject 3 matching keywords for firstCategory with maxBonus=2 → bonus capped at 2.
        // first raw=3+2=5, second raw=3. Total=8.
        // Largest-remainder: first=63 (5*100/8=62.5→63), second=37.
        // Without cap (bonus=3): first=6, total=9 → first=67. Verifies cap is active.
        Map<String, Map<Integer, List<String>>> kw = Map.of(
                "en", Map.of(firstCategoryId,
                        List.of("parliament", "budget", "prime minister")));

        String url = BASE + "9";
        int qid = insertPending(url);

        org.example.ai.ArticleClassifier bothCats = (text, tax) -> new ArticleClassification(
                true, firstCategoryId, null,
                List.of(new ExtraAssignment(secondCategoryId, RelevanceLevel.HIGH)));

        // Text matches all 3 patterns
        PageLoader kl = u -> new PageLoader.Result(
                new ParseResult("Parliament budget vote", "test.invalid", null, u,
                        ENG_TEXT, ENG_TEXT.length(), "article", null),
                u, 200);

        QueueWorker w = new QueueWorker(
                bothCats, kl,
                new ArticleQueueDAOImpl(), articleDAO,
                new EntityMatcher(List.of()),
                taxonomy, kw, 2,
                "stub-model", "v0", langDetectorEnOnly, Set.of());

        w.processQueue(1);

        Integer articleId = queueArticleId(qid);
        assertNotNull(articleId);

        int firstPct  = atpPercent(articleId, firstCategoryId);
        int secondPct = atpPercent(articleId, secondCategoryId);

        assertEquals(100, firstPct + secondPct, "ATP category percents must sum to 100");
        assertTrue(firstPct > 50,
                "keyword bonus must raise firstCategory above equal split (50%): got " + firstPct);
        // Cap check: with maxBonus=2, raw=5/8→63%; uncapped raw=6/9→67%; so must be ≤63
        assertTrue(firstPct <= 63,
                "bonus must be capped at maxBonus=2 (expect ≤63%): got " + firstPct);
    }

    @Test
    void preFilter_rootUrl_rejectsWithRootUrl() {
        String url = BASE + "14";
        int qid = insertPending(url);

        PageLoader rootLoader = u -> new PageLoader.Result(
                new ParseResult("Some Title", "test.invalid", null, "https://test.invalid/",
                        ENG_TEXT, ENG_TEXT.length(), "article", null),
                "https://test.invalid/", 200);

        worker(isArticleClassifier(firstCategoryId), rootLoader).processQueue(1);

        assertEquals(QueueStatus.REJECTED, queueStatus(qid));
        assertEquals(RejectReason.ROOT_URL, rejectReason(qid));
    }

    @Test
    void preFilter_tooShort_rejectsWithTextTooShort() {
        String url = BASE + "15";
        int qid = insertPending(url);

        PageLoader shortLoader = u -> new PageLoader.Result(
                new ParseResult("Short", "test.invalid", null, u,
                        "Too short.", 10, "fallback", null),
                u, 200);

        worker(isArticleClassifier(firstCategoryId), shortLoader).processQueue(1);

        assertEquals(QueueStatus.REJECTED, queueStatus(qid));
        assertEquals(RejectReason.TEXT_TOO_SHORT, rejectReason(qid));
    }

    @Test
    void preFilter_notFound_rejectsWithNotFound() {
        String url = BASE + "16";
        int qid = insertPending(url);

        PageLoader notFoundLoader = u -> new PageLoader.Result(
                new ParseResult("404 Page Not Found", "test.invalid", null, u,
                        ENG_TEXT, ENG_TEXT.length(), "article", null),
                u, 200);

        worker(isArticleClassifier(firstCategoryId), notFoundLoader).processQueue(1);

        assertEquals(QueueStatus.REJECTED, queueStatus(qid));
        assertEquals(RejectReason.NOT_FOUND, rejectReason(qid));
    }

    @Test
    void preFilter_newsletterHost_rejectsWithNewsletterHost() {
        String url = BASE + "17";
        int qid = insertPending(url);

        String finalUrl = "https://campaign-archive.com/newsletter-page";
        PageLoader newsletterLoader = u -> new PageLoader.Result(
                new ParseResult("Newsletter Title", "campaign-archive.com", null, finalUrl,
                        ENG_TEXT, ENG_TEXT.length(), "article", null),
                finalUrl, 200);

        QueueWorker w = new QueueWorker(
                isArticleClassifier(firstCategoryId), newsletterLoader,
                new ArticleQueueDAOImpl(), articleDAO,
                new EntityMatcher(List.of()),
                taxonomy, Map.of(), 2,
                "stub-model", "v0", langDetectorEnOnly,
                Set.of("campaign-archive.com"));

        w.processQueue(1);

        assertEquals(QueueStatus.REJECTED, queueStatus(qid));
        assertEquals(RejectReason.NEWSLETTER_HOST, rejectReason(qid));
    }
}
