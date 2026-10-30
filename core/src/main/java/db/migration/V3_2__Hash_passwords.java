package db.migration;

import org.example.security.PasswordHasher;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class V3_2__Hash_passwords extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();

        // Discover actual first-password column name: Hibernate may have used
        // camelCase "firstPassword" (no @Column(name=...) on the field) or
        // snake_case "first_password". Check information_schema to be safe.
        String firstPwdCol = findFirstPasswordColumn(conn);

        String selectSql = firstPwdCol != null
                ? "SELECT id, password, `" + firstPwdCol + "` AS fp FROM `user`"
                : "SELECT id, password, NULL AS fp FROM `user`";

        record UserRow(int id, String password, String firstPassword) {}
        List<UserRow> users = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    users.add(new UserRow(
                            rs.getInt("id"),
                            rs.getString("password"),
                            rs.getString("fp")));
                }
            }
        }

        try (PreparedStatement updateUser = conn.prepareStatement(
                "UPDATE `user` SET password = ?, password_changed_at = ? WHERE id = ?");
             PreparedStatement insertHistory = conn.prepareStatement(
                "INSERT INTO password_history (user_id, password_hash, created_at) VALUES (?, ?, ?)")) {

            for (UserRow u : users) {
                if (u.password().startsWith("$argon2")) continue;

                String newHash = PasswordHasher.hash(u.password());

                // Self-check: verify immediately after hashing; exception rolls back migration
                if (!PasswordHasher.verify(newHash, u.password())) {
                    throw new IllegalStateException(
                            "Argon2 verify failed for user id=" + u.id() + " — aborting migration");
                }

                LocalDateTime now = LocalDateTime.now();

                // first_password/firstPassword → history (if distinct from current)
                String fp = u.firstPassword();
                if (fp != null && !fp.isEmpty() && !fp.equals(u.password())) {
                    String fpHash = PasswordHasher.hash(fp);
                    if (!PasswordHasher.verify(fpHash, fp)) {
                        throw new IllegalStateException(
                                "Argon2 verify failed for firstPassword, user id=" + u.id());
                    }
                    insertHistory.setInt(1, u.id());
                    insertHistory.setString(2, fpHash);
                    insertHistory.setObject(3, now.minusSeconds(1)); // JDBC 4.2
                    insertHistory.executeUpdate();
                }

                // current password → history
                insertHistory.setInt(1, u.id());
                insertHistory.setString(2, newHash);
                insertHistory.setObject(3, now);
                insertHistory.executeUpdate();

                // update user: new hash + password_changed_at
                updateUser.setString(1, newHash);
                updateUser.setObject(2, now);
                updateUser.setInt(3, u.id());
                updateUser.executeUpdate();
            }
        }
    }

    /** Returns the actual column name ("firstPassword" or "first_password"), or null if absent. */
    private static String findFirstPasswordColumn(Connection conn) throws SQLException {
        try (ResultSet cols = conn.getMetaData().getColumns(null, null, "user", null)) {
            while (cols.next()) {
                String col = cols.getString("COLUMN_NAME");
                if ("firstPassword".equals(col) || "first_password".equals(col)) {
                    return col;
                }
            }
        }
        return null;
    }
}
