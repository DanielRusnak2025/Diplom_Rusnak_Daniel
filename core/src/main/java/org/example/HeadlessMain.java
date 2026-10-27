package org.example;

import org.example.email.EmailConfig;
import org.example.scheduler.EmailScheduler;
import org.example.service.ArticleService;

public class HeadlessMain {

    public static void main(String[] args) {
        boolean schedulerEnabled = EmailConfig.isSchedulerEnabled();
        boolean cleanupEnabled   = EmailConfig.isCleanupEnabled();
        org.hibernate.cfg.Configuration hCfg = new org.hibernate.cfg.Configuration().configure();
        String dbUrl  = hCfg.getProperty("hibernate.connection.url");
        String dbUser = hCfg.getProperty("hibernate.connection.username");
        System.out.println("[Config] scheduler.enabled=" + schedulerEnabled
                + "  scheduler.cleanup.enabled=" + cleanupEnabled
                + "  db.url=" + dbUrl + "  db.user=" + dbUser);

        // Temporary guard until Stage 4 (server startup will enforce this via env/profile)
        if (dbUrl == null || !dbUrl.contains("db_use_test")) {
            System.err.println("ERROR: db.url does not contain 'db_use_test'. Fix hibernate.cfg.xml.");
            System.exit(1);
        }

        // READ-ONLY DB check
        ArticleService articleService = new ArticleService();
        long count = articleService.getTotalArticles();
        System.out.println("[DB] articles in db_use_test: " + count);

        if (!schedulerEnabled) {
            System.out.println("[Config] scheduler.enabled=false — exiting.");
            return;
        }

        EmailScheduler emailScheduler = new EmailScheduler();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown: stopping EmailScheduler...");
            emailScheduler.stop();
        }));
        emailScheduler.start();
    }
}
