package org.example.email;

import java.io.InputStream;
import java.util.Properties;

/**
 * Загружает настройки email из файла email.properties
 */
public class EmailConfig {

    private static final Properties props = new Properties();

    static {
        try (InputStream is = EmailConfig.class
                .getClassLoader()
                .getResourceAsStream("email.properties")) {

            if (is == null) {
                throw new RuntimeException("Файл email.properties не найден в resources!");
            }
            props.load(is);

        } catch (Exception e) {
            throw new RuntimeException("Ошибка загрузки email.properties: " + e.getMessage());
        }
    }

    public static String getEmail() {
        return props.getProperty("email.address");
    }

    public static String getAppPassword() {
        return props.getProperty("email.app.password");
    }

    public static int getSchedulerIntervalMinutes() {
        try { return Integer.parseInt(props.getProperty("scheduler.interval.minutes", "30")); }
        catch (NumberFormatException e) { return 30; }
    }

    public static int getParallelRequests() {
        try { return Integer.parseInt(props.getProperty("scheduler.parallel.requests", "5")); }
        catch (NumberFormatException e) { return 5; }
    }

    public static int getCleanupDays() {
        try { return Integer.parseInt(props.getProperty("scheduler.cleanup.days", "60")); }
        catch (NumberFormatException e) { return 60; }
    }

    public static int getCleanupMaxReads() {
        try { return Integer.parseInt(props.getProperty("scheduler.cleanup.max.reads", "0")); }
        catch (NumberFormatException e) { return 0; }
    }

    public static boolean isSchedulerEnabled() {
        return Boolean.parseBoolean(props.getProperty("scheduler.enabled", "true"));
    }

    public static boolean isCleanupEnabled() {
        return Boolean.parseBoolean(props.getProperty("scheduler.cleanup.enabled", "false"));
    }

    public static int getMaxAgeDays() {
        try { return Integer.parseInt(props.getProperty("scheduler.max.age.days", "7")); }
        catch (NumberFormatException e) { return 7; }
    }

    public static int getMaxEmailsPerCycle() {
        try { return Integer.parseInt(props.getProperty("scheduler.max.emails.per.cycle", "0")); }
        catch (NumberFormatException e) { return 0; }
    }
}