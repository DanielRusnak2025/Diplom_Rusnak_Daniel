package org.example.pipeline;

import org.example.util.UrlNormalizer;
import org.hibernate.Session;

import java.sql.PreparedStatement;
import java.sql.Savepoint;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Inserts filtered URLs into article_queue with status PENDING.
 * Callers own the session and transaction; this class only issues SQL statements.
 *
 * Duplicate url_hash is silently skipped via SAVEPOINT-protected INSERT.
 * URLs longer than 2048 chars or malformed for UrlNormalizer are skipped with a WARN.
 * Any non-duplicate DB error is re-thrown (callers must rollback).
 */
public class ArticleQueueEnqueuer {

    private static final Logger log = Logger.getLogger(ArticleQueueEnqueuer.class.getName());
    private static final int MAX_URL_LENGTH = 2048;

    private record UrlEntry(String url, String hash) {}

    /**
     * @param urls    filtered list of URLs to insert
     * @param emailId FK to processed_email row, or null
     * @param session caller-managed Hibernate session with an open transaction
     * @return count of newly inserted rows (duplicates and skipped URLs not counted)
     */
    public int enqueue(List<String> urls, Integer emailId, Session session) {
        List<UrlEntry> valid = buildValid(urls);

        return session.doReturningWork(conn -> {
            int inserted = 0;
            for (UrlEntry entry : valid) {
                Savepoint sp;
                try {
                    sp = conn.setSavepoint();
                } catch (java.sql.SQLException e) {
                    throw new RuntimeException("setSavepoint failed for URL: " + entry.url(), e);
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO article_queue (url, url_hash, email_id, status, created_at) " +
                        "VALUES (?, ?, ?, 'PENDING', NOW(6))")) {
                    ps.setString(1, entry.url());
                    ps.setString(2, entry.hash());
                    if (emailId != null) ps.setInt(3, emailId);
                    else ps.setNull(3, Types.INTEGER);
                    ps.executeUpdate();
                    try { conn.releaseSavepoint(sp); } catch (java.sql.SQLException ignored) {}
                    inserted++;
                } catch (java.sql.SQLException e) {
                    try {
                        conn.rollback(sp);
                    } catch (java.sql.SQLException rbe) {
                        throw new RuntimeException("rollback(savepoint) failed", rbe);
                    }
                    if ("23000".equals(e.getSQLState()) && e.getErrorCode() == 1062) {
                        // Duplicate url_hash — already in queue, silently skip
                    } else {
                        throw new RuntimeException("URL insert failed: " + entry.url(), e);
                    }
                }
            }
            return inserted;
        });
    }

    private List<UrlEntry> buildValid(List<String> urls) {
        List<UrlEntry> result = new ArrayList<>(urls.size());
        for (String url : urls) {
            if (url.length() > MAX_URL_LENGTH) {
                log.warning("URL exceeds " + MAX_URL_LENGTH + " chars, skipping: "
                        + url.substring(0, Math.min(url.length(), 80)) + "…");
                continue;
            }
            try {
                String norm = UrlNormalizer.normalize(url);
                result.add(new UrlEntry(url, UrlNormalizer.hash(norm)));
            } catch (IllegalArgumentException e) {
                log.warning("Malformed URL, skipping: " + url + " — " + e.getMessage());
            }
        }
        return result;
    }
}
