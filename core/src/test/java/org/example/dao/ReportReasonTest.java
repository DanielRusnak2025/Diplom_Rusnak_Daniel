package org.example.dao;

import org.example.Article;
import org.example.Report;
import org.example.ReportReason;
import org.example.User;
import org.example.service.ReportService;
import org.example.test.TestDb;
import org.example.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(TestDb.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportReasonTest {

    private static final String PREFIX = "https://reason-test.invalid/";

    private Article testArticle;
    private User testUser;

    @BeforeEach
    void setup() {
        testUser = new User();
        testUser.setNick("reason-test-" + System.nanoTime());
        testUser.setPassword("x");

        testArticle = new Article();
        testArticle.setTitle("Reason test article " + System.nanoTime());
        testArticle.setLink(PREFIX + System.nanoTime());

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.persist(testUser);
            s.persist(testArticle);
            tx.commit();
        }
    }

    @AfterEach
    void cleanup() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.createNativeMutationQuery(
                    "DELETE r FROM report r " +
                    "JOIN article a ON a.id = r.article_id " +
                    "WHERE a.link LIKE :pfx")
                    .setParameter("pfx", PREFIX + "%")
                    .executeUpdate();
            s.createNativeMutationQuery("DELETE FROM article WHERE link LIKE :pfx")
                    .setParameter("pfx", PREFIX + "%")
                    .executeUpdate();
            s.createNativeMutationQuery("DELETE FROM user WHERE nick LIKE 'reason-test-%'")
                    .executeUpdate();
            tx.commit();
        }
    }

    @Test
    void saveComplaintWithValidReasonCode() {
        Report r = new Report(testArticle, testUser, -1, ReportReason.OTHER);
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.persist(r);
            tx.commit();
        }

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Report found = s.find(Report.class, r.getId());
            assertNotNull(found, "Complaint must be persisted");
            assertEquals(ReportReason.OTHER, found.getReasonCode());
            assertEquals(-1, found.getRating());
        }
    }

    @Test
    void invalidReasonCodeViaSQLViolatesCheck() {
        assertThrows(Exception.class, () -> {
            try (Session s = HibernateUtil.getSessionFactory().openSession()) {
                Transaction tx = s.beginTransaction();
                s.createNativeMutationQuery(
                        "INSERT INTO report (date, rating, reason_code, article_id, user_id) " +
                        "VALUES (CURDATE(), -1, 'INVALID_CODE', :articleId, :userId)")
                        .setParameter("articleId", testArticle.getId())
                        .setParameter("userId", testUser.getId())
                        .executeUpdate();
                tx.commit();
            }
        }, "CHECK chk_report_reason_code must reject unknown reason_code");
    }

    @Test
    void upsertRatingFromComplaintToLikeClearsReasonCode() {
        // Create a complaint: rating=-1, reasonCode=OTHER
        Report complaint = new Report(testArticle, testUser, -1, ReportReason.OTHER);
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.persist(complaint);
            tx.commit();
        }

        // upsertRating with rating=1 — must NOT throw, must clear reasonCode
        new ReportService().upsertRating(testArticle.getId(), testUser.getId(), 1);

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Report found = s.find(Report.class, complaint.getId());
            assertNotNull(found);
            assertEquals(1, found.getRating(), "rating must be updated to 1");
            assertNull(found.getReasonCode(), "reasonCode must be cleared when rating != -1");
        }
    }

    @Test
    void complaintWithRatingPlusOneViolatesCheck() {
        assertThrows(Exception.class, () -> {
            try (Session s = HibernateUtil.getSessionFactory().openSession()) {
                Transaction tx = s.beginTransaction();
                s.createNativeMutationQuery(
                        "INSERT INTO report (date, rating, reason_code, article_id, user_id) " +
                        "VALUES (CURDATE(), 1, 'OTHER', :articleId, :userId)")
                        .setParameter("articleId", testArticle.getId())
                        .setParameter("userId", testUser.getId())
                        .executeUpdate();
                tx.commit();
            }
        }, "CHECK chk_report_complaint must reject reason_code with rating != -1");
    }
}
