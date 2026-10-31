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

    @Test
    void detectsRomanian() {
        String text = "Vulpea maronie rapidă sare peste câinele leneș din pădure.";
        assertEquals("ro", detector.detect(text));
    }

    @Test
    void nullForUkrainian() {
        // Ukrainian must NOT collapse into Russian just because uk is not in allowed set.
        String text = "Швидка коричнева лисиця стрибнула через ледачого пса у лісі.";
        assertNull(detector.detect(text), "Ukrainian text should return null, not 'ru'");
    }

    @Test
    void nullForFrench() {
        String text = "Le renard brun rapide saute par-dessus le chien paresseux dans la forêt.";
        assertNull(detector.detect(text), "French text should return null (not in allowed set)");
    }
}
