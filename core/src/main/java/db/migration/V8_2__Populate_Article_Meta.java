package db.migration;

import org.example.util.UrlNormalizer;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class V8_2__Populate_Article_Meta extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();

        Map<Integer, String> links = loadArticleLinks(conn);
        Map<Integer, Date>   pubs  = loadPublicationDates(conn);

        checkHashCollisions(links);
        checkQueueConsistency(conn, links);
        populate(conn, links, pubs);
        assertNoNulls(conn, links.size());
    }

    private Map<Integer, String> loadArticleLinks(Connection conn) throws SQLException {
        Map<Integer, String> map = new LinkedHashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, link FROM article ORDER BY id")) {
            while (rs.next()) {
                map.put(rs.getInt("id"), rs.getString("link"));
            }
        }
        return map;
    }

    private Map<Integer, java.sql.Date> loadPublicationDates(Connection conn) throws SQLException {
        Map<Integer, java.sql.Date> map = new LinkedHashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, publication_date FROM article")) {
            while (rs.next()) {
                map.put(rs.getInt("id"), rs.getDate("publication_date"));
            }
        }
        return map;
    }

    private void checkHashCollisions(Map<Integer, String> links) {
        Map<String, Integer> seen = new LinkedHashMap<>();
        List<String> collisions = new ArrayList<>();
        for (Map.Entry<Integer, String> e : links.entrySet()) {
            String hash = UrlNormalizer.hash(UrlNormalizer.normalize(e.getValue()));
            Integer prev = seen.put(hash, e.getKey());
            if (prev != null) {
                collisions.add("hash " + hash + " shared by id=" + prev + " and id=" + e.getKey());
            }
        }
        if (!collisions.isEmpty()) {
            throw new RuntimeException("V8_2: link_hash collisions:\n" + String.join("\n", collisions));
        }
    }

    private void checkQueueConsistency(Connection conn, Map<Integer, String> links) throws SQLException {
        List<String> mismatches = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT article_id, url_hash FROM article_queue WHERE article_id IS NOT NULL")) {
            while (rs.next()) {
                int articleId = rs.getInt("article_id");
                String queueHash = rs.getString("url_hash");
                String link = links.get(articleId);
                if (link == null) continue;
                String computed = UrlNormalizer.hash(UrlNormalizer.normalize(link));
                if (!computed.equals(queueHash)) {
                    mismatches.add("article_id=" + articleId
                            + " computed=" + computed + " queue=" + queueHash);
                }
            }
        }
        if (!mismatches.isEmpty()) {
            throw new RuntimeException(
                    "V8_2: link_hash vs article_queue.url_hash mismatch:\n"
                    + String.join("\n", mismatches));
        }
    }

    private void populate(Connection conn, Map<Integer, String> links,
                          Map<Integer, java.sql.Date> pubs) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE article SET link_hash = ?, created_at = ? WHERE id = ?")) {
            for (Map.Entry<Integer, String> e : links.entrySet()) {
                int id = e.getKey();
                String hash = UrlNormalizer.hash(UrlNormalizer.normalize(e.getValue()));
                java.sql.Date pub = pubs.get(id);
                Timestamp ts = pub != null
                        ? new Timestamp(pub.getTime())
                        : new Timestamp(System.currentTimeMillis());
                ps.setString(1, hash);
                ps.setTimestamp(2, ts);
                ps.setInt(3, id);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void assertNoNulls(Connection conn, int expectedCount) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) AS total, " +
                     "SUM(link_hash IS NULL) AS null_hash, " +
                     "SUM(created_at IS NULL) AS null_cat " +
                     "FROM article")) {
            rs.next();
            int total    = rs.getInt("total");
            int nullHash = rs.getInt("null_hash");
            int nullCat  = rs.getInt("null_cat");
            if (total != expectedCount) {
                throw new RuntimeException(
                        "V8_2: expected " + expectedCount + " articles, got " + total);
            }
            if (nullHash != 0) {
                throw new RuntimeException(
                        "V8_2: " + nullHash + " articles still have NULL link_hash");
            }
            if (nullCat != 0) {
                throw new RuntimeException(
                        "V8_2: " + nullCat + " articles still have NULL created_at");
            }
        }
    }
}
