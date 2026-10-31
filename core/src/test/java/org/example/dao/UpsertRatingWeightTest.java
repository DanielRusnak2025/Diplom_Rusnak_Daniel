package org.example.dao;

import org.example.Article;
import org.example.ArticleTagPercent;
import org.example.Tag;
import org.example.TagKind;
import org.example.User;
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
class UpsertRatingWeightTest {

    private static final String PREFIX = "https://upsert-weight-test.invalid/";
    private static final String U_PREFIX = "upsert-wt-user-";
    private static final String T_PREFIX = "upsert-wt-tag-";

    private Article article;
    private User testUser;
    private Tag tag;

    @BeforeEach
    void setup() {
        testUser = new User();
        testUser.setNick(U_PREFIX + System.nanoTime());
        testUser.setPassword("x");

        tag = new Tag();
        tag.setName("UpsertWtTag-" + System.nanoTime());
        tag.setTagKey(T_PREFIX + System.nanoTime());
        tag.setKind(TagKind.ENTITY);

        article = new Article();
        article.setTitle("UpsertWtArticle");
        article.setLink(PREFIX + System.nanoTime());

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.persist(testUser);
            s.persist(tag);
            s.persist(article);
            ArticleTagPercent atp = new ArticleTagPercent(article, tag, 60);
            s.persist(atp);
            tx.commit();
        }
    }

    @AfterEach
    void cleanup() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
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

    private int getUserTagPercent() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Integer p = s.createQuery(
                    "SELECT utp.percent FROM UserTagPercent utp WHERE utp.user.id = :uid AND utp.id.tagId = :tid",
                    Integer.class)
                    .setParameter("uid", testUser.getId()).setParameter("tid", tag.getId())
                    .uniqueResult();
            return p != null ? p : 0;
        }
    }

    @Test
    void likeNewArticle_adjustsWeights() {
        new ReportService().upsertRating(article.getId(), testUser.getId(), 1);
        assertTrue(getUserTagPercent() > 0, "like must increase user_tag_percent for article's tags");
    }

    @Test
    void likeTwiceSameArticle_idempotent_weightsUnchangedOnSecondLike() {
        ReportService svc = new ReportService();
        svc.upsertRating(article.getId(), testUser.getId(), 1);
        int afterFirstLike = getUserTagPercent();

        svc.upsertRating(article.getId(), testUser.getId(), 1);
        int afterSecondLike = getUserTagPercent();

        assertEquals(afterFirstLike, afterSecondLike,
                "second like on same article must be no-op — weights must not be adjusted again");
    }

    @Test
    void neutralNewReport_noWeightChange() {
        new ReportService().upsertRating(article.getId(), testUser.getId(), 0);
        assertEquals(0, getUserTagPercent(), "neutral rating must not create or update user_tag_percent");
    }

    @Test
    void likeToDislike_appliesTwoNegativeSteps() {
        ReportService svc = new ReportService();
        svc.upsertRating(article.getId(), testUser.getId(), 1);
        int afterLike = getUserTagPercent();
        assertTrue(afterLike > 0, "like must produce positive weight");

        svc.upsertRating(article.getId(), testUser.getId(), -1);
        int afterDislike = getUserTagPercent();

        // Two steps of direction=-1 applied: expect afterDislike < afterLike (likely negative)
        assertTrue(afterDislike < afterLike, "like→dislike must apply 2 negative steps, reducing weight below afterLike");
    }

    @Test
    void likeToNeutral_appliesOneNegativeStep() {
        ReportService svc = new ReportService();
        svc.upsertRating(article.getId(), testUser.getId(), 1);
        int afterLike = getUserTagPercent();
        assertTrue(afterLike > 0, "like must produce positive weight");

        svc.upsertRating(article.getId(), testUser.getId(), 0);
        int afterNeutral = getUserTagPercent();

        // One step of direction=-1 applied: weight reduced
        assertTrue(afterNeutral < afterLike, "like→neutral must apply 1 negative step, reducing weight");
    }

    @Test
    void dislikeToLike_appliesTwoPositiveSteps() {
        ReportService svc = new ReportService();
        svc.upsertRating(article.getId(), testUser.getId(), -1);
        int afterDislike = getUserTagPercent();
        assertTrue(afterDislike < 0, "dislike must produce negative weight");

        svc.upsertRating(article.getId(), testUser.getId(), 1);
        int afterLike = getUserTagPercent();

        // Two steps of direction=+1 applied: expect afterLike > afterDislike (likely positive)
        assertTrue(afterLike > afterDislike, "dislike→like must apply 2 positive steps, raising weight above afterDislike");
    }
}
