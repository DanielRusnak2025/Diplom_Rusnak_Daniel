package org.example.test;

import org.example.util.HibernateUtil;
import org.flywaydb.core.Flyway;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JUnit 5 extension: clean db_use_test_it, apply baseline schema, then run all Flyway migrations.
 * Runs once per JVM (AtomicBoolean guard) — subsequent test classes reuse the migrated schema.
 *
 * Usage: @ExtendWith(TestDb.class) on any DB-writing test class.
 */
public class TestDb implements BeforeAllCallback {

    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (!initialized.compareAndSet(false, true)) return;

        // HibernateUtil.resolveUrl() is safe here: lazy SessionFactory is NOT triggered.
        Configuration hCfg = new Configuration().configure();
        String rawUrl = hCfg.getProperty("hibernate.connection.url");
        String user   = hCfg.getProperty("hibernate.connection.username");
        String url    = HibernateUtil.resolveUrl(rawUrl);

        if (!url.contains("/db_use_test_it")) {
            throw new IllegalStateException(
                    "TestDb must run against db_use_test_it; got: " + url
                    + "\nSet -Dnewsdove.db.name=db_use_test_it (see build.gradle.kts)");
        }

        String pass = hCfg.getProperty("hibernate.connection.password");

        Flyway flyway = Flyway.configure()
                .dataSource(url, user, pass)
                .baselineVersion("1")
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();

        flyway.clean();
        executeBaselineSchema(url, user, pass);
        flyway.baseline();
        flyway.migrate();
    }

    private static void executeBaselineSchema(String url, String user, String pass)
            throws Exception {
        String sql;
        try (InputStream is = TestDb.class.getResourceAsStream("/db/baseline_schema.sql")) {
            if (is == null) throw new IOException(
                    "baseline_schema.sql not found on classpath at /db/baseline_schema.sql");
            sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement st = conn.createStatement()) {
            for (String stmt : sql.split(";")) {
                String trimmed = stmt.strip();
                if (!trimmed.isEmpty()) {
                    st.execute(trimmed);
                }
            }
        }
    }
}
