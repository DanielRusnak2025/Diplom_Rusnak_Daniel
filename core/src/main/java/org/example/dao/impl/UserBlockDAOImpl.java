package org.example.dao.impl;

import org.example.UserBlock;
import org.example.dao.UserBlockDAO;
import org.example.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.List;

public class UserBlockDAOImpl implements UserBlockDAO {

    @Override
    public UserBlock save(UserBlock block) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.persist(block);
            tx.commit();
            return block;
        }
    }

    @Override
    public List<UserBlock> findByUserId(int userId) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            return s.createQuery(
                    "FROM UserBlock ub WHERE ub.user.id = :uid", UserBlock.class)
                    .setParameter("uid", userId)
                    .list();
        }
    }

    @Override
    public void deleteById(int id) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            UserBlock ub = s.find(UserBlock.class, id);
            if (ub != null) s.remove(ub);
            tx.commit();
        }
    }
}
