package org.example.dao.impl;

import org.example.RejectReason;
import org.example.dao.ArticleQueueDAO;
import org.example.dao.ClaimedItem;
import org.example.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class ArticleQueueDAOImpl implements ArticleQueueDAO {

    private static final Logger log = Logger.getLogger(ArticleQueueDAOImpl.class.getName());

    @Override
    public List<ClaimedItem> claimPending(int limit, int lockMinutes) {
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();

            @SuppressWarnings("unchecked")
            List<Object[]> rows = session.createNativeQuery(
                    "SELECT id, url, article_id FROM article_queue " +
                    "WHERE status = 'PENDING' " +
                    "ORDER BY created_at, id " +
                    "LIMIT :lim " +
                    "FOR UPDATE SKIP LOCKED",
                    Object[].class)
                    .setParameter("lim", limit)
                    .getResultList();

            if (rows.isEmpty()) {
                tx.commit();
                return List.of();
            }

            String token = UUID.randomUUID().toString();

            for (Object[] row : rows) {
                int rowId = ((Number) row[0]).intValue();
                session.createNativeMutationQuery(
                        "UPDATE article_queue " +
                        "SET status = 'PROCESSING', " +
                        "    lock_token = :token, " +
                        "    locked_until = NOW(6) + INTERVAL :m MINUTE, " +
                        "    attempts = attempts + 1 " +
                        "WHERE id = :id AND status = 'PENDING'")
                        .setParameter("token", token)
                        .setParameter("m", lockMinutes)
                        .setParameter("id", rowId)
                        .executeUpdate();
            }

            tx.commit();

            List<ClaimedItem> result = new ArrayList<>(rows.size());
            for (Object[] row : rows) {
                result.add(new ClaimedItem(
                        ((Number) row[0]).intValue(),
                        (String)  row[1],
                        row[2] != null ? ((Number) row[2]).intValue() : null,
                        token));
            }
            return result;

        } catch (Exception e) {
            if (tx != null) tx.rollback();
            throw new RuntimeException("claimPending failed", e);
        }
    }

    @Override
    public int resetStaleLocks(int maxAttempts) {
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            int updated = session.createNativeMutationQuery(
                    "UPDATE article_queue " +
                    "SET status      = CASE WHEN attempts < :max THEN 'PENDING' ELSE 'FAILED' END, " +
                    "    lock_token  = NULL, " +
                    "    locked_until = NULL " +
                    "WHERE status = 'PROCESSING' AND locked_until < NOW(6)")
                    .setParameter("max", maxAttempts)
                    .executeUpdate();
            tx.commit();
            return updated;
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            throw new RuntimeException("resetStaleLocks failed", e);
        }
    }

    @Override
    public void markDone(int queueId, int articleId, String lockToken) {
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            int updated = session.createNativeMutationQuery(
                    "UPDATE article_queue " +
                    "SET status       = 'DONE', " +
                    "    article_id   = :articleId, " +
                    "    processed_at = NOW(6), " +
                    "    lock_token   = NULL, " +
                    "    locked_until = NULL " +
                    "WHERE id = :id AND lock_token = :token AND status = 'PROCESSING'")
                    .setParameter("articleId", articleId)
                    .setParameter("id", queueId)
                    .setParameter("token", lockToken)
                    .executeUpdate();
            tx.commit();
            if (updated == 0) {
                log.warning("markDone: lock lost or already terminal for queue_id=" + queueId);
            }
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            throw new RuntimeException("markDone failed for queue_id=" + queueId, e);
        }
    }

    @Override
    public void markRejected(int queueId, RejectReason reason, String lockToken) {
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            int updated = session.createNativeMutationQuery(
                    "UPDATE article_queue " +
                    "SET status        = 'REJECTED', " +
                    "    reject_reason = :reason, " +
                    "    processed_at  = NOW(6), " +
                    "    lock_token    = NULL, " +
                    "    locked_until  = NULL " +
                    "WHERE id = :id AND lock_token = :token AND status = 'PROCESSING'")
                    .setParameter("reason", reason.name())
                    .setParameter("id", queueId)
                    .setParameter("token", lockToken)
                    .executeUpdate();
            tx.commit();
            if (updated == 0) {
                log.warning("markRejected: lock lost or already terminal for queue_id=" + queueId);
            }
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            throw new RuntimeException("markRejected failed for queue_id=" + queueId, e);
        }
    }

    @Override
    public void markFailed(int queueId, String lastError, String lockToken, int maxAttempts) {
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            int updated = session.createNativeMutationQuery(
                    "UPDATE article_queue " +
                    "SET status       = CASE WHEN attempts < :max THEN 'PENDING' ELSE 'FAILED' END, " +
                    "    last_error   = :lastError, " +
                    "    lock_token   = NULL, " +
                    "    locked_until = NULL " +
                    "WHERE id = :id AND lock_token = :token AND status = 'PROCESSING'")
                    .setParameter("max", maxAttempts)
                    .setParameter("lastError", lastError)
                    .setParameter("id", queueId)
                    .setParameter("token", lockToken)
                    .executeUpdate();
            tx.commit();
            if (updated == 0) {
                log.warning("markFailed: lock lost or already terminal for queue_id=" + queueId);
            }
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            throw new RuntimeException("markFailed failed for queue_id=" + queueId, e);
        }
    }
}
