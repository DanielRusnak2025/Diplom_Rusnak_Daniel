package org.example.dao;

import org.example.RejectReason;

import java.util.List;

public interface ArticleQueueDAO {

    /**
     * Atomically claims up to {@code limit} PENDING rows using FOR UPDATE SKIP LOCKED.
     * All claimed rows share one UUID lock_token; attempts is incremented at claim time.
     * Time is set server-side: locked_until = NOW(6) + INTERVAL lockMinutes MINUTE.
     * Returns lightweight records — not entities — to avoid lazy-load traps.
     * Ordering: created_at ASC, id ASC.
     */
    List<ClaimedItem> claimPending(int limit, int lockMinutes);

    /**
     * Returns stale PROCESSING rows (locked_until &lt; NOW(6)) back to PENDING or FAILED.
     * If attempts &lt; maxAttempts → status=PENDING; otherwise → status=FAILED.
     * lock_token and locked_until are cleared in both cases.
     *
     * @return count of rows affected
     */
    int resetStaleLocks(int maxAttempts);

    /**
     * Marks the row DONE, sets article_id and processed_at=NOW(6), clears lock.
     * WHERE id=? AND lock_token=? AND status='PROCESSING'.
     * 0 rows updated (lock lost or row already terminal) → WARN log, no-op.
     */
    void markDone(int queueId, int articleId, String lockToken);

    /**
     * Marks the row REJECTED, sets reject_reason and processed_at=NOW(6), clears lock.
     * Same WHERE guard as markDone.
     */
    void markRejected(int queueId, RejectReason reason, String lockToken);

    /**
     * On attempts &lt; maxAttempts → status=PENDING (retry), last_error saved, lock cleared.
     * On attempts &ge; maxAttempts → status=FAILED permanently, lock cleared.
     * Same WHERE guard as markDone.
     */
    void markFailed(int queueId, String lastError, String lockToken, int maxAttempts);
}
