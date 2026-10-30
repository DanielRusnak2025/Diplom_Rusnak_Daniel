package org.example.dao.impl;

import org.example.PasswordHistory;
import org.example.Role;
import org.example.User;
import org.example.dao.UserDAO;
import org.example.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class UserDAOImpl extends GenericDAOImpl<User, Integer> implements UserDAO {

    public UserDAOImpl() {
        super(User.class);
    }

    // ═══════════════════════════════════════════════════════════
    // Password management — single-transaction methods
    // ═══════════════════════════════════════════════════════════

    @Override
    public User saveWithHistory(User user) {
        LocalDateTime now = LocalDateTime.now();
        user.setPasswordChangedAt(now);
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            session.persist(user);
            session.persist(new PasswordHistory(user, user.getPassword(), now));
            tx.commit();
            return user;
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            throw new RuntimeException("saveWithHistory failed", e);
        }
    }

    @Override
    public void changePassword(int userId, String newHash, LocalDateTime now) {
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            User user = session.find(User.class, userId);
            if (user == null) throw new IllegalArgumentException("User not found: " + userId);
            user.setPassword(newHash);
            user.setPasswordChangedAt(now);
            session.persist(new PasswordHistory(user, newHash, now));
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            throw new RuntimeException("changePassword failed for userId=" + userId, e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Специфичные методы для User
    // ═══════════════════════════════════════════════════════════

    @Override
    public Optional<User> findByEmail(String email) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM User u WHERE u.email = :email";
            Query<User> query = session.createQuery(hql, User.class);
            query.setParameter("email", email);
            return Optional.ofNullable(query.uniqueResult());
        } catch (Exception e) {
            throw new RuntimeException("findByEmail failed", e);
        }
    }

    @Override
    public List<User> findByRole(Role role) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("FROM User u WHERE u.role = :role", User.class)
                    .setParameter("role", role)
                    .getResultList();
        } catch (Exception e) {
            throw new RuntimeException("findByRole failed for role=" + role, e);
        }
    }

    @Override
    public List<User> findByPreferredRegion(String region) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM User u WHERE u.preferredRegion = :region";
            return session.createQuery(hql, User.class)
                    .setParameter("region", region)
                    .getResultList();
        } catch (Exception e) {
            throw new RuntimeException("findByPreferredRegion failed", e);
        }
    }

    @Override
    public List<User> findByReadArticlesGreaterThan(int minCount) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            String hql = "FROM User u WHERE u.readArticlesCount > :minCount " +
                         "ORDER BY u.readArticlesCount DESC";
            return session.createQuery(hql, User.class)
                    .setParameter("minCount", minCount)
                    .getResultList();
        } catch (Exception e) {
            throw new RuntimeException("findByReadArticlesGreaterThan failed", e);
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
            throw new RuntimeException("findByNick failed", e);
        }
    }
}
