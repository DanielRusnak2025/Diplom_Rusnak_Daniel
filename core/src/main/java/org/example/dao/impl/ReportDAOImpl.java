package org.example.dao.impl;

import org.example.Report;
import org.example.Article;
import org.example.User;
import org.example.dao.ReportDAO;
import org.example.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.query.Query;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Реализация ReportDAO.
 */
public class ReportDAOImpl extends GenericDAOImpl<Report, Integer> implements ReportDAO {

    public ReportDAOImpl() {
        super(Report.class);
    }

    @Override
    public List<Report> findByArticle(Article article) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // JOIN FETCH r.user — загружаем пользователя сразу, иначе LazyInitializationException
            String hql = "SELECT r FROM Report r JOIN FETCH r.user WHERE r.article.id = :articleId ORDER BY r.date DESC";
            Query<Report> query = session.createQuery(hql, Report.class);
            query.setParameter("articleId", article.getId());
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public List<Report> findByUser(User user) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // JOIN FETCH r.article — нужен для отображения заголовка в SearchByUsername
            String hql = "SELECT r FROM Report r JOIN FETCH r.article " +
                         "WHERE r.user.nick = :nick ORDER BY r.date DESC";
            Query<Report> query = session.createQuery(hql, Report.class);
            query.setParameter("nick", user.getNick());
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public List<Report> findByRating(int rating) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Report r WHERE r.rating = :rating";
            Query<Report> query = session.createQuery(hql, Report.class);
            query.setParameter("rating", rating);
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public List<Report> findByDateRange(Date startDate, Date endDate) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Report r WHERE r.date BETWEEN :startDate AND :endDate ORDER BY r.date DESC";
            Query<Report> query = session.createQuery(hql, Report.class);
            query.setParameter("startDate", startDate);
            query.setParameter("endDate", endDate);
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public List<Report> findLatestReports(int limit) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Report r ORDER BY r.date DESC";
            Query<Report> query = session.createQuery(hql, Report.class);
            query.setMaxResults(limit);
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    // В ReportDAOImpl.java добавь этот метод — он переопределяет findAll() из GenericDAOImpl
// и подгружает article и user сразу через JOIN FETCH

    @Override
    public List<Report> findAll() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "SELECT r FROM Report r " +
                    "JOIN FETCH r.article " +
                    "JOIN FETCH r.user " +
                    "ORDER BY r.date DESC";
            return session.createQuery(hql, Report.class).getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public Double calculateAverageRatingForArticle(Article article) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "SELECT AVG(r.rating) FROM Report r WHERE r.article = :article";
            Query<Double> query = session.createQuery(hql, Double.class);
            query.setParameter("article", article);

            Double average = query.uniqueResult();
            return average != null ? average : 0.0;
        } catch (Exception e) {
            e.printStackTrace();
            return 0.0;
        }
    }

    @Override
    public Set<Integer> findRatedArticleIds(User user) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "SELECT r.article.id FROM Report r WHERE r.user.nick = :nick";
            List<Integer> ids = session.createQuery(hql, Integer.class)
                    .setParameter("nick", user.getNick())
                    .getResultList();
            return new HashSet<>(ids);
        } catch (Exception e) {
            e.printStackTrace();
            return new HashSet<>();
        }
    }

    @Override
    public Map<Integer, Integer> findLikePercentageByArticleIds(Collection<Integer> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) return new HashMap<>();
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // Считаем лайки (rating=1) и все оценки (rating!=-2) по группе статей
            String hql = "SELECT r.article.id, " +
                         "SUM(CASE WHEN r.rating = 1 THEN 1 ELSE 0 END), " +
                         "COUNT(r) " +
                         "FROM Report r " +
                         "WHERE r.article.id IN :ids AND r.rating != -2 " +
                         "GROUP BY r.article.id";
            List<Object[]> rows = session.createQuery(hql, Object[].class)
                    .setParameter("ids", articleIds)
                    .getResultList();
            Map<Integer, Integer> result = new HashMap<>();
            for (Object[] row : rows) {
                Integer id    = (Integer) row[0];
                Long    likes = ((Number) row[1]).longValue();
                Long    total = ((Number) row[2]).longValue();
                if (total > 0) {
                    result.put(id, (int)(likes * 100L / total));
                }
            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    @Override
    public Optional<Report> findByUserAndArticle(User user, Article article) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "SELECT r FROM Report r JOIN FETCH r.user JOIN FETCH r.article " +
                         "WHERE r.user.nick = :nick AND r.article.id = :articleId";
            Query<Report> query = session.createQuery(hql, Report.class);
            query.setParameter("nick", user.getNick());
            query.setParameter("articleId", article.getId());
            query.setMaxResults(1);
            return Optional.ofNullable(query.uniqueResult());
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }
}