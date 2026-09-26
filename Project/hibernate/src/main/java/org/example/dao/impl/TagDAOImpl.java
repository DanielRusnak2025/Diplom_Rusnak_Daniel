package org.example.dao.impl;

import org.example.Tag;
import org.example.dao.TagDAO;
import org.example.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.query.Query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Реализация TagDAO.
 */
public class TagDAOImpl extends GenericDAOImpl<Tag, Integer> implements TagDAO {

    public TagDAOImpl() {
        super(Tag.class);
    }

    @Override
    public Map<String, Tag> findByNames(List<String> names) {
        if (names == null || names.isEmpty()) return new HashMap<>();
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<Tag> tags = session.createQuery("FROM Tag t WHERE t.name IN :names", Tag.class)
                    .setParameter("names", names)
                    .getResultList();
            Map<String, Tag> map = new HashMap<>();
            for (Tag t : tags) map.put(t.getName().toLowerCase(), t);
            return map;
        } catch (Exception e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    @Override
    public Optional<Tag> findByName(String name) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM Tag t WHERE t.name = :name";
            Query<Tag> query = session.createQuery(hql, Tag.class);
            query.setParameter("name", name);

            Tag tag = query.uniqueResult();
            return Optional.ofNullable(tag);
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    @Override
    public List<Tag> findTagsWithUsers() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "SELECT DISTINCT t FROM Tag t JOIN t.userTagPercents";
            Query<Tag> query = session.createQuery(hql, Tag.class);
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public List<Tag> findTagsWithArticles() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "SELECT DISTINCT t FROM Tag t JOIN t.articleTagPercents";
            Query<Tag> query = session.createQuery(hql, Tag.class);
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public List<Tag> findMostPopularTags(int limit) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "SELECT t FROM Tag t LEFT JOIN t.articleTagPercents atp " +
                    "GROUP BY t.id ORDER BY COUNT(atp) DESC";
            Query<Tag> query = session.createQuery(hql, Tag.class);
            query.setMaxResults(limit);
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }
}