package org.example.pipeline;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ArticleLanguageDetectorTest {

    private static ArticleLanguageDetector detector;

    @BeforeAll
    static void setup() {
        detector = new ArticleLanguageDetector(Set.of("en", "ru", "de", "ro"));
    }

    @Test
    void detectsEnglish() {
        String text = "The quick brown fox jumps over the lazy dog in the forest.";
        assertEquals("en", detector.detect(text));
    }

    @Test
    void detectsRussian() {
        String text = "Быстрая коричневая лиса прыгает через ленивую собаку в лесу.";
        assertEquals("ru", detector.detect(text));
    }

    @Test
    void nullForUnknownLanguage() {
        // Chinese text — not in the allowed set
        String text = "快速的棕色狐狸跳过了懒狗在森林里";
        assertNull(detector.detect(text), "Chinese should not be detected as an allowed language");
    }

    @Test
    void nullForBlankText() {
        assertNull(detector.detect("   "));
    }

    @Test
    void nullForNullText() {
        assertNull(detector.detect(null));
    }
}
