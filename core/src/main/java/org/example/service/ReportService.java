package org.example.service;

import org.example.*;
import org.example.dao.ArticleDAO;
import org.example.dao.ReportDAO;
import org.example.dao.UserDAO;
import org.example.dao.impl.ArticleDAOImpl;
import org.example.dao.impl.ReportDAOImpl;
import org.example.dao.impl.UserDAOImpl;
import org.example.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ReportService {

    private final ReportDAO reportDAO;
    private final ArticleDAO articleDAO;
    private final UserDAO userDAO;

    public ReportService() {
        this.reportDAO = new ReportDAOImpl();
        this.articleDAO = new ArticleDAOImpl();
        this.userDAO = new UserDAOImpl();
    }

    public ReportService(ReportDAO reportDAO, ArticleDAO articleDAO, UserDAO userDAO) {
        this.reportDAO = reportDAO;
        this.articleDAO = articleDAO;
        this.userDAO = userDAO;
    }

    // ──────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────

    private Article requireArticle(int articleId) {
        return articleDAO.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("Article not found: " + articleId));
    }

    private User requireUser(int userId) {
        return userDAO.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    // ──────────────────────────────────────────────────────────────
    // Core operations
    // ──────────────────────────────────────────────────────────────

    public Report createReport(int articleId, int userId, int rating, ReportReason reasonCode) {
        if (rating < -1 || rating > 1) throw new IllegalArgumentException("Rating must be -1, 0, or 1");
        Article article = requireArticle(articleId);
        User user = requireUser(userId);
        Report report = reasonCode != null
                ? new Report(article, user, rating, reasonCode)
                : new Report(article, user, rating);
        return reportDAO.save(report);
    }

    /**
     * Upserts a rating for an article, all in one transaction.
     * Same rating → no-op. New report → save + weights (1 step, direction=rating).
     * Changed rating → update report + apply |new−old| steps (variant B):
     *   like→dislike = 2 steps of −1, like→neutral = 1 step of −1,
     *   dislike→like = 2 steps of +1, neutral→like/dislike = 1 step.
     * Rollback is approximate for multi-step due to smoothClamp non-linearity.
     */
    public void upsertRating(int articleId, int userId, int rating) {
        if (rating < -1 || rating > 1) throw new IllegalArgumentException("Rating must be -1, 0, or 1");
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                Article article = session.find(Article.class, articleId);
                if (article == null) throw new IllegalArgumentException("Article not found: " + articleId);
                User user = session.find(User.class, userId);
                if (user == null) throw new IllegalArgumentException("User not found: " + userId);

                Report existing = session.createQuery(
                        "FROM Report r WHERE r.user.id = :uid AND r.article.id = :aid", Report.class)
                        .setParameter("uid", userId).setParameter("aid", articleId)
                        .uniqueResultOptional().orElse(null);

                int stepDir, steps;
                if (existing == null) {
                    session.persist(new Report(article, user, rating));
                    stepDir = rating;
                    steps = rating != 0 ? 1 : 0;
                } else {
                    int oldRating = existing.getRating();
                    if (oldRating == rating) { tx.rollback(); return; }
                    existing.setRating(rating);
                    if (rating != -1) existing.setReasonCode(null);
                    int diff = rating - oldRating;
                    stepDir = Integer.signum(diff);
                    steps = Math.abs(diff);
                }

                if (steps > 0) TagPercentService.applyWeightSteps(session, articleId, userId, stepDir, steps);
                tx.commit();
            } catch (Exception e) {
                if (tx.isActive()) tx.rollback();
                throw e instanceof RuntimeException re ? re : new RuntimeException(e);
            }
        }
    }

    /**
     * Creates or updates a complaint (rating=-1, reasonCode), creates a user block when the
     * reason implies one, and adjusts tag weights — all in a single transaction.
     *
     * Weight steps: no prior rating → 1 step of −1. Prior rating was already −1 → no weight
     * change (only reason updated). Prior rating 0 → 1 step of −1. Prior rating 1 → 2 steps of −1.
     */
    public void complain(int articleId, int userId, ReportReason reason) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                Article article = session.find(Article.class, articleId);
                if (article == null) throw new IllegalArgumentException("Article not found: " + articleId);
                User user = session.find(User.class, userId);
                if (user == null) throw new IllegalArgumentException("User not found: " + userId);

                // 1. Upsert report; compute weight steps
                Report existing = session.createQuery(
                        "FROM Report r WHERE r.user.id = :uid AND r.article.id = :aid", Report.class)
                        .setParameter("uid", userId).setParameter("aid", articleId)
                        .uniqueResultOptional().orElse(null);
                int steps;
                if (existing == null) {
                    session.persist(new Report(article, user, -1, reason));
                    steps = 1;
                } else {
                    int oldRating = existing.getRating();
                    existing.setRating(-1);
                    existing.setReasonCode(reason);
                    // already dislike: only reason changed, weights unchanged
                    steps = (oldRating == -1) ? 0 : Math.abs(-1 - oldRating);
                }

                // 2. Block (check-before-insert to stay clear of UNIQUE violation)
                UserBlock block = buildBlock(session, user, article, reason);
                if (block != null) session.persist(block);

                // 3. Weight adjustment via shared helper (direction always −1)
                if (steps > 0) TagPercentService.applyWeightSteps(session, articleId, userId, -1, steps);

                tx.commit();
            } catch (Exception e) {
                if (tx.isActive()) tx.rollback();
                throw e instanceof RuntimeException re ? re : new RuntimeException(e);
            }
        }
    }

    private UserBlock buildBlock(Session session, User user, Article article, ReportReason reason) {
        int uid = user.getId();
        return switch (reason) {
            case UNWANTED_REGION -> {
                String regionKey = article.getArticleRegion();
                if (regionKey == null || regionKey.isBlank()) yield null;
                Region region = session.find(Region.class, regionKey);
                if (region == null) yield null;
                long cnt = session.createQuery(
                        "SELECT COUNT(ub) FROM UserBlock ub " +
                        "WHERE ub.user.id = :uid AND ub.blockType = :bt AND ub.region = :r",
                        Long.class)
                        .setParameter("uid", uid).setParameter("bt", BlockType.REGION)
                        .setParameter("r", region).uniqueResult();
                if (cnt > 0) yield null;
                UserBlock b = new UserBlock();
                b.setUser(user); b.setBlockType(BlockType.REGION); b.setRegion(region);
                yield b;
            }
            case UNWANTED_PUBLISHER -> {
                String pub = article.getPublisher();
                if (pub == null || pub.isBlank()) yield null;
                long cnt = session.createQuery(
                        "SELECT COUNT(ub) FROM UserBlock ub " +
                        "WHERE ub.user.id = :uid AND ub.blockType = :bt AND ub.publisher = :pub",
                        Long.class)
                        .setParameter("uid", uid).setParameter("bt", BlockType.PUBLISHER)
                        .setParameter("pub", pub).uniqueResult();
                if (cnt > 0) yield null;
                UserBlock b = new UserBlock();
                b.setUser(user); b.setBlockType(BlockType.PUBLISHER); b.setPublisher(pub);
                yield b;
            }
            case NOT_INTERESTED_TOPIC -> {
                ArticleTagPercent top = session.createQuery(
                        "FROM ArticleTagPercent atp JOIN FETCH atp.tag t " +
                        "WHERE atp.article.id = :aid AND t.kind = :kind ORDER BY atp.percent DESC",
                        ArticleTagPercent.class)
                        .setParameter("aid", article.getId()).setParameter("kind", TagKind.CATEGORY)
                        .setMaxResults(1).uniqueResultOptional().orElse(null);
                if (top == null) yield null;
                Tag tag = top.getTag();
                long cnt = session.createQuery(
                        "SELECT COUNT(ub) FROM UserBlock ub " +
                        "WHERE ub.user.id = :uid AND ub.blockType = :bt AND ub.tag.id = :tid",
                        Long.class)
                        .setParameter("uid", uid).setParameter("bt", BlockType.TAG)
                        .setParameter("tid", tag.getId()).uniqueResult();
                if (cnt > 0) yield null;
                UserBlock b = new UserBlock();
                b.setUser(user); b.setBlockType(BlockType.TAG); b.setTag(tag);
                yield b;
            }
            default -> null;
        };
    }

    // ──────────────────────────────────────────────────────────────
    // Read / update operations
    // ──────────────────────────────────────────────────────────────

    public Report updateReport(int reportId, int newRating, ReportReason newReasonCode) {
        Report report = reportDAO.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Отчёт не найден: " + reportId));
        if (newRating < -1 || newRating > 1) throw new IllegalArgumentException("Rating must be -1, 0, or 1");
        if (newReasonCode != null && newRating != -1)
            throw new IllegalArgumentException("reasonCode can only be set when rating is -1");
        report.setRating(newRating);
        report.setReasonCode(newRating == -1 ? newReasonCode : null);
        report.setDate(new Date());
        return reportDAO.update(report);
    }

    public Optional<Report> getReportById(int id) { return reportDAO.findById(id); }

    public List<Report> getAllReports() { return reportDAO.findAll(); }

    public List<Report> getReportsByArticle(int articleId) {
        return reportDAO.findByArticle(requireArticle(articleId));
    }

    public List<Report> getReportsByUser(int userId) {
        return reportDAO.findByUser(requireUser(userId));
    }

    public List<Report> getReportsByRating(int rating) { return reportDAO.findByRating(rating); }

    public List<Report> getReportsByDateRange(Date startDate, Date endDate) {
        return reportDAO.findByDateRange(startDate, endDate);
    }

    public List<Report> getLatestReports(int limit) { return reportDAO.findLatestReports(limit); }

    public double getAverageRatingForArticle(int articleId) {
        Double avg = reportDAO.calculateAverageRatingForArticle(requireArticle(articleId));
        return avg != null ? avg : 0.0;
    }

    public long getTotalReports() { return reportDAO.count(); }

    public Set<Integer> getRatedArticleIds(int userId) {
        return reportDAO.findRatedArticleIds(requireUser(userId));
    }

    public Map<Integer, Integer> getUserRatingsMap(int userId) {
        Map<Integer, Integer> map = new HashMap<>();
        for (Report r : reportDAO.findByUser(requireUser(userId))) {
            map.put(r.getArticle().getId(), r.getRating());
        }
        return map;
    }

    public Map<Integer, Integer> getLikePercentageMap(Collection<Integer> articleIds) {
        return reportDAO.findLikePercentageByArticleIds(articleIds);
    }

    public void deleteReport(int reportId) { reportDAO.deleteById(reportId); }
}
