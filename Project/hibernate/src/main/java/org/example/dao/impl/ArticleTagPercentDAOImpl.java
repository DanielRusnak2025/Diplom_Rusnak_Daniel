package org.example.dao.impl;

import org.example.ArticleTagPercent;
import org.example.ArticleTagId;
import org.example.Article;
import org.example.Tag;
import org.example.dao.ArticleTagPercentDAO;
import org.example.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.query.Query;

import java.util.List;
import java.util.Optional;

/**
 * Реализация ArticleTagPercentDAO.
 */
public class ArticleTagPercentDAOImpl extends GenericDAOImpl<ArticleTagPercent, ArticleTagId> implements ArticleTagPercentDAO {

    public ArticleTagPercentDAOImpl() {
        super(ArticleTagPercent.class);
    }

    @Override
    public List<ArticleTagPercent> findByArticle(Article article) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // JOIN FETCH atp.tag — загружаем тег сразу, иначе LazyInitializationException после закрытия сессии
            String hql = "SELECT atp FROM ArticleTagPercent atp JOIN FETCH atp.tag WHERE atp.article.id = :articleId ORDER BY atp.percent DESC";
            Query<ArticleTagPercent> query = session.createQuery(hql, ArticleTagPercent.class);
            query.setParameter("articleId", article.getId());
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public List<ArticleTagPercent> findByTag(Tag tag) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM ArticleTagPercent atp WHERE atp.tag = :tag";
            Query<ArticleTagPercent> query = session.createQuery(hql, ArticleTagPercent.class);
            query.setParameter("tag", tag);
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public Optional<ArticleTagPercent> findByArticleAndTag(Article article, Tag tag) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM ArticleTagPercent atp WHERE atp.article = :article AND atp.tag = :tag";
            Query<ArticleTagPercent> query = session.createQuery(hql, ArticleTagPercent.class);
            query.setParameter("article", article);
            query.setParameter("tag", tag);

            ArticleTagPercent result = query.uniqueResult();
            return Optional.ofNullable(result);
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    @Override
    public List<ArticleTagPercent> findTopTagsForArticle(Article article, int limit) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM ArticleTagPercent atp WHERE atp.article = :article ORDER BY atp.percent DESC";
            Query<ArticleTagPercent> query = session.createQuery(hql, ArticleTagPercent.class);
            query.setParameter("article", article);
            query.setMaxResults(limit);
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public List<ArticleTagPercent> findArticlesWithTagPercentAbove(Tag tag, int minPercent) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM ArticleTagPercent atp WHERE atp.tag = :tag AND atp.percent > :minPercent ORDER BY atp.percent DESC";
            Query<ArticleTagPercent> query = session.createQuery(hql, ArticleTagPercent.class);
            query.setParameter("tag", tag);
            query.setParameter("minPercent", minPercent);
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public List<Article> findArticlesByTags(List<Tag> tags) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // HQL для поиска статей, которые имеют ВСЕ указанные теги
            String hql = "SELECT atp.article FROM ArticleTagPercent atp " +
                    "WHERE atp.tag IN :tags " +
                    "GROUP BY atp.article " +
                    "HAVING COUNT(DISTINCT atp.tag) = :tagCount";

            Query<Article> query = session.createQuery(hql, Article.class);
            query.setParameter("tags", tags);
            query.setParameter("tagCount", (long) tags.size());

            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }
}