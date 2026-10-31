package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.ResultSet;
import java.sql.Statement;

public class V9_1__Check_Report_Reason extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement st = context.getConnection().createStatement()) {
            ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM report WHERE reason IS NOT NULL AND reason <> ''");
            rs.next();
            long count = rs.getLong(1);
            if (count > 0) {
                throw new RuntimeException(
                        "V9 aborted: " + count + " report row(s) have non-empty reason text. "
                        + "Resolve manually before running this migration.");
            }
        }
    }
}
