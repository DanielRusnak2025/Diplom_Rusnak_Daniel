package org.example.pipeline;

import org.example.Article;
import org.example.RejectReason;
import org.example.Tag;
import org.example.TagKind;
import org.example.TagKeyword;
import org.example.ai.AiConfig;
import org.example.ai.ArticleClassification;
import org.example.ai.ArticleClassifier;
import org.example.ai.ClassificationException;
import org.example.ai.ExtraAssignment;
import org.example.ai.OllamaClassifier;
import org.example.ai.RelevanceLevel;
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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

public class QueueWorker {

    private static final Logger log = Logger.getLogger(QueueWorker.class.getName());

    static final int LOCK_MINUTES = 10;
    static final int MAX_ATTEMPTS = 3;

    // Read from /seed/taxonomy.yaml once; written to article.taxonomy_version.
    static final int TAXONOMY_YAML_VERSION = loadTaxonomyYamlVersion();

    private static int loadTaxonomyYamlVersion() {
        try (InputStream is = QueueWorker.class.getResourceAsStream("/seed/taxonomy.yaml");
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("version:")) {
                    return Integer.parseInt(line.substring("version:".length()).trim());
                }
            }
        } catch (Exception ignored) {}
        return 1;
    }

    // Number of chars of article text used for keyword matching (title prepended separately).
    private static final int KEYWORD_TEXT_CHARS = 500;

    private final ArticleQueueDAO                       queueDAO;
    private final ArticleDAO                            articleDAO;
    private final ArticleClassifier                     classifier;
    private final PageLoader                            pageLoader;
    private final ArticleLanguageDetector               languageDetector;
    private final EntityMatcher                         entityMatcher;
    private final List<TagInfo>                         taxonomy;
    private final int                                   taxonomyVersion;
    private final String                                classifierModel;
    private final String                                promptVersion;
    // language-code → (category_id → keyword patterns from all subtopics of that category)
    private final Map<String, Map<Integer, List<String>>> keywordsByLang;
    private final int                                   maxBonus;
    private final Set<String>                           newsletterHosts;

    // ── Production constructor ────────────────────────────────────────────────

    public QueueWorker(ArticleClassifier classifier, PageLoader pageLoader) {
        this(
            classifier, pageLoader,
            new ArticleQueueDAOImpl(),
            new ArticleDAOImpl(),
            loadEntityMatcherFromDb(),
            loadTaxonomyFromDb(),
            loadKeywordsFromDb(),
            AiConfig.getKeywordBonusMax(),
            AiConfig.getOllamaModel(),
            OllamaClassifier.PROMPT_VERSION,
            new ArticleLanguageDetector(AiConfig.getLanguagesAllowed()),
            AiConfig.getNewsletterHosts()
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
            Map<String, Map<Integer, List<String>>> keywordsByLang,
            int maxBonus,
            String classifierModel,
            String promptVersion,
            ArticleLanguageDetector languageDetector,
            Set<String> newsletterHosts) {
        this.classifier       = classifier;
        this.pageLoader       = pageLoader;
        this.queueDAO         = queueDAO;
        this.articleDAO       = articleDAO;
        this.entityMatcher    = entityMatcher;
        this.taxonomy         = List.copyOf(taxonomy);
        this.taxonomyVersion  = TAXONOMY_YAML_VERSION;
        this.classifierModel  = classifierModel;
        this.promptVersion    = promptVersion;
        this.languageDetector = languageDetector;
        this.maxBonus         = maxBonus;
        this.newsletterHosts  = Set.copyOf(newsletterHosts);

        Map<String, Map<Integer, List<String>>> copy = new HashMap<>();
        for (Map.Entry<String, Map<Integer, List<String>>> e : keywordsByLang.entrySet()) {
            Map<Integer, List<String>> inner = new HashMap<>();
            for (Map.Entry<Integer, List<String>> ie : e.getValue().entrySet()) {
                inner.put(ie.getKey(), List.copyOf(ie.getValue()));
            }
            copy.put(e.getKey(), Collections.unmodifiableMap(inner));
        }
        this.keywordsByLang = Collections.unmodifiableMap(copy);
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

    private void processOne(ClaimedItem item) throws Exception {
        // 1. Fetch + parse page
        PageLoader.Result fetch;
        try {
            fetch = pageLoader.load(item.url());
        } catch (PageLoader.SsrfViolationException e) {
            queueDAO.markRejected(item.id(), RejectReason.INVALID_URL, item.lockToken());
            log.info("[QueueWorker] q=" + item.id() + " REJECTED INVALID_URL (SSRF): " + e.getMessage());
            return;
        }
        if (fetch == null) {
            queueDAO.markRejected(item.id(), RejectReason.FETCH_FAILED, item.lockToken());
            log.info("[QueueWorker] q=" + item.id() + " REJECTED FETCH_FAILED");
            return;
        }

        // 1b. Pre-filter (newsletter host, root URL, soft-404, og-type, too-short — no Ollama call needed)
        String skipReason = ArticlePreFilter.check(fetch.parsed(), fetch.finalUrl(), fetch.httpStatus(), newsletterHosts);
        if (skipReason != null) {
            RejectReason rr = switch (skipReason) {
                case "root-url"        -> RejectReason.ROOT_URL;
                case "not-found"       -> RejectReason.NOT_FOUND;
                case "newsletter-host"  -> RejectReason.NEWSLETTER_HOST;
                case "dw-section-page" -> RejectReason.DW_SECTION_PAGE;
                case "og-type"         -> RejectReason.NOT_ARTICLE;
                default                -> RejectReason.TEXT_TOO_SHORT;
            };
            queueDAO.markRejected(item.id(), rr, item.lockToken());
            log.info("[QueueWorker] q=" + item.id() + " REJECTED " + rr + " (" + skipReason + ")");
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
            if (existingByHash.isPresent()) {
                queueDAO.markRejected(item.id(), RejectReason.DUPLICATE, item.lockToken());
                log.info("[QueueWorker] q=" + item.id()
                         + " REJECTED DUPLICATE (new; article_id=" + existingByHash.get().getId() + ")");
                return;
            }
        } else {
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
                ? reclassify(item.articleId(), finalUrl, langCode, region, ac,
                             fetch.parsed(), fetch.parsed().textForAi())
                : createNew(finalUrl, langCode, region, ac,
                            fetch.parsed(), fetch.parsed().textForAi());

        queueDAO.markDone(item.id(), articleId, item.lockToken());
        log.info("[QueueWorker] q=" + item.id() + " DONE article_id=" + articleId);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private int createNew(String finalUrl, String langCode, String region,
                          ArticleClassification ac, ParseResult parsed, String textForAi) {
        Article article = new Article();
        article.setLink(finalUrl);
        article.setTitle(truncate(parsed.title(), 500));
        article.setPublisher(truncate(parsed.publisher(), 255));
        article.setArticleLanguage(langCode);
        article.setArticleRegion(region);
        article.setClassifierModel(truncate(classifierModel, 100));
        article.setPromptVersion(truncate(promptVersion, 20));
        article.setTaxonomyVersion(taxonomyVersion);
        article.setPublicationDate(parseDateOrNow(parsed.publishedDate()));
        Article saved = articleDAO.save(article);
        saveTagPercents(saved.getId(), ac, langCode, parsed.title(), textForAi);
        return saved.getId();
    }

    private int reclassify(int existingId, String finalUrl, String langCode, String region,
                            ArticleClassification ac, ParseResult parsed, String textForAi) {
        Optional<Article> opt = articleDAO.findById(existingId);
        if (opt.isEmpty()) {
            return createNew(finalUrl, langCode, region, ac, parsed, textForAi);
        }
        Article article = opt.get();
        article.setLink(finalUrl);
        if (parsed.title() != null && !parsed.title().isBlank()) {
            article.setTitle(truncate(parsed.title(), 500));
        }
        if (parsed.publisher() != null && !parsed.publisher().isBlank()) {
            article.setPublisher(truncate(parsed.publisher(), 255));
        }
        article.setArticleLanguage(langCode);
        article.setArticleRegion(region);
        article.setClassifierModel(truncate(classifierModel, 100));
        article.setPromptVersion(truncate(promptVersion, 20));
        article.setTaxonomyVersion(taxonomyVersion);
        article.setPublicationDate(parseDateOrNow(parsed.publishedDate()));
        articleDAO.update(article);
        deleteTagsForArticle(existingId);
        saveTagPercents(existingId, ac, langCode, parsed.title(), textForAi);
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
     * Inserts article_tag_percent rows using ArticleScorer.
     * One row per category (percents sum to 100), plus one row for the main subtopic
     * (percent = its parent category's percent).
     *
     * Keyword bonus: for each category, counts matching keyword rules from all its
     * subtopics in the article's language (title + first KEYWORD_TEXT_CHARS of text).
     */
    private void saveTagPercents(int articleId, ArticleClassification ac,
                                 String langCode, String title, String textForAi) {
        Map<Integer, RelevanceLevel> assignments = buildAssignments(ac);

        Map<Integer, List<String>> kwForLang =
                keywordsByLang.getOrDefault(langCode, Map.of());

        String snippet = (title != null ? title + " " : "") +
                (textForAi != null && textForAi.length() > KEYWORD_TEXT_CHARS
                        ? textForAi.substring(0, KEYWORD_TEXT_CHARS)
                        : (textForAi != null ? textForAi : ""));
        String normalizedText = KeywordMatcher.normalize(snippet);

        Map<Integer, Integer> percents =
                ArticleScorer.score(assignments, kwForLang, normalizedText, maxBonus);

        int mainPercent = percents.getOrDefault(ac.mainCategoryId(), 0);

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();

            for (Map.Entry<Integer, Integer> e : percents.entrySet()) {
                session.createNativeMutationQuery(
                        "INSERT INTO article_tag_percent (article_id, tag_id, percent) " +
                        "VALUES (:a, :t, :p)")
                        .setParameter("a", articleId)
                        .setParameter("t", e.getKey())
                        .setParameter("p", e.getValue())
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
     * Builds RelevanceLevel assignment map from ArticleClassification.
     * Main category → HIGH; extras → their own level. Extras that duplicate the main are ignored.
     */
    private static Map<Integer, RelevanceLevel> buildAssignments(ArticleClassification ac) {
        Map<Integer, RelevanceLevel> assignments = new LinkedHashMap<>();
        assignments.put(ac.mainCategoryId(), RelevanceLevel.HIGH);
        for (ExtraAssignment ex : ac.extra()) {
            assignments.putIfAbsent(ex.categoryId(), ex.level());
        }
        return assignments;
    }

    // ── Static loaders (taxonomy, entity matcher, keywords — once per processQueue) ──

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

    private static Map<String, Map<Integer, List<String>>> loadKeywordsFromDb() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return loadKeywordsFrom(session);
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

    /**
     * Package-private so QueueWorkerTest can reuse it.
     * Returns: language-code → (category_id → keyword patterns from all subtopics).
     */
    static Map<String, Map<Integer, List<String>>> loadKeywordsFrom(Session session) {
        List<TagKeyword> all = session.createQuery(
                "SELECT tk FROM TagKeyword tk JOIN FETCH tk.tag t LEFT JOIN FETCH t.parent",
                TagKeyword.class)
                .getResultList();

        Map<String, Map<Integer, List<String>>> result = new HashMap<>();
        for (TagKeyword tk : all) {
            Tag subtopic = tk.getTag();
            Tag parent   = subtopic.getParent();
            if (parent == null) continue;
            int    catId = parent.getId();
            String lang  = tk.getLanguage().toLowerCase();
            result.computeIfAbsent(lang, k -> new HashMap<>())
                  .computeIfAbsent(catId, k -> new ArrayList<>())
                  .add(tk.getPattern());
        }
        return result;
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static Date parseDateOrNow(String iso) {
        if (iso != null && !iso.isBlank()) {
            try {
                return Date.from(OffsetDateTime.parse(iso).toInstant());
            } catch (DateTimeParseException ignored) {}
            try {
                return Date.from(LocalDate.parse(iso).atStartOfDay(ZoneOffset.UTC).toInstant());
            } catch (DateTimeParseException ignored) {}
        }
        return new Date();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
