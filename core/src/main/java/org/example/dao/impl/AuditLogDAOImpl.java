package org.example.dao.impl;

import org.example.AuditLog;
import org.example.dao.AuditLogDAO;
import org.example.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

public class AuditLogDAOImpl implements AuditLogDAO {

    @Override
    public AuditLog save(AuditLog log) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.persist(log);
            tx.commit();
            return log;
        }
    }
}
