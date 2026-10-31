package db.migration;

import org.example.util.UrlNormalizer;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V7_2 — Populate article_queue with all existing articles (status=PENDING).
 *
 * Each article's link is normalized via UrlNormalizer and hashed (SHA-256).
 * Hash collision → migration fails (signals a data issue to fix before retrying).
 * Broken/unsupported URL → migration fails with the offending article id.
 */
public class V7_2__Populate_Article_Queue extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();

        Map<Integer, String> articles = loadArticles(conn);
        int expected = articles.size();

        checkCollisions(articles);
        insertRows(conn, articles);
        assertCount(conn, expected);
    }

    private Map<Integer, String> loadArticles(Connection conn) throws SQLException {
        Map<Integer, String> map = new LinkedHashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, link FROM article ORDER BY id")) {
            while (rs.next()) {
                map.put(rs.getInt("id"), rs.getString("link"));
            }
        }
        return map;
    }

    private void checkCollisions(Map<Integer, String> articles) {
        Map<String, Integer> hashToId = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> e : articles.entrySet()) {
            int    id  = e.getKey();
            String url = e.getValue();
            String normalized;
            try {
                normalized = UrlNormalizer.normalize(url);
            } catch (IllegalArgumentException ex) {
                throw new RuntimeException(
                        "Article id=" + id + " has bad URL '" + url + "': " + ex.getMessage(), ex);
            }
            String hash = UrlNormalizer.hash(normalized);
            Integer existing = hashToId.put(hash, id);
            if (existing != null) {
                throw new RuntimeException(
                        "URL hash collision between article id=" + existing +
                        " and id=" + id +
                        " (normalized: " + normalized + ")");
            }
        }
    }

    private void insertRows(Connection conn, Map<Integer, String> articles) throws SQLException {
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO article_queue (url, url_hash, article_id, status, created_at) " +
                "VALUES (?, ?, ?, 'PENDING', NOW(6))")) {
            for (Map.Entry<Integer, String> e : articles.entrySet()) {
                String normalized = UrlNormalizer.normalize(e.getValue());
                String hash       = UrlNormalizer.hash(normalized);
                ins.setString(1, normalized);
                ins.setString(2, hash);
                ins.setInt(3, e.getKey());
                ins.addBatch();
            }
            ins.executeBatch();
        }
    }

    private void assertCount(Connection conn, int expected) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM article_queue")) {
            rs.next();
            int actual = rs.getInt(1);
            if (actual != expected) {
                throw new RuntimeException(
                        "V7_2 assertion failed: expected " + expected +
                        " rows in article_queue, got " + actual);
            }
        }
    }
}
