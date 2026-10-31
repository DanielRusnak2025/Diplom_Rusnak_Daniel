package org.example.service;

import org.example.Report;
import org.example.ReportReason;
import org.example.Article;
import org.example.User;
import org.example.dao.ReportDAO;
import org.example.dao.impl.ReportDAOImpl;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class ReportService {

    private final ReportDAO reportDAO;

    public ReportService() {
        this.reportDAO = new ReportDAOImpl();
    }

    public ReportService(ReportDAO reportDAO) {
        this.reportDAO = reportDAO;
    }

    public Report createReport(Article article, User user, int rating, ReportReason reasonCode) {
        if (rating < -1 || rating > 1) {
            throw new IllegalArgumentException("Rating must be -1, 0, or 1");
        }
        Report report = reasonCode != null
                ? new Report(article, user, rating, reasonCode)
                : new Report(article, user, rating);
        return reportDAO.save(report);
    }

    public void upsertRating(Article article, User user, int rating) {
        Optional<Report> existing = reportDAO.findByUserAndArticle(user, article);
        if (existing.isPresent()) {
            Report r = existing.get();
            r.setRating(rating);
            if (rating != -1) r.setReasonCode(null); // changing away from dislike clears complaint
            reportDAO.update(r);
        } else {
            reportDAO.save(new Report(article, user, rating));
        }
    }

    public Report updateReport(int reportId, int newRating, ReportReason newReasonCode) {
        Report report = reportDAO.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Отчёт не найден: " + reportId));

        if (newRating < -1 || newRating > 1) {
            throw new IllegalArgumentException("Rating must be -1, 0, or 1");
        }
        if (newReasonCode != null && newRating != -1) {
            throw new IllegalArgumentException("reasonCode can only be set when rating is -1");
        }

        report.setRating(newRating);
        report.setReasonCode(newRating == -1 ? newReasonCode : null);
        report.setDate(new Date());

        return reportDAO.update(report);
    }

    public Optional<Report> getReportById(int id) {
        return reportDAO.findById(id);
    }

    public List<Report> getAllReports() {
        return reportDAO.findAll();
    }

    public List<Report> getReportsByArticle(Article article) {
        return reportDAO.findByArticle(article);
    }

    public List<Report> getReportsByUser(User user) {
        return reportDAO.findByUser(user);
    }

    public List<Report> getReportsByRating(int rating) {
        return reportDAO.findByRating(rating);
    }

    public List<Report> getReportsByDateRange(Date startDate, Date endDate) {
        return reportDAO.findByDateRange(startDate, endDate);
    }

    public List<Report> getLatestReports(int limit) {
        return reportDAO.findLatestReports(limit);
    }

    public double getAverageRatingForArticle(Article article) {
        Double average = reportDAO.calculateAverageRatingForArticle(article);
        return average != null ? average : 0.0;
    }

    public long getTotalReports() {
        return reportDAO.count();
    }

    public Set<Integer> getRatedArticleIds(User user) {
        return reportDAO.findRatedArticleIds(user);
    }

    public Map<Integer, Integer> getUserRatingsMap(User user) {
        Map<Integer, Integer> map = new HashMap<>();
        for (Report r : reportDAO.findByUser(user)) {
            map.put(r.getArticle().getId(), r.getRating());
        }
        return map;
    }

    public Map<Integer, Integer> getLikePercentageMap(Collection<Integer> articleIds) {
        return reportDAO.findLikePercentageByArticleIds(articleIds);
    }

    public void deleteReport(int reportId) {
        reportDAO.deleteById(reportId);
    }
}
