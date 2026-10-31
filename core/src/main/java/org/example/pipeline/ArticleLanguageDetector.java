package org.example.pipeline;

import com.github.pemistahl.lingua.api.IsoCode639_1;
import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Detects the language of a text and returns the ISO 639-1 code if it is in the
 * allowed set, or {@code null} if the language is unrecognized or not allowed.
 *
 * In QueueWorker, a null return becomes REJECTED(LANGUAGE_NOT_SUPPORTED).
 */
public class ArticleLanguageDetector {

    private final LanguageDetector detector;
    private final Set<String> allowedCodes;

    public ArticleLanguageDetector(Set<String> allowedCodes) {
        this.allowedCodes = allowedCodes.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toUnmodifiableSet());

        IsoCode639_1[] isos = allowedCodes.stream()
                .map(String::toUpperCase)
                .map(code -> {
                    try { return IsoCode639_1.valueOf(code); }
                    catch (IllegalArgumentException e) { return null; }
                })
                .filter(c -> c != null)
                .toArray(IsoCode639_1[]::new);

        this.detector = (isos.length > 0)
                ? LanguageDetectorBuilder.fromIsoCodes639_1(isos).build()
                : LanguageDetectorBuilder.fromAllLanguages().build();
    }

    /**
     * @return ISO 639-1 code (lowercase) if detected and allowed, {@code null} otherwise.
     */
    public String detect(String text) {
        if (text == null || text.isBlank()) return null;
        Language detected = detector.detectLanguageOf(text);
        if (detected == Language.UNKNOWN) return null;
        String code = detected.getIsoCode639_1().toString().toLowerCase();
        return allowedCodes.contains(code) ? code : null;
    }
}
