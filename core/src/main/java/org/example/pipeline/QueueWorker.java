package org.example.pipeline;

import org.example.Article;
import org.example.RejectReason;
import org.example.Tag;
import org.example.TagKind;
import org.example.ai.AiConfig;
import org.example.ai.ArticleClassification;
import org.example.ai.ArticleClassifier;
import org.example.ai.ClassificationException;
import org.example.ai.ExtraAssignment;
import org.example.ai.OllamaClassifier;
import org.example.ai.SubtopicInfo;
import org.example.ai.TagInfo;
import org.example.dao.ArticleDAO;
import org.example.dao.ArticleQueueDAO;
import org.example.dao.ClaimedItem;
import org.example.dao.impl.ArticleDAOImpl;
import org.example.dao.impl.ArticleQueueDAOImpl;
import org.example.util.HibernateUtil;
import org.example.util.UrlNormalizer;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

public class QueueWorker {

    private static final Logger log = Logger.getLogger(QueueWorker.class.getName());

    static final int LOCK_MINUTES  = 10;
    static final int MAX_ATTEMPTS  = 3;
    // relative weight given to the main category vs extra assignments
    private static final int PRIMARY_WEIGHT = 6;

    private final ArticleQueueDAO          queueDAO;
    private final ArticleDAO               articleDAO;
    private final ArticleClassifier        classifier;
    private final PageLoader               pageLoader;
    private final ArticleLanguageDetector  languageDetector;
    private final EntityMatcher            entityMatcher;
    private final List<TagInfo>            taxonomy;
    private final int                      taxonomyVersion;
    private final String                   classifierModel;
    private final String                   promptVersion;

    // ── Production constructor ────────────────────────────────────────────────

    public QueueWorker(ArticleClassifier classifier, PageLoader pageLoader) {
        this(
            classifier, pageLoader,
            new ArticleQueueDAOImpl(),
            new ArticleDAOImpl(),
            loadEntityMatcherFromDb(),
            loadTaxonomyFromDb(),
            AiConfig.getOllamaModel(),
            OllamaClassifier.PROMPT_VERSION,
            new ArticleLanguageDetector(AiConfig.getLanguagesAllowed())
        );
    }

    // ── Test / injectable constructor (package-private) ───────────────────────

