package org.example.util;

import org.example.*;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

public class HibernateUtil {

    private static final SessionFactory sessionFactory;

    static {
        // Override hbm2ddl: Flyway owns the schema; Hibernate only validates.
        // Entities registered here (not in hibernate.cfg.xml) to avoid
        // DuplicateMappingException if cfg.xml has leftover <mapping> entries.
        sessionFactory = new Configuration()
                .configure()
                .addAnnotatedClass(User.class)
                .addAnnotatedClass(Article.class)
                .addAnnotatedClass(Tag.class)
                .addAnnotatedClass(ArticleTagPercent.class)
                .addAnnotatedClass(UserTagPercent.class)
                .addAnnotatedClass(Report.class)
                .addAnnotatedClass(PasswordHistory.class)
                .setProperty("hibernate.hbm2ddl.auto", "validate")
                .buildSessionFactory();
    }

    public static SessionFactory getSessionFactory() {
        return sessionFactory;
    }

    public static void shutdown() {
        if (sessionFactory != null) {
            sessionFactory.close();
        }
    }
}