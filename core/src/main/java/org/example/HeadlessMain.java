package org.example;

import org.example.email.EmailConfig;
import org.example.scheduler.EmailScheduler;
import org.example.service.ArticleService;
import org.example.util.HibernateUtil;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.output.ValidateResult;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class HeadlessMain {

    private static final Logger log = LoggerFactory.getLogger(HeadlessMain.class);

    public static void main(String[] args) {
        boolean migrateMode = Arrays.asList(args).contains("--migrate");
        boolean schedulerEnabled = EmailConfig.isSchedulerEnabled();
        boolean cleanupEnabled   = EmailConfig.isCleanupEnabled();

        // Read DB config from hibernate.cfg.xml without building SessionFactory.
        Configuration hCfg = new Configuration().configure();
        String dbUrl  = HibernateUtil.resolveUrl(hCfg.getProperty("hibernate.connection.url"));
        String dbUser = hCfg.getProperty("hibernate.connection.username");
        String dbPass = hCfg.getProperty("hibernate.connection.password");

        log.info("[Config] mode={}  scheduler.enabled={}  cleanup.enabled={}  db.url={}  db.user={}",
                migrateMode ? "MIGRATE" : "RUN",
                schedulerEnabled, cleanupEnabled, dbUrl, dbUser);

        if (dbUrl == null || !dbUrl.contains("db_use_test")) {
            log.error("[SAFETY] DB is not db_use_test: {}. Fix hibernate.cfg.xml.", dbUrl);
            System.exit(1);
        }

        // ── Flyway ────────────────────────────────────────────────────────────
        // Must run before HibernateUtil loads (static init builds SessionFactory
        // with hbm2ddl=validate, which requires the schema to be up to date).
        Flyway flyway = Flyway.configure()
                .dataSource(dbUrl, dbUser, dbPass)
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .locations("classpath:db/migration")
                .load();

        if (migrateMode) {
            log.info("[Flyway] Running migrations...");
            var result = flyway.migrate();
            log.info("[Flyway] Applied {} migration(s). Current version: {}",
                    result.migrationsExecuted,
                    result.targetSchemaVersion);
            System.exit(0);
        }

        // Normal start: fail fast on pending migrations OR checksum mismatches.
        MigrationInfoService info = flyway.info();
        MigrationInfo[] pending = info.pending();
        if (pending.length > 0) {
            for (MigrationInfo m : pending) {
                log.error("[Flyway] Pending: {} — {}", m.getVersion(), m.getDescription());
            }
            log.error("[Flyway] {} pending migration(s). Take a dump, then run with --migrate.",
                    pending.length);
            System.exit(1);
        }

        // Also detect tampering with already-applied migrations (checksum drift).
        ValidateResult vr = flyway.validateWithResult();
        if (!vr.validationSuccessful) {
            log.error("[Flyway] Schema inconsistency — applied migration changed or missing.");
            log.error("[Flyway] {}", vr.getAllErrorMessages());
            System.exit(1);
        }

        log.info("[Flyway] Schema up to date at version {}",
                info.current() != null ? info.current().getVersion() : "baseline");

        // ── Hibernate SessionFactory (hbm2ddl=validate) ───────────────────────
        ArticleService articleService = new ArticleService();
        long count = articleService.getTotalArticles();
        log.info("[DB] articles in db_use_test: {}", count);

        if (!schedulerEnabled) {
            log.info("[Config] scheduler.enabled=false — exiting.");
            return;
        }

        EmailScheduler emailScheduler = new EmailScheduler();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown: stopping EmailScheduler...");
            emailScheduler.stop();
        }));
        emailScheduler.start();
    }
}
