package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * V6_1 — Create user_language and user_region tables;
 * migrate preferred_language and preferred_region from user.
 *
 * Language normalization: Russian/ru → ru, Romanian → ro, en → en.
 * Region normalization:   US → north_america, Moldova → eastern_europe,
 *                         EU/Europe → western_europe, Asia → east_southeast_asia.
 * NULL values are skipped (no row inserted). Any unknown value fails the migration.
 */
public class V6_1__User_Language_Region extends BaseJavaMigration {

    private static final Map<String, String> LANG_MAP = new LinkedHashMap<>();
    private static final Map<String, String> REGION_MAP = new LinkedHashMap<>();

    static {
        LANG_MAP.put("Russian",  "ru");
        LANG_MAP.put("ru",       "ru");
        LANG_MAP.put("Romanian", "ro");
        LANG_MAP.put("en",       "en");

        REGION_MAP.put("US",      "north_america");
        REGION_MAP.put("Moldova", "eastern_europe");
        REGION_MAP.put("EU",      "western_europe");
        REGION_MAP.put("Europe",  "western_europe");
        REGION_MAP.put("Asia",    "east_southeast_asia");
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();
        createTables(conn);
        int langCount   = migrateLanguages(conn);
        int regionCount = migrateRegions(conn);
        assertCount(conn, "user_language", langCount);
        assertCount(conn, "user_region",   regionCount);
    }

    private void createTables(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute(
                "CREATE TABLE user_language (" +
                "  user_id  INT         NOT NULL, " +
                "  language VARCHAR(5)  NOT NULL, " +
                "  PRIMARY KEY (user_id, language), " +
                "  CONSTRAINT chk_ul_lang CHECK (language REGEXP '^[a-z]{2}$'), " +
                "  CONSTRAINT fk_ul_user  FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );
            st.execute(
                "CREATE TABLE user_region (" +
                "  user_id    INT         NOT NULL, " +
                "  region_key VARCHAR(30) NOT NULL, " +
                "  PRIMARY KEY (user_id, region_key), " +
                "  CONSTRAINT fk_ur_user   FOREIGN KEY (user_id)    REFERENCES user(id)             ON DELETE CASCADE, " +
                "  CONSTRAINT fk_ur_region FOREIGN KEY (region_key) REFERENCES region(region_key)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );
        }
    }

    private int migrateLanguages(Connection conn) throws SQLException {
        try (PreparedStatement sel = conn.prepareStatement(
                 "SELECT id, nick, preferred_language FROM user WHERE preferred_language IS NOT NULL");
             PreparedStatement ins = conn.prepareStatement(
                 "INSERT INTO user_language (user_id, language) VALUES (?, ?)")) {
            ResultSet rs = sel.executeQuery();
            int count = 0;
            while (rs.next()) {
                int    id   = rs.getInt("id");
                String nick = rs.getString("nick");
                String raw  = rs.getString("preferred_language");
                String code = LANG_MAP.get(raw);
                if (code == null) {
                    throw new RuntimeException(
                        "Unknown preferred_language '" + raw + "' for nick='" + nick + "'" +
                        " — add mapping to LANG_MAP or clean data before migrating."
                    );
                }
                ins.setInt(1, id);
                ins.setString(2, code);
                ins.addBatch();
                count++;
            }
            ins.executeBatch();
            return count;
        }
    }

    private int migrateRegions(Connection conn) throws SQLException {
        try (PreparedStatement sel = conn.prepareStatement(
                 "SELECT id, nick, preferred_region FROM user WHERE preferred_region IS NOT NULL");
             PreparedStatement ins = conn.prepareStatement(
                 "INSERT INTO user_region (user_id, region_key) VALUES (?, ?)")) {
            ResultSet rs = sel.executeQuery();
            int count = 0;
            while (rs.next()) {
                int    id   = rs.getInt("id");
                String nick = rs.getString("nick");
                String raw  = rs.getString("preferred_region");
                String key  = REGION_MAP.get(raw);
                if (key == null) {
                    throw new RuntimeException(
                        "Unknown preferred_region '" + raw + "' for nick='" + nick + "'" +
                        " — add mapping to REGION_MAP or clean data before migrating."
                    );
                }
                ins.setInt(1, id);
                ins.setString(2, key);
                ins.addBatch();
                count++;
            }
            ins.executeBatch();
            return count;
        }
    }

    private static void assertCount(Connection conn, String table, int expected) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM `" + table + "`")) {
            rs.next();
            int actual = rs.getInt(1);
            if (actual != expected) {
                throw new RuntimeException(
                    "Assertion failed: expected " + expected + " rows in " + table + ", got " + actual
                );
            }
        }
    }
}
