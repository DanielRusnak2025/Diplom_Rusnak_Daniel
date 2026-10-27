package org.example.service;

import org.example.Report;
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

/**
 * Сервис для работы с отчётами (оценками статей).
 */
public class ReportService {

    private final ReportDAO reportDAO;

    public ReportService() {
        this.reportDAO = new ReportDAOImpl();
    }

    public ReportService(ReportDAO reportDAO) {
        this.reportDAO = reportDAO;
    }

    // ═══════════════════════════════════════════════════════════
    // Создание и управление отчётами
    // ═══════════════════════════════════════════════════════════

    /**
     * Создаёт новый отчёт (оценку статьи пользователем).
     *
     * @param article статья
     * @param user пользователь
     * @param rating рейтинг от -2 до 1
     * @param reason причина оценки (может быть null)
     */
    public Report createReport(Article article, User user, int rating, String reason) {
        // Проверяем допустимость рейтинга
        if (rating < -2 || rating > 1) {
            throw new IllegalArgumentException("Рейтинг должен быть от -2 до 1");
        }

        Report report = new Report(article, user, rating, reason);
        return reportDAO.save(report);
    }

    /**
     * Создаёт или обновляет оценку пользователя для статьи (upsert).
     * rating: 1=нравится, 0=нейтрально, -1=не нравится, -2=жалоба
     */
    public void upsertRating(Article article, User user, int rating) {
        Optional<Report> existing = reportDAO.findByUserAndArticle(user, article);
        if (existing.isPresent()) {
            Report r = existing.get();
            r.setRating(rating);
            reportDAO.update(r);
        } else {
            reportDAO.save(new Report(article, user, rating, null));
        }
    }

    /**
     * Обновляет существующий отчёт.
     */
    public Report updateReport(int reportId, int newRating, String newReason) {
        Report report = reportDAO.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Отчёт не найден: " + reportId));

        if (newRating < -2 || newRating > 1) {
            throw new IllegalArgumentException("Рейтинг должен быть от -2 до 1");
        }

        report.setRating(newRating);
        if (newReason != null) {
            report.setReason(newReason);
        }
        report.setDate(new Date()); // обновляем дату

        return reportDAO.update(report);
    }

    // ═══════════════════════════════════════════════════════════
    // Поиск и получение данных
    // ═══════════════════════════════════════════════════════════

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

    /**
     * Вычисляет средний рейтинг для статьи.
     */
    public double getAverageRatingForArticle(Article article) {
        Double average = reportDAO.calculateAverageRatingForArticle(article);
        return average != null ? average : 0.0;
    }

    public long getTotalReports() {
        return reportDAO.count();
    }

    /**
     * Возвращает множество ID статей, уже оценённых данным пользователем.
     */
    public Set<Integer> getRatedArticleIds(User user) {
        return reportDAO.findRatedArticleIds(user);
    }

    /**
     * Возвращает карту articleId → rating для всех оценок пользователя.
     * Используется для отображения значков оценок в экране истории.
     */
    public Map<Integer, Integer> getUserRatingsMap(User user) {
        Map<Integer, Integer> map = new HashMap<>();
        for (Report r : reportDAO.findByUser(user)) {
            map.put(r.getArticle().getId(), r.getRating());
        }
        return map;
    }

    /**
     * Возвращает карту articleId → процент лайков для списка статей.
     * Используется для отображения значка "популярная статья" в ленте.
     */
    public Map<Integer, Integer> getLikePercentageMap(Collection<Integer> articleIds) {
        return reportDAO.findLikePercentageByArticleIds(articleIds);
    }

    // ═══════════════════════════════════════════════════════════
    // Удаление
    // ═══════════════════════════════════════════════════════════

    public void deleteReport(int reportId) {
        reportDAO.deleteById(reportId);
    }
}