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

    // Wider detection pool to avoid forcing ambiguous languages onto allowed ones
    // (e.g. Ukrainian classified as Russian when only {en,ru,de,ro} are in the detector).
    private static final IsoCode639_1[] DETECTION_POOL = {
        IsoCode639_1.EN, IsoCode639_1.RU, IsoCode639_1.UK, IsoCode639_1.DE,
        IsoCode639_1.RO, IsoCode639_1.FR, IsoCode639_1.ES, IsoCode639_1.IT,
        IsoCode639_1.PT, IsoCode639_1.PL, IsoCode639_1.NL, IsoCode639_1.TR,
        IsoCode639_1.CS, IsoCode639_1.BG, IsoCode639_1.SR
    };

    private final LanguageDetector detector;
    private final Set<String> allowedCodes;

    public ArticleLanguageDetector(Set<String> allowedCodes) {
        this.allowedCodes = allowedCodes.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toUnmodifiableSet());

        this.detector = LanguageDetectorBuilder
                .fromIsoCodes639_1(DETECTION_POOL)
                .withMinimumRelativeDistance(0.1)
                .build();
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
