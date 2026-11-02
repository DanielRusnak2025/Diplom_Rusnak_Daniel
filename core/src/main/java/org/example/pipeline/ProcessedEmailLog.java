package org.example.pipeline;

import org.example.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.Date;
import java.util.logging.Logger;

/**
 * Tracks processed emails in the processed_email table.
 * Used by EmailScheduler for idempotent email processing.
 *
 * Transactional operations (insertPlaceholder / updateSavedCount) use the caller's session
 * so they participate in the caller's single transaction.
 * Read-only operations (isAlreadyProcessed) open their own session.
 */
public class ProcessedEmailLog {

    private static final Logger log = Logger.getLogger(ProcessedEmailLog.class.getName());

    /** Returns true if an email with this message_id_hash was already processed. */
    public boolean isAlreadyProcessed(String messageIdHash) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Long count = s.createNativeQuery(
                    "SELECT COUNT(*) FROM processed_email WHERE message_id_hash = :h", Long.class)
                    .setParameter("h", messageIdHash)
                    .getSingleResult();
            return count > 0;
        }
    }

    /**
     * Inserts a placeholder row with links_saved = 0.
     * Call updateSavedCount afterwards (within the same transaction).
     *
     * @param session caller-managed session with an open transaction
     * @return the new row id
     */
    public Integer insertPlaceholder(String messageIdHash, String senderDomain, Date receivedAt,
                                     int linksTotal, int linksRejected, Session session) {
        session.createNativeMutationQuery(
                "INSERT INTO processed_email " +
                "(message_id_hash, sender_domain, received_at, processed_at, " +
                "links_total, links_rejected, links_saved) " +
                "VALUES (:hash, :domain, :recv, NOW(6), :total, :rejected, 0)")
                .setParameter("hash", messageIdHash)
                .setParameter("domain", senderDomain)
                .setParameter("recv", receivedAt)
                .setParameter("total", linksTotal)
                .setParameter("rejected", linksRejected)
                .executeUpdate();
        return session.createNativeQuery("SELECT LAST_INSERT_ID()", Integer.class)
                .getSingleResult();
    }

    /**
     * Updates links_saved after all article_queue rows have been inserted.
     *
     * @param session caller-managed session with an open transaction
     */
    public void updateSavedCount(Integer id, int saved, Session session) {
        session.createNativeMutationQuery(
                "UPDATE processed_email SET links_saved = :saved WHERE id = :id")
                .setParameter("saved", saved)
                .setParameter("id", id)
                .executeUpdate();
    }

    /**
     * Standalone record (own transaction). Used by existing tests and kept for compatibility.
     * Returns the new row id, or null on failure (e.g. duplicate hash).
     */
    public Integer record(String messageIdHash, String senderDomain, Date receivedAt,
                          int linksTotal, int linksRejected, int linksEnqueued) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            try {
                s.createNativeMutationQuery(
                        "INSERT INTO processed_email " +
                        "(message_id_hash, sender_domain, received_at, processed_at, " +
                        "links_total, links_rejected, links_saved) " +
                        "VALUES (:hash, :domain, :recv, NOW(6), :total, :rejected, :saved)")
                        .setParameter("hash", messageIdHash)
                        .setParameter("domain", senderDomain)
                        .setParameter("recv", receivedAt)
                        .setParameter("total", linksTotal)
                        .setParameter("rejected", linksRejected)
                        .setParameter("saved", linksEnqueued)
                        .executeUpdate();
                Integer id = s.createNativeQuery("SELECT LAST_INSERT_ID()", Integer.class)
                        .getSingleResult();
                tx.commit();
                return id;
            } catch (Exception e) {
                tx.rollback();
                log.warning("Failed to record processed_email hash=" + messageIdHash
                        + ": " + e.getMessage());
                return null;
            }
        }
    }
}
