package org.example.dao.impl;

import org.example.PasswordHistory;
import org.example.dao.PasswordHistoryDAO;
import org.example.util.HibernateUtil;
import org.hibernate.Session;

import java.util.List;

public class PasswordHistoryDAOImpl extends GenericDAOImpl<PasswordHistory, Integer>
        implements PasswordHistoryDAO {

    public PasswordHistoryDAOImpl() {
        super(PasswordHistory.class);
    }

    @Override
    public List<String> findLastHashes(int userId, int n) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                            "SELECT ph.passwordHash FROM PasswordHistory ph " +
                            "WHERE ph.user.id = :uid ORDER BY ph.createdAt DESC",
                            String.class)
                    .setParameter("uid", userId)
                    .setMaxResults(n)
                    .getResultList();
        } catch (Exception e) {
            throw new RuntimeException("findLastHashes failed for userId=" + userId, e);
        }
    }
}
