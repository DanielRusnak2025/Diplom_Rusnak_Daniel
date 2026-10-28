package org.example.dao.impl;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.example.Article;
import org.example.dao.ArticleDAO;
import org.example.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.query.Query;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Реализация ArticleDAO.
 */
public class ArticleDAOImpl extends GenericDAOImpl<Article, Integer> implements ArticleDAO {

    public ArticleDAOImpl() {
        super(Article.class);
    }

    @Override
    public Optional<Article> findByLink(String link) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Article a WHERE a.link = :link";
            Query<Article> query = session.createQuery(hql, Article.class);
            query.setParameter("link", link);

            Article article = query.uniqueResult();
            return Optional.ofNullable(article);
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public List<Article> findRecentWithTags(int limit) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // Шаг 1: получаем ID последних N статей с SQL-level LIMIT (без JOIN FETCH)
            List<Integer> ids = session.createQuery(
                    "SELECT a.id FROM Article a ORDER BY a.publicationDate DESC",
                    Integer.class
            ).setMaxResults(limit).getResultList();

            if (ids.isEmpty()) return List.of();

            // Шаг 2: загружаем статьи с тегами только для найденных ID
            List<Article> articles = session.createQuery(
                    "SELECT DISTINCT a FROM Article a " +
                            "LEFT JOIN FETCH a.articleTagPercents atp " +
                            "LEFT JOIN FETCH atp.tag " +
                            "WHERE a.id IN :ids " +
                            "ORDER BY a.publicationDate DESC",
                    Article.class
            ).setParameter("ids", ids).getResultList();

            return articles;
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public List<Article> findByLanguage(String language) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Article a WHERE a.articleLanguage = :language";
            Query<Article> query = session.createQuery(hql, Article.class);
            query.setParameter("language", language);
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public List<Article> findByRegion(String region) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Article a WHERE a.articleRegion = :region";
            Query<Article> query = session.createQuery(hql, Article.class);
            query.setParameter("region", region);
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public List<Article> findByPublisher(String publisher) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Article a WHERE a.publisher = :publisher";
            Query<Article> query = session.createQuery(hql, Article.class);
            query.setParameter("publisher", publisher);
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public List<Article> findByPublicationDateAfter(Date date) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Article a WHERE a.publicationDate > :date ORDER BY a.publicationDate DESC";
            Query<Article> query = session.createQuery(hql, Article.class);
            query.setParameter("date", date);
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public List<Article> findMostRead(int limit) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Article a ORDER BY a.readCount DESC";
            Query<Article> query = session.createQuery(hql, Article.class);
            query.setMaxResults(limit);
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public List<Article> findByLanguageAndRegion(String language, String region) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Article a WHERE a.articleLanguage = :language AND a.articleRegion = :region";
            Query<Article> query = session.createQuery(hql, Article.class);
            query.setParameter("language", language);
            query.setParameter("region", region);
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public Optional<Article> findByTitle(String title) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Article a WHERE LOWER(a.title) = LOWER(:title)";
            Query<Article> query = session.createQuery(hql, Article.class);
            query.setParameter("title", title);
            query.setMaxResults(1);
            Article article = query.uniqueResult();
            return Optional.ofNullable(article);
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    @Override
    public List<Article> findByTitlePrefix(String prefix) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Article> query = session.createQuery(
                    "FROM Article a WHERE LOWER(a.title) LIKE LOWER(:prefix)",
                    Article.class);
            query.setParameter("prefix", prefix.toLowerCase() + "%");
            query.setMaxResults(5);
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public List<Article> findByIds(java.util.Collection<Integer> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "SELECT DISTINCT a FROM Article a " +
                    "LEFT JOIN FETCH a.articleTagPercents atp " +
                    "LEFT JOIN FETCH atp.tag " +
                    "WHERE a.id IN :ids " +
                    "ORDER BY a.publicationDate DESC",
                    Article.class
            ).setParameter("ids", ids).getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public List<Article> findOldUnread(int daysOld, int maxReadCount) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Date cutoff = Date.from(
                java.time.LocalDate.now()
                    .minusDays(daysOld)
                    .atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant()
            );
            String hql = "FROM Article a WHERE a.publicationDate < :cutoff AND a.readCount <= :maxReadCount";
            return session.createQuery(hql, Article.class)
                    .setParameter("cutoff", cutoff)
                    .setParameter("maxReadCount", maxReadCount)
                    .getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public int countNewerThan(int afterId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Long count = session.createQuery(
                    "SELECT COUNT(a) FROM Article a WHERE a.id > :afterId", Long.class)
                    .setParameter("afterId", afterId)
                    .uniqueResult();
            return count != null ? count.intValue() : 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    @Override
    public List<Integer> findIdsNewerThan(int afterId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "SELECT a.id FROM Article a WHERE a.id > :afterId", Integer.class)
                    .setParameter("afterId", afterId)
                    .getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public List<Article> findByKeyword(String keyword) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "FROM Article a WHERE LOWER(a.title) LIKE LOWER(:kw) " +
                    "OR LOWER(a.publisher) LIKE LOWER(:kw)",
                    Article.class)
                    .setParameter("kw", "%" + keyword + "%")
                    .getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public List<Article> searchByKeywordAndDate(String keyword, Date publicationDate) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<Article> cq = cb.createQuery(Article.class);
            Root<Article> root = cq.from(Article.class);

            List<Predicate> predicates = new ArrayList<>();

            if (keyword != null && !keyword.isEmpty()) {
                String pattern = "%" + escapeLike(keyword.toLowerCase()) + "%";
                Predicate titlePredicate     = cb.like(cb.lower(root.get("title")),     pattern, '!');
                Predicate publisherPredicate = cb.like(cb.lower(root.get("publisher")), pattern, '!');
                predicates.add(cb.or(titlePredicate, publisherPredicate));
            }

            if (publicationDate != null) {
                predicates.add(cb.equal(root.get("publicationDate"), publicationDate));
            }

            cq.where(predicates.toArray(new Predicate[0]));
            cq.orderBy(cb.desc(root.get("publicationDate")));

            return session.createQuery(cq).getResultList();
        }
    }

    /** Escapes LIKE special characters so user input is treated as a literal substring. */
    private static String escapeLike(String s) {
        return s.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}