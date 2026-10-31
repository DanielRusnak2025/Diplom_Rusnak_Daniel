package org.example.dao;

import org.example.Report;
import org.example.Article;
import org.example.User;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * DAO для работы с сущностью Report.
 */
public interface ReportDAO extends GenericDAO<Report, Integer> {

    /**
     * Находит все отчёты для конкретной статьи.
     *
     * @param article статья
     * @return список отчётов
     */
    List<Report> findByArticle(Article article);

    /**
     * Находит все отчёты конкретного пользователя.
     *
     * @param user пользователь
     * @return список отчётов
     */
    List<Report> findByUser(User user);

    /**
     * Находит отчёты с определённым рейтингом.
     *
     * @param rating рейтинг (-1, 0, 1)
     * @return список отчётов
     */
    List<Report> findByRating(int rating);

    /**
     * Находит отчёты за определённый период.
     *
     * @param startDate начальная дата
     * @param endDate конечная дата
     * @return список отчётов
     */
    List<Report> findByDateRange(Date startDate, Date endDate);

    /**
     * Находит последние отчёты.
     *
     * @param limit максимальное количество результатов
     * @return список отчётов
     */
    List<Report> findLatestReports(int limit);

    /**
     * Подсчитывает средний рейтинг для статьи.
     *
     * @param article статья
     * @return средний рейтинг
     */
    Double calculateAverageRatingForArticle(Article article);

    Optional<Report> findByUserAndArticle(User user, Article article);

    /**
     * Возвращает множество ID статей, которые пользователь уже оценил.
     */
    Set<Integer> findRatedArticleIds(User user);

    /**
     * Возвращает карту articleId → процент лайков (rating=1 из всех оценок -1..1).
     * Жалобы (rating=-1 + reason_code IS NOT NULL) учитываются как дизлайки.
     * Статьи без оценок не включаются в результат.
     */
    Map<Integer, Integer> findLikePercentageByArticleIds(Collection<Integer> articleIds);
}