package org.example.dao.impl;

import org.example.RefreshToken;
import org.example.dao.RefreshTokenDAO;
import org.example.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.time.LocalDateTime;
import java.util.Optional;

public class RefreshTokenDAOImpl implements RefreshTokenDAO {

    @Override
    public RefreshToken save(RefreshToken token) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.persist(token);
            tx.commit();
            return token;
        }
    }

    @Override
    public Optional<RefreshToken> findByHash(String tokenHash) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            return s.createQuery(
                    "FROM RefreshToken rt WHERE rt.tokenHash = :hash", RefreshToken.class)
                    .setParameter("hash", tokenHash)
                    .uniqueResultOptional();
        }
    }

    @Override
    public void revokeById(long id) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            RefreshToken rt = s.find(RefreshToken.class, id);
            if (rt != null) {
                rt.setRevokedAt(LocalDateTime.now());
                s.merge(rt);
            }
            tx.commit();
        }
    }
}
