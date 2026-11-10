package db.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;

public class V13_2__seed_taxonomy_name_ru extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/seed/taxonomy_names_ru.yaml")) {
            if (is == null) {
                throw new RuntimeException("taxonomy_names_ru.yaml not found on classpath");
            }

            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            @SuppressWarnings("unchecked")
            Map<String, Object> root = mapper.readValue(is, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, String> names = (Map<String, String>) root.get("names");

            if (names == null || names.isEmpty()) {
                throw new RuntimeException("taxonomy_names_ru.yaml has no 'names' section");
            }

            Connection conn = context.getConnection();
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE tag SET name_ru = ? WHERE tag_key = ?")) {
                int updated = 0;
                for (Map.Entry<String, String> entry : names.entrySet()) {
                    String tagKey = entry.getKey();
                    String nameRu = entry.getValue();
                    ps.setString(1, nameRu);
                    ps.setString(2, tagKey);
                    ps.addBatch();
                    updated++;
                    if (updated % 50 == 0) ps.executeBatch();
                }
                ps.executeBatch();
            }
        }
    }
}
