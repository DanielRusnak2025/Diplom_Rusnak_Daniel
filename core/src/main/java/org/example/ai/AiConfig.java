package org.example.ai;

import java.io.InputStream;
import java.util.Properties;

/**
 * Загружает настройки AI из файла ai.properties.
 */
public class AiConfig {

    private static final Properties props = new Properties();

    static {
        try (InputStream is = AiConfig.class.getClassLoader()
                .getResourceAsStream("ai.properties")) {
            if (is != null) {
                props.load(is);
            } else {
                System.err.println("Предупреждение: ai.properties не найден — используются значения по умолчанию");
            }
        } catch (Exception e) {
            System.err.println("Предупреждение: ошибка загрузки ai.properties: " + e.getMessage());
        }
    }

    public static String getOllamaUrl() {
        return props.getProperty("ollama.url", "http://localhost:11434");
    }

    public static String getOllamaModel() {
        return props.getProperty("ollama.model", "llama3.2");
    }

    public static int getMaxTextLength() {
        return intProp("ollama.max_text_length", 2000);
    }

    public static int getFetchTimeoutSeconds() {
        return intProp("fetch.timeout.seconds", 15);
    }

    public static int getFetchMaxBytes() {
        return intProp("fetch.max.bytes", 2_000_000);
    }

    public static int getFetchMaxRedirects() {
        return intProp("fetch.max.redirects", 5);
    }

    public static int getKeywordBonusMax() {
        return intProp("keyword.bonus.max", 2);
    }

    public static java.util.Set<String> getLanguagesAllowed() {
        String raw = props.getProperty("languages.allowed", "en,ru,de,ro");
        java.util.Set<String> result = new java.util.LinkedHashSet<>();
        for (String code : raw.split(",")) {
            String trimmed = code.trim().toLowerCase();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return java.util.Collections.unmodifiableSet(result);
    }

    private static int intProp(String key, int defaultValue) {
        try {
            return Integer.parseInt(props.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
