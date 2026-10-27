package org.example;

import org.example.email.EmailConfig;
import org.example.scheduler.EmailScheduler;
import org.example.service.ArticleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HeadlessMain {

    private static final Logger log = LoggerFactory.getLogger(HeadlessMain.class);

    public static void main(String[] args) {
        boolean schedulerEnabled = EmailConfig.isSchedulerEnabled();
        boolean cleanupEnabled   = EmailConfig.isCleanupEnabled();
        org.hibernate.cfg.Configuration hCfg = new org.hibernate.cfg.Configuration().configure();
        String dbUrl  = hCfg.getProperty("hibernate.connection.url");
        String dbUser = hCfg.getProperty("hibernate.connection.username");
        log.info("[Config] scheduler.enabled={}  scheduler.cleanup.enabled={}  db.url={}  db.user={}",
                schedulerEnabled, cleanupEnabled, dbUrl, dbUser);

        // Temporary guard until Stage 4 (server startup will enforce this via env/profile)
        if (dbUrl == null || !dbUrl.contains("db_use_test")) {
            log.error("ERROR: db.url does not contain 'db_use_test'. Fix hibernate.cfg.xml.");
            System.exit(1);
        }

        // READ-ONLY DB check
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
