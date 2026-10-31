package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Generates tag_key for all existing ENTITY tags.
 *
 * Algorithm (Java-equivalent of [^\p{L}\p{N}]+ → "_"):
 *   1. toLowerCase(Locale.ROOT)
 *   2. replaceAll("[^\p{L}\p{N}]+", "_")  — keeps Unicode letters and digits
 *   3. strip leading/trailing "_"
 *   4. prefix "ent:"
 *
 * Dry-run verified 0 collisions among 257 existing tags.
 * On any collision → RuntimeException (transaction rollback, no silent suffix).
 */
public class V5_2__Generate_Entity_Tag_Keys extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Map<String, Integer> seen = new HashMap<>();
        Connection conn = context.getConnection();

        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, name FROM tag ORDER BY id")) {

            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                String normalized = normalizeJava(name);

                if (normalized.isEmpty()) {
                    throw new RuntimeException(
                        "Empty tag_key after normalization for tag id=" + id
                        + " name='" + name + "'");
                }

                String key = "ent:" + normalized;

                if (seen.containsKey(key)) {
                    throw new RuntimeException(
                        "tag_key collision: '" + key + "' already assigned to id="
                        + seen.get(key) + ", conflicts with id=" + id
                        + " name='" + name + "'");
                }
                seen.put(key, id);

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE tag SET tag_key = ? WHERE id = ?")) {
                    ps.setString(1, key);
                    ps.setInt(2, id);
                    ps.executeUpdate();
                }
            }
        }
    }

    private static String normalizeJava(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        String replaced = lower.replaceAll("[^\\p{L}\\p{N}]+", "_");
        return replaced.replaceAll("^_+|_+$", "");
    }
}
