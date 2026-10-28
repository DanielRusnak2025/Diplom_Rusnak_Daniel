package org.example.dao.impl;

import org.example.User;
import org.example.dao.UserDAO;
import org.example.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.query.Query;

import java.util.List;
import java.util.Optional;

/**
 * Реализация UserDAO.
 * Наследует GenericDAOImpl, поэтому базовые CRUD методы уже работают.
 */
public class UserDAOImpl extends GenericDAOImpl<User, Integer> implements UserDAO {

    /**
     * Конструктор передаёт User.class в родительский класс.
     */
    public UserDAOImpl() {
        super(User.class);
    }

    // ═══════════════════════════════════════════════════════════
    // Специфичные методы для User
    // ═══════════════════════════════════════════════════════════

    @Override
    public Optional<User> findByEmail(String email) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // HQL запрос с параметром
            String hql = "FROM User u WHERE u.email = :email";
            Query<User> query = session.createQuery(hql, User.class);
            query.setParameter("email", email); // подставляем значение

            User user = query.uniqueResult(); // получаем один результат (или null)
            return Optional.ofNullable(user);
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    @Override
    public List<User> findAllAdmins() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM User u WHERE u.isAdmin = true";
            Query<User> query = session.createQuery(hql, User.class);
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of(); // возвращаем пустой список при ошибке
        }
    }

    @Override
    public List<User> findByPreferredRegion(String region) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM User u WHERE u.preferredRegion = :region";
            Query<User> query = session.createQuery(hql, User.class);
            query.setParameter("region", region);
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public List<User> findByReadArticlesGreaterThan(int minCount) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM User u WHERE u.readArticlesCount > :minCount ORDER BY u.readArticlesCount DESC";
            Query<User> query = session.createQuery(hql, User.class);
            query.setParameter("minCount", minCount);
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @Override
    public Optional<User> findByNick(String nick) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return Optional.ofNullable(
                session.createQuery("FROM User u WHERE u.nick = :nick", User.class)
                       .setParameter("nick", nick)
                       .uniqueResult());
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }
}