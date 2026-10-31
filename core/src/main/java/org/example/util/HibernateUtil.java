package org.example.util;

import org.example.*;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

public class HibernateUtil {

    private static volatile SessionFactory sessionFactory;

    /**
     * Applies -Dnewsdove.db.name substitution to a raw JDBC URL.
     * Safe to call before the SessionFactory is built (no static init side effect).
     */
    public static String resolveUrl(String rawUrl) {
        String dbName = System.getProperty("newsdove.db.name");
        if (dbName == null || dbName.isBlank()) return rawUrl;
        int p = rawUrl.lastIndexOf('/');
        int q = rawUrl.indexOf('?', p);
        return rawUrl.substring(0, p + 1) + dbName + (q >= 0 ? rawUrl.substring(q) : "");
    }

    public static SessionFactory getSessionFactory() {
        SessionFactory sf = sessionFactory;
        if (sf == null) {
            synchronized (HibernateUtil.class) {
                sf = sessionFactory;
                if (sf == null) {
                    sessionFactory = sf = buildSessionFactory();
                }
            }
        }
        return sf;
    }

    private static SessionFactory buildSessionFactory() {
        Configuration cfg = new Configuration().configure();

        String rawUrl = cfg.getProperty("hibernate.connection.url");
        String newUrl = resolveUrl(rawUrl);
        if (!newUrl.equals(rawUrl)) {
            if (!newUrl.contains("db_use_test")) {
                throw new IllegalStateException(
                        "[SAFETY] DB URL must contain 'db_use_test', got: " + newUrl);
            }
            cfg.setProperty("hibernate.connection.url", newUrl);
        }

        return cfg
                .addAnnotatedClass(User.class)
                .addAnnotatedClass(Article.class)
                .addAnnotatedClass(Tag.class)
                .addAnnotatedClass(ArticleTagPercent.class)
                .addAnnotatedClass(UserTagPercent.class)
                .addAnnotatedClass(Report.class)
                .addAnnotatedClass(PasswordHistory.class)
                .addAnnotatedClass(TagKeyword.class)
                .addAnnotatedClass(Region.class)
                .addAnnotatedClass(ProcessedEmail.class)
                .addAnnotatedClass(ArticleQueue.class)
                .addAnnotatedClass(org.example.UserBlock.class)
                .addAnnotatedClass(org.example.RefreshToken.class)
                .addAnnotatedClass(org.example.AuditLog.class)
                .setProperty("hibernate.hbm2ddl.auto", "validate")
                .buildSessionFactory();
    }

    public static void shutdown() {
        SessionFactory sf = sessionFactory;
        if (sf != null) sf.close();
    }
}
