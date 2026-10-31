package org.example.dao;

import org.example.Tag;
import org.example.User;
import org.example.test.TestDb;
import org.example.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(TestDb.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class V10TablesTest {

    private static final String U_PREFIX = "v10-user-";
    private static final String T_PREFIX = "v10-tag-";

    private User testUser;
    private Tag testTag;

    @BeforeEach
    void setup() {
        testUser = new User();
        testUser.setNick(U_PREFIX + System.nanoTime());
        testUser.setPassword("x");

        testTag = new Tag();
        testTag.setName("V10TestTag");
        testTag.setTagKey(T_PREFIX + System.nanoTime());

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.persist(testUser);
            s.persist(testTag);
            tx.commit();
        }
    }

    @AfterEach
    void cleanup() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            // Delete user_block rows (CASCADE would handle user delete, but user may still exist)
            s.createNativeMutationQuery(
                    "DELETE ub FROM user_block ub " +
                    "JOIN user u ON u.id = ub.user_id " +
                    "WHERE u.nick LIKE :pfx")
                    .setParameter("pfx", U_PREFIX + "%")
                    .executeUpdate();
            // Delete refresh_token rows for test users
            s.createNativeMutationQuery(
                    "DELETE rt FROM refresh_token rt " +
                    "JOIN user u ON u.id = rt.user_id " +
                    "WHERE u.nick LIKE :pfx")
                    .setParameter("pfx", U_PREFIX + "%")
                    .executeUpdate();
            // Delete audit_log entries created by tests
            s.createNativeMutationQuery(
                    "DELETE FROM audit_log WHERE action LIKE 'V10_TEST_%'")
                    .executeUpdate();
            // Delete test users (also removes dangling FK rows via CASCADE)
            s.createNativeMutationQuery("DELETE FROM user WHERE nick LIKE :pfx")
                    .setParameter("pfx", U_PREFIX + "%")
                    .executeUpdate();
            // Delete test tags (no user_block references them after above cleanup)
            s.createNativeMutationQuery("DELETE FROM tag WHERE tag_key LIKE :pfx")
                    .setParameter("pfx", T_PREFIX + "%")
                    .executeUpdate();
            tx.commit();
        }
    }

    @Test
    void doubleBlockSameTagViolatesUnique() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.createNativeMutationQuery(
                    "INSERT INTO user_block (user_id, block_type, tag_id, created_at) " +
                    "VALUES (:uid, 'TAG', :tagId, NOW(6))")
                    .setParameter("uid", testUser.getId())
                    .setParameter("tagId", testTag.getId())
                    .executeUpdate();
            tx.commit();
        }

        assertThrows(Exception.class, () -> {
            try (Session s = HibernateUtil.getSessionFactory().openSession()) {
                Transaction tx = s.beginTransaction();
                s.createNativeMutationQuery(
                        "INSERT INTO user_block (user_id, block_type, tag_id, created_at) " +
                        "VALUES (:uid, 'TAG', :tagId, NOW(6))")
                        .setParameter("uid", testUser.getId())
                        .setParameter("tagId", testTag.getId())
                        .executeUpdate();
                tx.commit();
            }
        }, "UNIQUE(user_id, block_key) must reject duplicate block for same tag");
    }

    @Test
    void tagBlockWithPublisherViolatesCheck() {
        assertThrows(Exception.class, () -> {
            try (Session s = HibernateUtil.getSessionFactory().openSession()) {
                Transaction tx = s.beginTransaction();
                s.createNativeMutationQuery(
                        "INSERT INTO user_block (user_id, block_type, tag_id, publisher, created_at) " +
                        "VALUES (:uid, 'TAG', :tagId, 'some publisher', NOW(6))")
                        .setParameter("uid", testUser.getId())
                        .setParameter("tagId", testTag.getId())
                        .executeUpdate();
                tx.commit();
            }
        }, "CHECK chk_ub_target must reject TAG block with publisher set");
    }

    @Test
    void deleteTagWithBlockViolatesFKRestrict() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.createNativeMutationQuery(
                    "INSERT INTO user_block (user_id, block_type, tag_id, created_at) " +
                    "VALUES (:uid, 'TAG', :tagId, NOW(6))")
                    .setParameter("uid", testUser.getId())
                    .setParameter("tagId", testTag.getId())
                    .executeUpdate();
            tx.commit();
        }

        assertThrows(Exception.class, () -> {
            try (Session s = HibernateUtil.getSessionFactory().openSession()) {
                Transaction tx = s.beginTransaction();
                s.createNativeMutationQuery("DELETE FROM tag WHERE id = :id")
                        .setParameter("id", testTag.getId())
                        .executeUpdate();
                tx.commit();
            }
        }, "FK fk_ub_tag (RESTRICT) must prevent deleting a tag that has user_block rows");
    }

    @Test
    void deleteUserCascadesBlocksAndTokensKeepsAuditLog() {
        int userId = testUser.getId();

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.createNativeMutationQuery(
                    "INSERT INTO user_block (user_id, block_type, publisher, created_at) " +
                    "VALUES (:uid, 'PUBLISHER', 'Test Publisher', NOW(6))")
                    .setParameter("uid", userId)
                    .executeUpdate();
            s.createNativeMutationQuery(
                    "INSERT INTO refresh_token (user_id, token_hash, family_id, created_at, expires_at) " +
                    "VALUES (:uid, LPAD(HEX(:uid), 64, '0'), UUID(), NOW(6), DATE_ADD(NOW(6), INTERVAL 7 DAY))")
                    .setParameter("uid", userId)
                    .executeUpdate();
            s.createNativeMutationQuery(
                    "INSERT INTO audit_log (actor_user_id, action, created_at) " +
                    "VALUES (:uid, 'V10_TEST_LOGIN', NOW(6))")
                    .setParameter("uid", userId)
                    .executeUpdate();
            tx.commit();
        }

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.createNativeMutationQuery("DELETE FROM user WHERE id = :uid")
                    .setParameter("uid", userId)
                    .executeUpdate();
            tx.commit();
        }

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Long ubCount = s.createNativeQuery(
                    "SELECT COUNT(*) FROM user_block WHERE user_id = :uid", Long.class)
                    .setParameter("uid", userId)
                    .getSingleResult();
            assertEquals(0L, ubCount, "user_block rows must be deleted via CASCADE");

            Long rtCount = s.createNativeQuery(
                    "SELECT COUNT(*) FROM refresh_token WHERE user_id = :uid", Long.class)
                    .setParameter("uid", userId)
                    .getSingleResult();
            assertEquals(0L, rtCount, "refresh_token rows must be deleted via CASCADE");

            Long alCount = s.createNativeQuery(
                    "SELECT COUNT(*) FROM audit_log WHERE action = 'V10_TEST_LOGIN' AND actor_user_id IS NULL",
                    Long.class)
                    .getSingleResult();
            assertEquals(1L, alCount, "audit_log must remain with actor_user_id=NULL");
        }
    }

    @Test
    void duplicateTokenHashViolatesUnique() {
        String hash = "a".repeat(64);

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.createNativeMutationQuery(
                    "INSERT INTO refresh_token (user_id, token_hash, family_id, created_at, expires_at) " +
                    "VALUES (:uid, :hash, UUID(), NOW(6), DATE_ADD(NOW(6), INTERVAL 7 DAY))")
                    .setParameter("uid", testUser.getId())
                    .setParameter("hash", hash)
                    .executeUpdate();
            tx.commit();
        }

        assertThrows(Exception.class, () -> {
            try (Session s = HibernateUtil.getSessionFactory().openSession()) {
                Transaction tx = s.beginTransaction();
                s.createNativeMutationQuery(
                        "INSERT INTO refresh_token (user_id, token_hash, family_id, created_at, expires_at) " +
                        "VALUES (:uid, :hash, UUID(), NOW(6), DATE_ADD(NOW(6), INTERVAL 7 DAY))")
                        .setParameter("uid", testUser.getId())
                        .setParameter("hash", hash)
                        .executeUpdate();
                tx.commit();
            }
        }, "UNIQUE(token_hash) must reject duplicate token hash");
    }
}
