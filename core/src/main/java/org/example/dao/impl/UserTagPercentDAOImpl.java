package org.example.dao.impl;

import org.example.UserTagPercent;
import org.example.UserTagId;
import org.example.User;
import org.example.Tag;
import org.example.dao.UserTagPercentDAO;
import org.example.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.query.Query;

import java.util.List;
import java.util.Optional;

/**
 * Реализация UserTagPercentDAO.
 */
public class UserTagPercentDAOImpl extends GenericDAOImpl<UserTagPercent, UserTagId> implements UserTagPercentDAO {

    public UserTagPercentDAOImpl() {
        super(UserTagPercent.class);
    }

    @Override
    public List<UserTagPercent> findByUser(User user) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // JOIN FETCH utp.tag — загружаем тег сразу, иначе LazyInitializationException
            String hql = "SELECT utp FROM UserTagPercent utp JOIN FETCH utp.tag " +
                         "WHERE utp.user.nick = :nick ORDER BY utp.percent DESC";
            Query<UserTagPercent> query = session.createQuery(hql, UserTagPercent.class);
            query.setParameter("nick", user.getNick());
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public List<UserTagPercent> findByTag(Tag tag) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // JOIN FETCH utp.user — загружаем пользователя сразу
            String hql = "SELECT utp FROM UserTagPercent utp JOIN FETCH utp.user " +
                         "WHERE utp.tag = :tag";
            Query<UserTagPercent> query = session.createQuery(hql, UserTagPercent.class);
            query.setParameter("tag", tag);
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public Optional<UserTagPercent> findByUserAndTag(User user, Tag tag) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM UserTagPercent utp WHERE utp.user = :user AND utp.tag = :tag";
            Query<UserTagPercent> query = session.createQuery(hql, UserTagPercent.class);
            query.setParameter("user", user);
            query.setParameter("tag", tag);

            UserTagPercent result = query.uniqueResult();
            return Optional.ofNullable(result);
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    @Override
    public List<UserTagPercent> findTopTagsForUser(User user, int limit) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "SELECT utp FROM UserTagPercent utp JOIN FETCH utp.tag " +
                         "WHERE utp.user.nick = :nick ORDER BY utp.percent DESC";
            Query<UserTagPercent> query = session.createQuery(hql, UserTagPercent.class);
            query.setParameter("nick", user.getNick());
            query.setMaxResults(limit);
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public List<UserTagPercent> findUsersWithTagPercentAbove(Tag tag, int minPercent) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM UserTagPercent utp WHERE utp.tag = :tag AND utp.percent > :minPercent ORDER BY utp.percent DESC";
            Query<UserTagPercent> query = session.createQuery(hql, UserTagPercent.class);
            query.setParameter("tag", tag);
            query.setParameter("minPercent", minPercent);
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }
}