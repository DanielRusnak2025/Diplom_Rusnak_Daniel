package org.example;

import org.example.ai.OllamaClassifier;
import org.example.email.EmailConfig;
import org.example.pipeline.BenchmarkScorer;
import org.example.pipeline.ModelComparisonRunner;
import org.example.pipeline.PageFetcherLoader;
import org.example.pipeline.QueueWorker;
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

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

public class HeadlessMain {

    private static final Logger log = LoggerFactory.getLogger(HeadlessMain.class);

    public static void main(String[] args) {
        // --score-benchmark needs no DB — handle before Flyway/Hibernate init
        String scoreCsvArg = parseScoreBenchmark(args);
        if (scoreCsvArg != null) {
            String goldArg = parseGold(args);
            try {
                Path goldPath = goldArg != null ? Path.of(goldArg) : null;
                BenchmarkScorer.appendToReport(Path.of(scoreCsvArg), goldPath);
                log.info("[score-benchmark] Done.");
            } catch (Exception e) {
                log.error("[score-benchmark] failed: {}", e.getMessage(), e);
                System.exit(1);
            }
            System.exit(0);
        }

        boolean migrateMode = Arrays.asList(args).contains("--migrate");
        int processQueueCount = parseProcessQueue(args);
        List<String> compareModels = parseCompareModels(args);
        int compareLimit = parseCompareLimit(args);
        boolean schedulerEnabled = EmailConfig.isSchedulerEnabled();
        boolean cleanupEnabled   = EmailConfig.isCleanupEnabled();

        // Read DB config from hibernate.cfg.xml without building SessionFactory.
        Configuration hCfg = new Configuration().configure();
        String dbUrl  = HibernateUtil.resolveUrl(hCfg.getProperty("hibernate.connection.url"));
        String dbUser = hCfg.getProperty("hibernate.connection.username");
        String dbPass = hCfg.getProperty("hibernate.connection.password");

        String mode = migrateMode ? "MIGRATE"
                    : processQueueCount > 0 ? "PROCESS-QUEUE(" + processQueueCount + ")"
                    : !compareModels.isEmpty() ? "COMPARE-MODELS"
                    : "RUN";
        log.info("[Config] mode={}  scheduler.enabled={}  cleanup.enabled={}  db.url={}  db.user={}",
                mode, schedulerEnabled, cleanupEnabled, dbUrl, dbUser);

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

        String makeAdminNick = parseMakeAdmin(args);
        if (makeAdminNick != null) {
            if (dbUrl == null || (!dbUrl.contains("db_use_test") && !dbUrl.contains("db_use_test_it"))) {
                log.error("[SAFETY] --make-admin refused: DB is not db_use_test/db_use_test_it: {}", dbUrl);
                System.exit(1);
            }
            try (org.hibernate.Session session = HibernateUtil.getSessionFactory().openSession()) {
                org.hibernate.Transaction tx = session.beginTransaction();
                User user = session.createSelectionQuery("FROM User u WHERE u.nick = :nick", User.class)
                        .setParameter("nick", makeAdminNick).uniqueResult();
                if (user == null) {
                    log.error("[make-admin] User '{}' not found.", makeAdminNick);
                    tx.rollback();
                    System.exit(1);
                }
                user.setRole(Role.ADMIN);
                AuditLog auditLog = new AuditLog();
                auditLog.setAction("MAKE_ADMIN");
                auditLog.setTargetType("user");
                auditLog.setTargetId(String.valueOf(user.getId()));
                auditLog.setDetails("nick=" + makeAdminNick);
                auditLog.setCreatedAt(LocalDateTime.now());
                session.persist(auditLog);
                tx.commit();
                log.info("[make-admin] '{}' is now ADMIN.", makeAdminNick);
            }
            System.exit(0);
        }

        if (processQueueCount > 0) {
            log.info("[QueueWorker] --process-queue {} — loading classifier and page loader…", processQueueCount);
            QueueWorker worker = new QueueWorker(new OllamaClassifier(), new PageFetcherLoader());
            int processed = worker.processQueue(processQueueCount);
            log.info("[QueueWorker] processed={}", processed);
            System.exit(0);
        }

        if (!compareModels.isEmpty()) {
            log.info("[compare-models] models={} limit={}", compareModels, compareLimit);
            try {
                String benchDir = System.getProperty("newsdove.benchmark.dir", "docs/benchmarks");
                new ModelComparisonRunner(compareModels, compareLimit)
                        .run(Path.of(benchDir));
            } catch (Exception e) {
                log.error("[compare-models] failed: {}", e.getMessage(), e);
                System.exit(1);
            }
            System.exit(0);
        }

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

    private static int parseProcessQueue(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--process-queue".equals(args[i])) {
                try {
                    int n = Integer.parseInt(args[i + 1]);
                    if (n > 0) return n;
                } catch (NumberFormatException ignored) {}
            }
        }
        return 0;
    }

    /** Parses {@code --compare-models m1,m2,m3} → list of model names. */
    private static List<String> parseCompareModels(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--compare-models".equals(args[i])) {
                String raw = args[i + 1].trim();
                if (!raw.isEmpty()) {
                    return Arrays.asList(raw.split(","));
                }
            }
        }
        return List.of();
    }

    /** Parses {@code --compare-limit N} → N (default 20). */
    private static int parseCompareLimit(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--compare-limit".equals(args[i])) {
                try {
                    int n = Integer.parseInt(args[i + 1]);
                    if (n > 0) return n;
                } catch (NumberFormatException ignored) {}
            }
        }
        return 0; // 0 = no limit (load all PENDING URLs)
    }

    /** Parses {@code --score-benchmark <csv>} → path string, or null if absent. */
    private static String parseScoreBenchmark(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--score-benchmark".equals(args[i])) {
                String v = args[i + 1].trim();
                if (!v.isEmpty()) return v;
            }
        }
        return null;
    }

    /** Parses {@code --gold <csv>} → path string, or null if absent. */
    private static String parseGold(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--gold".equals(args[i])) {
                String v = args[i + 1].trim();
                if (!v.isEmpty()) return v;
            }
        }
        return null;
    }

    /** Parses {@code --make-admin <nick>} → nick string, or null if absent. */
    private static String parseMakeAdmin(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--make-admin".equals(args[i])) {
                String v = args[i + 1].trim();
                if (!v.isEmpty()) return v;
            }
        }
        return null;
    }
}
