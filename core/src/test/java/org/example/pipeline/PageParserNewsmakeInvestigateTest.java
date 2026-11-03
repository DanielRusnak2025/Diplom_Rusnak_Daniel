package org.example.pipeline;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

/**
 * Read-only investigation: why newsmaker.md articles yield not-article for all 3 models.
 * Fetches ONE URL through PageFetcher + PageParser and logs text_chars + extractSource.
 *
 * Network-dependent — disabled in CI. Run manually:
 *   ./gradlew :core:test --tests "org.example.pipeline.PageParserNewsmakeInvestigateTest"
 *   -Djunit.jupiter.extensions.autodetection.enabled=true
 */
class PageParserNewsmakeInvestigateTest {

    private static final Logger log = Logger.getLogger(PageParserNewsmakeInvestigateTest.class.getName());

    // One of the 4 newsmaker.md URLs where all models returned is_article=false
    private static final String TARGET_URL =
            "https://newsmaker.md/ru/zarplaty-rastut-bystree-ekonomiki-chto-eshe-proishodit-s-ekonomikoi-moldovy";

    @Disabled("network — run manually to investigate newsmaker classification")
    @Test
    void investigate_newsmakeUrl_reportExtractSource() throws Exception {
        PageFetcher fetcher = new PageFetcher(30, 5 * 1024 * 1024, 10);
        FetchResult fr = fetcher.fetch(TARGET_URL);

        ParseResult parsed = PageParser.parse(fr.html(), fr.finalUrl());

        log.info("=== newsmaker investigation ===");
        log.info("finalUrl      : " + fr.finalUrl());
        log.info("title         : " + parsed.title());
        log.info("text_chars    : " + parsed.textChars());
        log.info("extract_source: " + parsed.extractSource());
        String preview = parsed.textForAi() == null ? "(null)"
                : parsed.textForAi().substring(0, Math.min(200, parsed.textForAi().length()));
        log.info("text (≤200)   : " + preview);
    }
}
