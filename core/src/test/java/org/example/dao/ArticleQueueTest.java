package org.example.dao;

import org.example.QueueStatus;
import org.example.dao.impl.ArticleQueueDAOImpl;
import org.example.test.TestDb;
import org.example.util.HibernateUtil;
import org.example.util.UrlNormalizer;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ArticleQueueDAO.
 * Runs exclusively against db_use_test_it (set via -Dnewsdove.db.name in build.gradle.kts).
 * TestDb extension performs clean + baseline schema + Flyway migrate once per JVM.
 */
@ExtendWith(TestDb.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArticleQueueTest {

    private static final String TEST_PREFIX = "https://test.invalid/";

    private final ArticleQueueDAO dao = new ArticleQueueDAOImpl();

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Inserts a PENDING test row, returns its generated id. */
    private int insertPending(Session s, String suffix) {
        String url  = UrlNormalizer.normalize(TEST_PREFIX + suffix);
        String hash = UrlNormalizer.hash(url);
        s.createNativeMutationQuery(
                "INSERT INTO article_queue (url, url_hash, status, created_at) " +
                "VALUES (:url, :hash, 'PENDING', NOW(6))")
                .setParameter("url", url)
                .setParameter("hash", hash)
                .executeUpdate();
        return ((Number) s.createNativeQuery(
                "SELECT LAST_INSERT_ID()", Integer.class)
                .getSingleResult()).intValue();
    }

    /** Inserts a PROCESSING row with locked_until in the past. */
    private int insertStale(Session s, String suffix, int attempts) {
        String url  = UrlNormalizer.normalize(TEST_PREFIX + suffix);
        String hash = UrlNormalizer.hash(url);
        s.createNativeMutationQuery(
                "INSERT INTO article_queue " +
                "(url, url_hash, status, lock_token, locked_until, attempts, created_at) " +
                "VALUES (:url, :hash, 'PROCESSING', :token, NOW(6)-INTERVAL 1 HOUR, :att, NOW(6))")
                .setParameter("url", url)
                .setParameter("hash", hash)
                .setParameter("token", "00000000-0000-0000-0000-stale0000001")
                .setParameter("att", attempts)
                .executeUpdate();
        return ((Number) s.createNativeQuery(
                "SELECT LAST_INSERT_ID()", Integer.class)
                .getSingleResult()).intValue();
    }

    private QueueStatus getStatus(int id) {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            String raw = (String) s.createNativeQuery(
                    "SELECT status FROM article_queue WHERE id=:id", String.class)
                    .setParameter("id", id)
                    .getSingleResult();
            return QueueStatus.valueOf(raw);
        }
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    void concurrentClaimNoDuplicates() throws InterruptedException {
        List<Integer> inserted = new ArrayList<>();
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            for (int i = 1; i <= 6; i++) {
                inserted.add(insertPending(s, "concurrent/" + i));
            }
            tx.commit();
        }

        List<ClaimedItem> claim1 = new ArrayList<>();
        List<ClaimedItem> claim2 = new ArrayList<>();

        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        Thread t1 = new Thread(() -> {
            try { gate.await(); claim1.addAll(dao.claimPending(10, 5)); }
            catch (Exception e) { throw new RuntimeException(e); }
            finally { done.countDown(); }
        });
        Thread t2 = new Thread(() -> {
            try { gate.await(); claim2.addAll(dao.claimPending(10, 5)); }
            catch (Exception e) { throw new RuntimeException(e); }
            finally { done.countDown(); }
        });

        t1.start(); t2.start();
        gate.countDown();
        done.await();

        Set<Integer> ids1 = claim1.stream().map(ClaimedItem::id).collect(Collectors.toSet());
        Set<Integer> ids2 = claim2.stream().map(ClaimedItem::id).collect(Collectors.toSet());
        Set<Integer> insertedSet = Set.copyOf(inserted);

        assertTrue(insertedSet.containsAll(ids1), "claim1 must only contain test rows");
        assertTrue(insertedSet.containsAll(ids2), "claim2 must only contain test rows");

        Set<Integer> overlap = ids1.stream().filter(ids2::contains).collect(Collectors.toSet());
        assertEquals(0, overlap.size(), "No overlapping ids between concurrent claims");

        assertEquals(6, ids1.size() + ids2.size(), "All 6 rows claimed exactly once");
    }

    @Test
    void wrongTokenIsNoOp() {
        int id;
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            id = insertPending(s, "wrongtoken/1");
            tx.commit();
        }

        List<ClaimedItem> claimed = dao.claimPending(1, 5);
        ClaimedItem item = claimed.stream().filter(c -> c.id() == id).findFirst().orElse(null);
        if (item == null) {
            List<ClaimedItem> retry = dao.claimPending(10, 5);
            item = retry.stream().filter(c -> c.id() == id).findFirst()
                    .orElseThrow(() -> new AssertionError("Test row not found in claim"));
        }

        dao.markDone(item.id(), 1, "wrong-token-xyz");  // wrong token → no-op

        assertEquals(QueueStatus.PROCESSING, getStatus(id),
                "Row must still be PROCESSING after markDone with wrong token");
        // Row stays PROCESSING; wiped by Flyway clean on next test run.
    }

    @Test
    void staleLockResetsToReady() {
        int id;
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            id = insertStale(s, "stale/1", 1);
            tx.commit();
        }

        int affected = dao.resetStaleLocks(3);
        assertTrue(affected >= 1, "At least one stale row should have been reset");

        assertEquals(QueueStatus.PENDING, getStatus(id),
                "Stale row with attempts=1 < 3 must be returned to PENDING");

        // Delete immediately so the now-PENDING row does not get claimed by other tests.
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.createNativeMutationQuery("DELETE FROM article_queue WHERE id=:id")
                    .setParameter("id", id)
                    .executeUpdate();
            tx.commit();
        }
    }

    @Test
    void maxAttemptsBecomeFailed() {
        int id;
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            id = insertPending(s, "maxattempts/1");
            tx.commit();
        }

        ClaimedItem c1 = claimSpecific(id);
        dao.markFailed(c1.id(), "err1", c1.lockToken(), 3);
        assertEquals(QueueStatus.PENDING, getStatus(id));

        ClaimedItem c2 = claimSpecific(id);
        dao.markFailed(c2.id(), "err2", c2.lockToken(), 3);
        assertEquals(QueueStatus.PENDING, getStatus(id));

        ClaimedItem c3 = claimSpecific(id);
        dao.markFailed(c3.id(), "err3", c3.lockToken(), 3);
        assertEquals(QueueStatus.FAILED, getStatus(id),
                "Row with attempts=3 >= maxAttempts=3 must become FAILED");
    }

    /** Claims until the specific id is found (releases others; retries up to 5 times). */
    private ClaimedItem claimSpecific(int targetId) {
        for (int i = 0; i < 5; i++) {
            List<ClaimedItem> batch = dao.claimPending(10, 5);
            for (ClaimedItem c : batch) {
                if (c.id() == targetId) return c;
                dao.markFailed(c.id(), "released", c.lockToken(), Integer.MAX_VALUE);
            }
        }
        throw new AssertionError("Could not claim target row id=" + targetId);
    }
}
