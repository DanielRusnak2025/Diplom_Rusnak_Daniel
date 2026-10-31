package org.example.dao;

import jakarta.persistence.PersistenceException;
import org.example.Article;
import org.example.Report;
import org.example.User;
import org.example.dao.impl.ArticleDAOImpl;
import org.example.test.TestDb;
import org.example.util.HibernateUtil;
import org.example.util.UrlNormalizer;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Article link_hash / created_at lifecycle and findOldArticles.
 * Runs against db_use_test_it via TestDb extension.
 */
@ExtendWith(TestDb.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArticleMetaTest {

    private static final String PREFIX = "https://meta-test.invalid/";

    private final ArticleDAO dao = new ArticleDAOImpl();

    @AfterEach
    void cleanup() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            // Report must be deleted before Article (FK)
            s.createNativeMutationQuery(
                    "DELETE r FROM report r " +
                    "JOIN article a ON a.id = r.article_id " +
                    "WHERE a.link LIKE :pfx")
                    .setParameter("pfx", PREFIX + "%")
                    .executeUpdate();
            s.createNativeMutationQuery(
                    "DELETE FROM article WHERE link LIKE :pfx")
                    .setParameter("pfx", PREFIX + "%")
                    .executeUpdate();
            s.createNativeMutationQuery(
                    "DELETE FROM user WHERE nick LIKE 'test-meta-%'")
                    .executeUpdate();
            tx.commit();
        }
    }

    @Test
    void linkHashAndCreatedAtComputedOnPersist() {
        Article a = new Article();
        a.setTitle("Meta test article");
        a.setLink(PREFIX + "v1?utm_source=email");

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.persist(a);
            tx.commit();
        }

        String expected = UrlNormalizer.hash(
                UrlNormalizer.normalize(PREFIX + "v1?utm_source=email"));
        assertEquals(expected, a.getLinkHash(), "linkHash must equal hash(normalize(link))");
        assertEquals(64, a.getLinkHash().length(), "SHA-256 hex must be 64 chars");
        assertTrue(a.getLinkHash().matches("[0-9a-f]{64}"), "must be lowercase hex");
        assertNotNull(a.getCreatedAt(), "createdAt must be set by @PrePersist");
    }

    @Test
    void sameNormalizedLinkRejectsSecond() {
        // Both normalize to PREFIX + "dup" → same hash
        String url1 = PREFIX + "dup";
        String url2 = PREFIX.toUpperCase() + "dup?utm_campaign=email";

        Article a1 = new Article();
        a1.setTitle("Dup article 1");
        a1.setLink(url1);

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.persist(a1);
            tx.commit();
        }

        Article a2 = new Article();
        a2.setTitle("Dup article 2");
        a2.setLink(url2);

        assertThrows(PersistenceException.class, () -> {
            try (Session s = HibernateUtil.getSessionFactory().openSession()) {
                Transaction tx = s.beginTransaction();
                s.persist(a2);
                tx.commit();
            }
        }, "UNIQUE constraint on link_hash must reject duplicate normalized link");
    }

    @Test
    void findByLinkHashReturnsArticle() {
        String url = PREFIX + "find-me";
        Article a = new Article();
        a.setTitle("Find-me article");
        a.setLink(url);

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.persist(a);
            tx.commit();
        }

        String hash = UrlNormalizer.hash(UrlNormalizer.normalize(url));
        Optional<Article> found = dao.findByLinkHash(hash);
        assertTrue(found.isPresent(), "findByLinkHash must find the saved article");
        assertEquals(url, found.get().getLink());
    }

    @Test
    void findOldArticlesExcludesReported() {
        Date oldDate = Date.from(
                LocalDate.now().minusYears(1)
                        .atStartOfDay(ZoneId.systemDefault()).toInstant());

        Article withReport = new Article();
        withReport.setTitle("Old article with report");
        withReport.setLink(PREFIX + "old-reported");
        withReport.setPublicationDate(oldDate);

        Article withoutReport = new Article();
        withoutReport.setTitle("Old article without report");
        withoutReport.setLink(PREFIX + "old-clean");
        withoutReport.setPublicationDate(oldDate);

        User testUser = new User();
        testUser.setNick("test-meta-user");
        testUser.setPassword("x");

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Transaction tx = s.beginTransaction();
            s.persist(testUser);
            s.persist(withReport);
            s.persist(withoutReport);
            s.persist(new Report(withReport, testUser, 1));
            tx.commit();
        }

        List<String> links = dao.findOldArticles(0).stream()
                .map(Article::getLink)
                .filter(l -> l.startsWith(PREFIX))
                .collect(Collectors.toList());

        assertFalse(links.contains(PREFIX + "old-reported"),
                "Article with report must be excluded from cleanup candidates");
        assertTrue(links.contains(PREFIX + "old-clean"),
                "Article without report must be included in cleanup candidates");
    }
}
