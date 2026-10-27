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
        try {
            return Integer.parseInt(props.getProperty("ollama.max_text_length", "2000"));
        } catch (NumberFormatException e) {
            return 2000;
        }
    }
}
