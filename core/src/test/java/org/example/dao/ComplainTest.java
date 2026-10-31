package org.example.dao;

import org.example.Article;
import org.example.ArticleTagPercent;
import org.example.BlockType;
import org.example.ReportReason;
import org.example.Tag;
import org.example.TagKind;
import org.example.User;
import org.example.UserBlock;
import org.example.service.ReportService;
import org.example.test.TestDb;
import org.example.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(TestDb.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ComplainTest {

    private static final String PREFIX = "https://complain-test.invalid/";
    private static final String U_PREFIX = "complain-user-";
    private static final String T_PREFIX = "complain-tag-";

    private Article articleWithRegion;
    private Article articleNoRegion;
    private Article articleWithPublisher;
    private Article articleWithCategoryTag;
    private Article articleNoCategoryTag;
    private User testUser;
    private Tag categoryTag;
    private String testRegionKey;

    @BeforeEach
    void setup() {
        // Find any region in the taxonomy
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            testRegionKey = s.createQuery("SELECT r.regionKey FROM Region r", String.class)
                    .setMaxResults(1).uniqueResult();
        }
        assertNotNull(testRegionKey, "Region table must have at least one entry from taxonomy migrations");

        testUser = new User();
        testUser.setNick(U_PREFIX + System.nanoTime());
        testUser.setPassword("x");

        categoryTag = new Tag();
        categoryTag.setName("TestCategoryTag-" + System.nanoTime());
        categoryTag.setTagKey(T_PREFIX + System.nanoTime());
        categoryTag.setKind(TagKind.CATEGORY);

        articleWithRegion = new Article();
        articleWithRegion.setTitle("WithRegion");
        articleWithRegion.setLink(PREFIX + "region-" + System.nanoTime());
        articleWithRegion.setArticleRegion(testRegionKey);

        articleNoRegion = new Article();
        articleNoRegion.setTitle("NoRegion");
        articleNoRegion.setLink(PREFIX + "no-region-" + System.nanoTime());

        articleWithPublisher = new Article();
        articleWithPublisher.setTitle("WithPublisher");
        articleWithPublisher.setLink(PREFIX + "pub-" + System.nanoTime());
        articleWithPublisher.setPublisher("Test Publisher Co");

        articleWithCategoryTag = new Article();
        articleWithCategoryTag.setTitle("WithCategoryTag");
        articleWithCategoryTag.setLink(PREFIX + "cat-" + System.nanoTime());

        articleNoCategoryTag = new Article();
        articleNoCategoryTag.setTitle("NoCategoryTag");
        articleNoCategoryTag.setLink(PREFIX + "no-cat-" + System.nanoTime());

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.persist(testUser);
            s.persist(categoryTag);
            s.persist(articleWithRegion);
            s.persist(articleNoRegion);
            s.persist(articleWithPublisher);
            s.persist(articleWithCategoryTag);
            s.persist(articleNoCategoryTag);

            // Attach category tag to articleWithCategoryTag (80%)
            ArticleTagPercent atp = new ArticleTagPercent(articleWithCategoryTag, categoryTag, 80);
            s.persist(atp);

            // Attach non-category tag to articleNoCategoryTag
            Tag entityTag = new Tag();
            entityTag.setName("EntityTag-" + System.nanoTime());
            entityTag.setTagKey(T_PREFIX + "entity-" + System.nanoTime());
            entityTag.setKind(TagKind.ENTITY);
            s.persist(entityTag);
            ArticleTagPercent atp2 = new ArticleTagPercent(articleNoCategoryTag, entityTag, 60);
            s.persist(atp2);

            tx.commit();
        }
    }

    @AfterEach
    void cleanup() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.createNativeMutationQuery(
                    "DELETE ub FROM user_block ub JOIN user u ON u.id = ub.user_id WHERE u.nick LIKE :pfx")
                    .setParameter("pfx", U_PREFIX + "%").executeUpdate();
            s.createNativeMutationQuery(
                    "DELETE utp FROM user_tag_percent utp JOIN user u ON u.id = utp.user_id WHERE u.nick LIKE :pfx")
                    .setParameter("pfx", U_PREFIX + "%").executeUpdate();
            s.createNativeMutationQuery(
                    "DELETE r FROM report r JOIN article a ON a.id = r.article_id WHERE a.link LIKE :pfx")
                    .setParameter("pfx", PREFIX + "%").executeUpdate();
            s.createNativeMutationQuery(
                    "DELETE atp FROM article_tag_percent atp JOIN article a ON a.id = atp.article_id WHERE a.link LIKE :pfx")
                    .setParameter("pfx", PREFIX + "%").executeUpdate();
            s.createNativeMutationQuery("DELETE FROM article WHERE link LIKE :pfx")
                    .setParameter("pfx", PREFIX + "%").executeUpdate();
            s.createNativeMutationQuery("DELETE FROM user WHERE nick LIKE :pfx")
                    .setParameter("pfx", U_PREFIX + "%").executeUpdate();
            s.createNativeMutationQuery("DELETE FROM tag WHERE tag_key LIKE :pfx")
                    .setParameter("pfx", T_PREFIX + "%").executeUpdate();
            tx.commit();
        }
    }

    @Test
    void complainUnwantedRegion_createsReportAndBlock() {
        new ReportService().complain(articleWithRegion.getId(), testUser.getId(), ReportReason.UNWANTED_REGION);

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Long reportCount = s.createQuery(
                    "SELECT COUNT(r) FROM Report r WHERE r.user.id = :uid AND r.article.id = :aid", Long.class)
                    .setParameter("uid", testUser.getId()).setParameter("aid", articleWithRegion.getId())
                    .uniqueResult();
            assertEquals(1L, reportCount, "report must be created");

            Long blockCount = s.createQuery(
                    "SELECT COUNT(ub) FROM UserBlock ub WHERE ub.user.id = :uid AND ub.blockType = :bt",
                    Long.class)
                    .setParameter("uid", testUser.getId()).setParameter("bt", BlockType.REGION)
                    .uniqueResult();
            assertEquals(1L, blockCount, "region block must be created");
        }
    }

    @Test
    void complainNullRegion_noBlockCreated() {
        new ReportService().complain(articleNoRegion.getId(), testUser.getId(), ReportReason.UNWANTED_REGION);

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Long blockCount = s.createQuery(
                    "SELECT COUNT(ub) FROM UserBlock ub WHERE ub.user.id = :uid", Long.class)
                    .setParameter("uid", testUser.getId()).uniqueResult();
            assertEquals(0L, blockCount, "no block when article has no region");
        }
    }

    @Test
    void complainUnwantedPublisher_createsBlock() {
        new ReportService().complain(articleWithPublisher.getId(), testUser.getId(), ReportReason.UNWANTED_PUBLISHER);

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Long blockCount = s.createQuery(
                    "SELECT COUNT(ub) FROM UserBlock ub WHERE ub.user.id = :uid AND ub.blockType = :bt",
                    Long.class)
                    .setParameter("uid", testUser.getId()).setParameter("bt", BlockType.PUBLISHER)
                    .uniqueResult();
            assertEquals(1L, blockCount, "publisher block must be created");
        }
    }

    @Test
    void complainNotInterestedTopic_createsCategoryTagBlock() {
        new ReportService().complain(articleWithCategoryTag.getId(), testUser.getId(), ReportReason.NOT_INTERESTED_TOPIC);

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Long blockCount = s.createQuery(
                    "SELECT COUNT(ub) FROM UserBlock ub WHERE ub.user.id = :uid AND ub.blockType = :bt AND ub.tag.id = :tid",
                    Long.class)
                    .setParameter("uid", testUser.getId()).setParameter("bt", BlockType.TAG)
                    .setParameter("tid", categoryTag.getId()).uniqueResult();
            assertEquals(1L, blockCount, "TAG block must be created for highest CATEGORY tag");
        }
    }

    @Test
    void complainNotInterestedTopic_noCategoryTag_noBlock() {
        new ReportService().complain(articleNoCategoryTag.getId(), testUser.getId(), ReportReason.NOT_INTERESTED_TOPIC);

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Long blockCount = s.createQuery(
                    "SELECT COUNT(ub) FROM UserBlock ub WHERE ub.user.id = :uid", Long.class)
                    .setParameter("uid", testUser.getId()).uniqueResult();
            assertEquals(0L, blockCount, "no block when no CATEGORY tag");
        }
    }

    @Test
    void complainOtherReason_noBlock() {
        new ReportService().complain(articleWithPublisher.getId(), testUser.getId(), ReportReason.BROKEN_LINK);

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Long blockCount = s.createQuery(
                    "SELECT COUNT(ub) FROM UserBlock ub WHERE ub.user.id = :uid", Long.class)
                    .setParameter("uid", testUser.getId()).uniqueResult();
            assertEquals(0L, blockCount, "no block for reasons other than UNWANTED_REGION/PUBLISHER/NOT_INTERESTED_TOPIC");
        }
    }

    @Test
    void complainTwiceSameBlock_secondComplainSilent() {
        ReportService svc = new ReportService();
        assertDoesNotThrow(() -> svc.complain(articleWithPublisher.getId(), testUser.getId(), ReportReason.UNWANTED_PUBLISHER));
        assertDoesNotThrow(() -> svc.complain(articleWithPublisher.getId(), testUser.getId(), ReportReason.UNWANTED_PUBLISHER));

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Long blockCount = s.createQuery(
                    "SELECT COUNT(ub) FROM UserBlock ub WHERE ub.user.id = :uid AND ub.blockType = :bt",
                    Long.class)
                    .setParameter("uid", testUser.getId()).setParameter("bt", BlockType.PUBLISHER)
                    .uniqueResult();
            assertEquals(1L, blockCount, "only one publisher block after two identical complaints");
        }
    }

    @Test
    void complainAdjustsTagWeights() {
        new ReportService().complain(articleWithCategoryTag.getId(), testUser.getId(), ReportReason.NOT_INTERESTED_TOPIC);

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Long utpCount = s.createQuery(
                    "SELECT COUNT(utp) FROM UserTagPercent utp WHERE utp.user.id = :uid", Long.class)
                    .setParameter("uid", testUser.getId()).uniqueResult();
            assertTrue(utpCount > 0, "complain must adjust user_tag_percent (direction=-1)");
        }
    }

    @Test
    void complainAfterDislike_weightsUnchanged() {
        ReportService svc = new ReportService();
        // Establish a dislike (rating=-1, 1 negative step applied)
        svc.upsertRating(articleWithCategoryTag.getId(), testUser.getId(), -1);

        int afterDislike = getCategoryTagPercent();

        // Complain: existing report already has rating=-1, so steps=0, weights must not change
        svc.complain(articleWithCategoryTag.getId(), testUser.getId(), ReportReason.NOT_INTERESTED_TOPIC);

        int afterComplain = getCategoryTagPercent();
        assertEquals(afterDislike, afterComplain,
                "complain after existing dislike must not adjust weights (prior rating already -1)");
    }

    private int getCategoryTagPercent() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Integer p = s.createQuery(
                    "SELECT utp.percent FROM UserTagPercent utp " +
                    "WHERE utp.user.id = :uid AND utp.id.tagId = :tid", Integer.class)
                    .setParameter("uid", testUser.getId()).setParameter("tid", categoryTag.getId())
                    .uniqueResult();
            return p != null ? p : 0;
        }
    }
}