    QueueWorker(
            ArticleClassifier classifier,
            PageLoader pageLoader,
            ArticleQueueDAO queueDAO,
            ArticleDAO articleDAO,
            EntityMatcher entityMatcher,
            List<TagInfo> taxonomy,
            String classifierModel,
            String promptVersion,
            ArticleLanguageDetector languageDetector) {
        this.classifier      = classifier;
        this.pageLoader      = pageLoader;
        this.queueDAO        = queueDAO;
        this.articleDAO      = articleDAO;
        this.entityMatcher   = entityMatcher;
        this.taxonomy        = List.copyOf(taxonomy);
        this.taxonomyVersion = taxonomy.size();
        this.classifierModel = classifierModel;
        this.promptVersion   = promptVersion;
        this.languageDetector = languageDetector;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Resets stale locks, claims up to {@code limit} PENDING items, processes each.
     * @return count of items claimed (each ends up DONE, REJECTED, or FAILED/PENDING-retry)
     */
    public int processQueue(int limit) {
        queueDAO.resetStaleLocks(MAX_ATTEMPTS);
        List<ClaimedItem> items = queueDAO.claimPending(limit, LOCK_MINUTES);
        log.info("[QueueWorker] claimed=" + items.size());

        for (ClaimedItem item : items) {
            try {
                processOne(item);
            } catch (Exception e) {
                log.warning("[QueueWorker] unexpected error queue_id=" + item.id() + ": " + e.getMessage());
                try {
                    queueDAO.markFailed(item.id(), truncate(e.getMessage(), 500),
                                        item.lockToken(), MAX_ATTEMPTS);
                } catch (Exception inner) {
                    log.severe("[QueueWorker] markFailed also failed queue_id=" + item.id()
                               + ": " + inner.getMessage());
                }
            }
        }
        return items.size();
    }

    // ── Per-item pipeline ─────────────────────────────────────────────────────

    private void processOne(ClaimedItem item) {
        // 1. Fetch + parse page
        PageLoader.Result fetch = pageLoader.load(item.url());
        if (fetch == null) {
            queueDAO.markRejected(item.id(), RejectReason.FETCH_FAILED, item.lockToken());
            log.info("[QueueWorker] q=" + item.id() + " REJECTED FETCH_FAILED");
            return;
        }

        // 2. Language detection (null → not in allowed set)
        String langCode = languageDetector.detect(fetch.parsed().textForAi());
        if (langCode == null) {
            queueDAO.markRejected(item.id(), RejectReason.LANGUAGE_NOT_SUPPORTED, item.lockToken());
            log.info("[QueueWorker] q=" + item.id() + " REJECTED LANGUAGE_NOT_SUPPORTED");
            return;
        }

        // 3. Normalize final URL + compute link_hash
        String finalUrl;
        String linkHash;
        try {
            finalUrl = UrlNormalizer.normalize(fetch.finalUrl());
            linkHash = UrlNormalizer.hash(finalUrl);
        } catch (IllegalArgumentException e) {
            queueDAO.markRejected(item.id(), RejectReason.INVALID_URL, item.lockToken());
            log.info("[QueueWorker] q=" + item.id() + " REJECTED INVALID_URL: " + e.getMessage());
            return;
        }

        // 4. Duplicate check by link_hash of final URL
        Optional<Article> existingByHash = articleDAO.findByLinkHash(linkHash);
        if (item.articleId() == null) {
            // New item: any existing article with this hash is a duplicate
            if (existingByHash.isPresent()) {
                queueDAO.markRejected(item.id(), RejectReason.DUPLICATE, item.lockToken());
                log.info("[QueueWorker] q=" + item.id()
                         + " REJECTED DUPLICATE (new; article_id=" + existingByHash.get().getId() + ")");
                return;
            }
        } else {
            // Reclassification: hash collision with a DIFFERENT article → reject, leave old article intact
            if (existingByHash.isPresent() && existingByHash.get().getId() != item.articleId()) {
                queueDAO.markRejected(item.id(), RejectReason.DUPLICATE, item.lockToken());
                log.info("[QueueWorker] q=" + item.id()
                         + " REJECTED DUPLICATE (reclassify; hash→article_id="
                         + existingByHash.get().getId() + ")");
                return;
            }
        }

        // 5. Region detection
        String title  = fetch.parsed().title() != null ? fetch.parsed().title() : "";
        String region = entityMatcher.matchRegion(title, fetch.parsed().textForAi(), langCode);

        // 6. Classify
        ArticleClassification ac;
        try {
            ac = classifier.classify(fetch.parsed().textForAi(), taxonomy);
        } catch (ClassificationException e) {
            queueDAO.markFailed(item.id(), truncate(e.getMessage(), 500),
                                item.lockToken(), MAX_ATTEMPTS);
            log.warning("[QueueWorker] q=" + item.id() + " classify failed: " + e.getMessage());
            return;
        }

        if (!ac.isArticle()) {
            queueDAO.markRejected(item.id(), RejectReason.NOT_ARTICLE, item.lockToken());
            log.info("[QueueWorker] q=" + item.id() + " REJECTED NOT_ARTICLE");
            return;
        }

        // 7. Persist article + tags
        int articleId = (item.articleId() != null)
                ? reclassify(item.articleId(), finalUrl, langCode, region, ac, fetch.parsed())
                : createNew(finalUrl, langCode, region, ac, fetch.parsed());

        queueDAO.markDone(item.id(), articleId, item.lockToken());
        log.info("[QueueWorker] q=" + item.id() + " DONE article_id=" + articleId);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private int createNew(String finalUrl, String langCode, String region,
                          ArticleClassification ac, ParseResult parsed) {
        Article article = new Article();
        article.setLink(finalUrl);
        article.setTitle(truncate(parsed.title(), 500));
        article.setPublisher(truncate(parsed.publisher(), 255));
        article.setArticleLanguage(isoToFull(langCode));
        article.setArticleRegion(region);
        article.setClassifierModel(truncate(classifierModel, 100));
        article.setPromptVersion(truncate(promptVersion, 20));
        article.setTaxonomyVersion(taxonomyVersion);
        article.setPublicationDate(new java.util.Date());
        Article saved = articleDAO.save(article);
        saveTagPercents(saved.getId(), ac);
        return saved.getId();
    }

    private int reclassify(int existingId, String finalUrl, String langCode, String region,
                            ArticleClassification ac, ParseResult parsed) {
        Optional<Article> opt = articleDAO.findById(existingId);
        if (opt.isEmpty()) {
            // Article was deleted since queue entry was created — create a new one
            return createNew(finalUrl, langCode, region, ac, parsed);
        }
        Article article = opt.get();
        article.setLink(finalUrl);
        if (parsed.title() != null && !parsed.title().isBlank()) {
            article.setTitle(truncate(parsed.title(), 500));
        }
        if (parsed.publisher() != null && !parsed.publisher().isBlank()) {
            article.setPublisher(truncate(parsed.publisher(), 255));
        }
        article.setArticleLanguage(isoToFull(langCode));
        article.setArticleRegion(region);
        article.setClassifierModel(truncate(classifierModel, 100));
        article.setPromptVersion(truncate(promptVersion, 20));
        article.setTaxonomyVersion(taxonomyVersion);
        articleDAO.update(article);
        deleteTagsForArticle(existingId);
        saveTagPercents(existingId, ac);
        return existingId;
    }

    private void deleteTagsForArticle(int articleId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            session.createNativeMutationQuery(
                    "DELETE FROM article_tag_percent WHERE article_id = :id")
                    .setParameter("id", articleId)
                    .executeUpdate();
            tx.commit();
        }
    }

    /**
     * Inserts article_tag_percent rows: one per category (percents sum to 100),
     * plus one row for the main subtopic (percent = its category's percent).
     */
    private void saveTagPercents(int articleId, ArticleClassification ac) {
        List<int[]> assignments = buildCategoryAssignments(ac); // [tagId, percent] pairs

        int mainPercent = 0;
        for (int[] pair : assignments) {
            if (pair[0] == ac.mainCategoryId()) {
                mainPercent = pair[1];
                break;
            }
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();

            for (int[] pair : assignments) {
                session.createNativeMutationQuery(
                        "INSERT INTO article_tag_percent (article_id, tag_id, percent) " +
                        "VALUES (:a, :t, :p)")
                        .setParameter("a", articleId)
                        .setParameter("t", pair[0])
                        .setParameter("p", pair[1])
                        .executeUpdate();
            }

            if (ac.mainSubtopicId() != null) {
                session.createNativeMutationQuery(
                        "INSERT INTO article_tag_percent (article_id, tag_id, percent) " +
                        "VALUES (:a, :t, :p)")
                        .setParameter("a", articleId)
                        .setParameter("t", ac.mainSubtopicId())
                        .setParameter("p", mainPercent)
                        .executeUpdate();
            }

            tx.commit();
        }
    }

    /**
     * Distributes 100% across categories using relative weights.
     * Main category → PRIMARY_WEIGHT; extras → their RelevanceLevel.weight.
     * Returns [tagId, percent] pairs summing to 100. Deduplicates by tagId.
     */
    private List<int[]> buildCategoryAssignments(ArticleClassification ac) {
        List<int[]> weighted = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();

        weighted.add(new int[]{ac.mainCategoryId(), PRIMARY_WEIGHT});
        seen.add(ac.mainCategoryId());

        for (ExtraAssignment ex : ac.extra()) {
            if (seen.add(ex.categoryId())) {
                weighted.add(new int[]{ex.categoryId(), ex.level().weight});
            }
        }

        int totalWeight = 0;
        for (int[] w : weighted) totalWeight += w[1];

        List<int[]> result = new ArrayList<>(weighted.size());
        int assigned = 0;
        for (int i = 0; i < weighted.size(); i++) {
            int tagId  = weighted.get(i)[0];
            int weight = weighted.get(i)[1];
            int percent;
            if (i == weighted.size() - 1) {
                percent = Math.max(1, 100 - assigned);
            } else {
                percent = Math.max(1, (int) Math.round(weight * 100.0 / totalWeight));
                assigned += percent;
            }
            result.add(new int[]{tagId, percent});
        }
        return result;
    }

    // ── Taxonomy / EntityMatcher loaders (used by production constructor) ─────

    private static EntityMatcher loadEntityMatcherFromDb() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return EntityMatcher.loadFromDatabase(session);
        }
    }

    private static List<TagInfo> loadTaxonomyFromDb() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return loadTaxonomyFrom(session);
        }
    }

    /** Package-private so QueueWorkerTest can reuse it. */
    static List<TagInfo> loadTaxonomyFrom(Session session) {
        List<Tag> all = session.createQuery(
                "SELECT DISTINCT t FROM Tag t LEFT JOIN FETCH t.parent " +
                "WHERE t.kind IN :kinds",
                Tag.class)
                .setParameter("kinds", List.of(TagKind.CATEGORY, TagKind.SUBTOPIC))
                .getResultList();

        Map<Integer, List<SubtopicInfo>> subtopicsByParent = new HashMap<>();
        for (Tag t : all) {
            if (t.getKind() == TagKind.SUBTOPIC && t.getParent() != null) {
                subtopicsByParent.computeIfAbsent(t.getParent().getId(), k -> new ArrayList<>())
                        .add(new SubtopicInfo(t.getId(), t.getName()));
            }
        }

        return all.stream()
                .filter(t -> t.getKind() == TagKind.CATEGORY)
                .map(t -> new TagInfo(t.getId(), t.getName(), t.getDescription(),
                        subtopicsByParent.getOrDefault(t.getId(), List.of())))
                .toList();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static String isoToFull(String code) {
        return switch (code.toLowerCase()) {
            case "en" -> "English";
            case "de" -> "German";
            case "ru" -> "Russian";
            case "ro" -> "Romanian";
            case "fr" -> "French";
            case "es" -> "Spanish";
            case "it" -> "Italian";
            case "uk" -> "Ukrainian";
            case "pt" -> "Portuguese";
            default  -> code;
        };
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
