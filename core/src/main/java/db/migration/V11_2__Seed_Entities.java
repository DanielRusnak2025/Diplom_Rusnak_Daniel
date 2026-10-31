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
 * Seeds entity dictionary: countries, places, organisations, region_names, and their aliases.
 * Source of truth: /seed/entities.yaml
 *
 * getChecksum() reads entities.yaml as UTF-8, normalises CRLF→LF, returns CRC32.
 */
public class V11_2__Seed_Entities extends BaseJavaMigration {

    private static final int EXP_COUNTRIES     = 197;
    private static final int EXP_PLACES        = 120;
    private static final int EXP_ORGANISATIONS = 37;
    private static final int EXP_REGION_NAMES  = 12;
    private static final int EXP_ALIASES       = 1419;
    private static final int EXP_ACRONYMS      = 76;

    @Override
    public Integer getChecksum() {
        try (InputStream is = getClass().getResourceAsStream("/seed/entities.yaml")) {
            if (is == null) throw new RuntimeException("entities.yaml not found on classpath");
            byte[] raw = is.readAllBytes();
            String normalised = new String(raw, StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").replace("\r", "\n");
            CRC32 crc = new CRC32();
            crc.update(normalised.getBytes(StandardCharsets.UTF_8));
            return (int) crc.getValue();
        } catch (IOException e) {
            throw new RuntimeException("Cannot compute entities.yaml checksum", e);
        }
    }

    @Override
    public void migrate(Context context) throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        EntitiesYaml yaml;
        try (InputStream is = getClass().getResourceAsStream("/seed/entities.yaml")) {
            yaml = mapper.readValue(is, EntitiesYaml.class);
        }

        Connection conn = context.getConnection();

        // countries must come first — places reference them by country_id
        Map<String, Integer> countryIdByKey = insertEntities(conn, yaml.countries, "COUNTRY");
        insertPlaces(conn, yaml.places, countryIdByKey);
        insertEntities(conn, yaml.organisations, "ORGANISATION");
        insertEntities(conn, yaml.region_names, "REGION_NAME");

        int aliasCount   = insertAliases(conn, yaml.countries,    false);
        aliasCount      += insertAliases(conn, yaml.places,       false);
        aliasCount      += insertAliases(conn, yaml.organisations, false);
        aliasCount      += insertAliases(conn, yaml.region_names,  false);

        int acronymCount  = insertAliases(conn, yaml.countries,    true);
        acronymCount     += insertAliases(conn, yaml.places,       true);
        acronymCount     += insertAliases(conn, yaml.organisations, true);
        acronymCount     += insertAliases(conn, yaml.region_names,  true);

        assertCount("countries",     yaml.countries.size(),     EXP_COUNTRIES);
        assertCount("places",        yaml.places.size(),        EXP_PLACES);
        assertCount("organisations", yaml.organisations.size(), EXP_ORGANISATIONS);
        assertCount("region_names",  yaml.region_names.size(),  EXP_REGION_NAMES);
        assertCount("aliases",       aliasCount,                EXP_ALIASES);
        assertCount("acronyms",      acronymCount,              EXP_ACRONYMS);
    }

    /** Inserts COUNTRY / ORGANISATION / REGION_NAME rows; returns {entity_key → generated id}. */
    private Map<String, Integer> insertEntities(Connection conn,
                                                List<EntityEntry> entries,
                                                String kind) throws SQLException {
        Map<String, Integer> idMap = new LinkedHashMap<>();
        String sql = "INSERT INTO `entity` (`entity_key`, `kind`, `region_key`) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (EntityEntry e : entries) {
                ps.setString(1, e.key);
                ps.setString(2, kind);
                ps.setString(3, e.region);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        e.generatedId = rs.getInt(1);
                        idMap.put(e.key, e.generatedId);
                    }
                }
            }
        }
        return idMap;
    }

    private void insertPlaces(Connection conn,
                              List<EntityEntry> places,
                              Map<String, Integer> countryIdByKey) throws SQLException {
        String sql = "INSERT INTO `entity` (`entity_key`, `kind`, `country_id`) VALUES (?, 'PLACE', ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (EntityEntry e : places) {
                Integer countryId = countryIdByKey.get(e.country);
                if (countryId == null) throw new RuntimeException(
                        "Place '" + e.key + "' references unknown country '" + e.country + "'");
                ps.setString(1, e.key);
                ps.setInt(2, countryId);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) e.generatedId = rs.getInt(1);
                }
            }
        }
    }

    /**
     * Inserts aliases or acronyms for a list of entities.
     * @param acronymsOnly true → insert acronym rows (language=NULL, is_acronym=TRUE);
     *                     false → insert language-keyed alias rows
     */
    private int insertAliases(Connection conn,
                               List<EntityEntry> entries,
                               boolean acronymsOnly) throws SQLException {
        String sql = "INSERT INTO `entity_alias` (`entity_id`, `language`, `pattern`, `is_acronym`) " +
                     "VALUES (?, ?, ?, ?)";
        int count = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (EntityEntry e : entries) {
                if (acronymsOnly) {
                    if (e.acronyms == null) continue;
                    for (String acr : e.acronyms) {
                        ps.setInt(1, e.generatedId);
                        ps.setNull(2, Types.VARCHAR);
                        ps.setString(3, acr);
                        ps.setBoolean(4, true);
                        ps.addBatch();
                        count++;
                    }
                } else {
                    if (e.aliases == null) continue;
                    for (Map.Entry<String, List<String>> langEntry : e.aliases.entrySet()) {
                        String lang = langEntry.getKey();
                        for (String pattern : langEntry.getValue()) {
                            ps.setInt(1, e.generatedId);
                            ps.setString(2, lang);
                            ps.setString(3, pattern);
                            ps.setBoolean(4, false);
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

    // ── YAML POJOs ──────────────────────────────────────────────────────────

    public static class EntitiesYaml {
        public int version;
        public List<EntityEntry> countries    = new ArrayList<>();
        public List<EntityEntry> places       = new ArrayList<>();
        public List<EntityEntry> organisations = new ArrayList<>();
        public List<EntityEntry> region_names = new ArrayList<>();
    }

    public static class EntityEntry {
        public String key;
        public String region;   // COUNTRY / ORGANISATION / REGION_NAME
        public String country;  // PLACE only
        public Map<String, List<String>> aliases;
        public List<String> acronyms;
        public transient int generatedId;
    }
}
