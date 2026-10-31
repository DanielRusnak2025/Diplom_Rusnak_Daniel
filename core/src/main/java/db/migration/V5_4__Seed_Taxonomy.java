package db.migration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.zip.CRC32;

/**
 * Seeds taxonomy: 12 regions, 15 CATEGORY tags, 83 SUBTOPIC tags (68 named + 15 General),
 * 1209 keyword patterns.  Source of truth: /seed/taxonomy.yaml.
 *
 * getChecksum() reads taxonomy.yaml as UTF-8, normalises CRLF→LF, returns CRC32.
 * If taxonomy.yaml changes after this migration was applied, flyway validate fails.
 * Future taxonomy updates must use a new migration with upsert by tag_key.
 *
 * Final step: adds FK article.article_region → region (safe only after region rows exist).
 */
public class V5_4__Seed_Taxonomy extends BaseJavaMigration {

    private static final int EXP_REGIONS    = 12;
    private static final int EXP_CATEGORIES = 15;
    private static final int EXP_SUBTOPICS  = 83;   // 68 named + 15 General
    private static final int EXP_KEYWORDS   = 1209;

    // ──────────────────────────────────────────────────────────────────────────
    // Checksum
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public Integer getChecksum() {
        try (InputStream is = getClass().getResourceAsStream("/seed/taxonomy.yaml")) {
            if (is == null) {
                throw new RuntimeException("taxonomy.yaml not found on classpath (/seed/taxonomy.yaml)");
            }
            byte[] raw = is.readAllBytes();
            String normalised = new String(raw, StandardCharsets.UTF_8)
                    .replace("\r\n", "\n")
                    .replace("\r", "\n");
            CRC32 crc = new CRC32();
            crc.update(normalised.getBytes(StandardCharsets.UTF_8));
            return (int) crc.getValue();
        } catch (IOException e) {
            throw new RuntimeException("Cannot compute taxonomy.yaml checksum", e);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Migration
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public void migrate(Context context) throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        TaxonomyYaml taxonomy;
        try (InputStream is = getClass().getResourceAsStream("/seed/taxonomy.yaml")) {
            taxonomy = mapper.readValue(is, TaxonomyYaml.class);
        }

        Connection conn = context.getConnection();

        int regionCount = insertRegions(conn, taxonomy.regions);
        int catCount    = insertCategories(conn, taxonomy.categories);
        int subCount    = insertSubtopics(conn, taxonomy.categories);
        int kwCount     = insertKeywords(conn, taxonomy.categories);

        assertCount("regions",    regionCount, EXP_REGIONS);
        assertCount("categories", catCount,    EXP_CATEGORIES);
        assertCount("subtopics",  subCount,    EXP_SUBTOPICS);
        assertCount("keywords",   kwCount,     EXP_KEYWORDS);

        // FK article.article_region → region (added after region rows are committed).
        // article.article_region values verified ⊂ 12 keys in pre-migration check.
        try (Statement st = conn.createStatement()) {
            st.execute(
                "ALTER TABLE `article` ADD CONSTRAINT `fk_article_region` " +
                "FOREIGN KEY (`article_region`) REFERENCES `region` (`region_key`)"
            );
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Insert helpers
    // ──────────────────────────────────────────────────────────────────────────

    private int insertRegions(Connection conn, List<RegionEntry> regions) throws SQLException {
        int count = 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO `region` (`region_key`, `name`) VALUES (?, ?)")) {
            for (RegionEntry r : regions) {
                ps.setString(1, r.key);
                ps.setString(2, r.name);
                ps.addBatch();
                count++;
            }
            ps.executeBatch();
        }
        return count;
    }

    private int insertCategories(Connection conn, List<CategoryEntry> categories) throws SQLException {
        int count = 0;
        String sql = "INSERT INTO `tag` (`name`, `kind`, `tag_key`, `description`) " +
                     "VALUES (?, 'CATEGORY', ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (CategoryEntry cat : categories) {
                ps.setString(1, cat.name);
                ps.setString(2, "cat:" + cat.key);
                ps.setString(3, cat.description);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) cat.generatedId = rs.getInt(1);
                }
                count++;
            }
        }
        return count;
    }

    private int insertSubtopics(Connection conn, List<CategoryEntry> categories) throws SQLException {
        int count = 0;
        String sql = "INSERT INTO `tag` (`name`, `kind`, `tag_key`, `parent_id`) " +
                     "VALUES (?, 'SUBTOPIC', ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (CategoryEntry cat : categories) {
                // Auto-generated "General" subtopic — no keywords
                ps.setString(1, "General");
                ps.setString(2, "sub:" + cat.key + "/general");
                ps.setInt(3, cat.generatedId);
                ps.executeUpdate();
                count++;

                // Named subtopics
                for (SubtopicEntry sub : cat.subtopics) {
                    ps.setString(1, sub.name);
                    ps.setString(2, "sub:" + cat.key + "/" + sub.key);
                    ps.setInt(3, cat.generatedId);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) sub.generatedId = rs.getInt(1);
                    }
                    count++;
                }
            }
        }
        return count;
    }

    private int insertKeywords(Connection conn, List<CategoryEntry> categories) throws SQLException {
        int count = 0;
        String sql = "INSERT INTO `tag_keyword` (`tag_id`, `pattern`, `language`) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (CategoryEntry cat : categories) {
                for (SubtopicEntry sub : cat.subtopics) {
                    if (sub.keywords == null || sub.keywords.isEmpty()) continue;
                    for (Map.Entry<String, List<String>> langEntry : sub.keywords.entrySet()) {
                        String lang = langEntry.getKey();
                        for (String pattern : langEntry.getValue()) {
                            ps.setInt(1, sub.generatedId);
                            ps.setString(2, pattern);
                            ps.setString(3, lang);
                            ps.addBatch();
                            count++;
                        }
                    }
                }
            }
            ps.executeBatch();
        }
        return count;
    }

    private static void assertCount(String what, int actual, int expected) {
        if (actual != expected) {
            throw new RuntimeException(
                "Seed count mismatch [" + what + "]: expected " + expected + ", got " + actual);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POJOs for taxonomy.yaml deserialization
    // ──────────────────────────────────────────────────────────────────────────

    public static class TaxonomyYaml {
        public int version;
        public List<RegionEntry>   regions    = new ArrayList<>();
        public List<CategoryEntry> categories = new ArrayList<>();
    }

    public static class RegionEntry {
        public String key;
        public String name;
    }

    public static class CategoryEntry {
        public String key;
        public String name;
        public String description;
        public List<SubtopicEntry> subtopics = new ArrayList<>();
        public transient int generatedId;
    }

    public static class SubtopicEntry {
        public String key;
        public String name;
        public Map<String, List<String>> keywords;
        public transient int generatedId;
    }
}
