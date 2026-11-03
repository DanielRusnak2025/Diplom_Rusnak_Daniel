package org.example.pipeline;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkScorerTest {

    private static final String HEADER =
            "url,domain,title,lang,gold_is_article,gold_category," +
            "m1_is_article,m1_category,m1_subtopic,m1_latency_ms,m1_think_fallback,m1_tokens_per_second\n";

    private Path writeCsv(String content) throws IOException {
        Path p = Files.createTempFile("scorer-test-", ".csv");
        Files.writeString(p, content, StandardCharsets.UTF_8);
        p.toFile().deleteOnExit();
        return p;
    }

    @Test
    void perfectAccuracy() throws IOException {
        String csv = HEADER
                + "https://a.com,a.com,T1,en,true,Technology,true,Technology,General,1000,false,10.0\n"
                + "https://b.com,b.com,T2,en,false,,false,,,900,false,\n";
        String section = BenchmarkScorer.computeSection(writeCsv(csv));
        assertTrue(section.contains("100.0% (2/2)"), "is_article accuracy must be 100% (2/2): " + section);
        assertTrue(section.contains("100.0% (1/1)"), "cat accuracy must be 100% (1/1): " + section);
    }

    @Test
    void partialCategoryAccuracy() throws IOException {
        String csv = HEADER
                + "https://a.com,a.com,T1,en,true,Technology,true,Technology,General,1000,false,10.0\n"
                + "https://b.com,b.com,T2,en,true,Politics,true,Technology,General,900,false,10.0\n";
        String section = BenchmarkScorer.computeSection(writeCsv(csv));
        assertTrue(section.contains("100.0% (2/2)"), "is_article must be 100%: " + section);
        assertTrue(section.contains("50.0% (1/2)"), "cat must be 50% (1/2): " + section);
    }

    @Test
    void isArticleMismatch() throws IOException {
        String csv = HEADER
                + "https://a.com,a.com,T1,en,true,Technology,false,,,1000,false,\n"
                + "https://b.com,b.com,T2,en,false,,false,,,900,false,\n";
        String section = BenchmarkScorer.computeSection(writeCsv(csv));
        // row1: gold=true, model=false → mismatch. row2: gold=false, model=false → match
        assertTrue(section.contains("50.0% (1/2)"), "is_article accuracy must be 50%: " + section);
        assertTrue(section.contains("n/a"), "cat accuracy must be n/a (no gold articles matched): " + section);
    }

    @Test
    void noGoldFilled_showsPlaceholder() throws IOException {
        String csv = HEADER
                + "https://a.com,a.com,T1,en,,,true,Technology,General,1000,false,10.0\n";
        String section = BenchmarkScorer.computeSection(writeCsv(csv));
        assertTrue(section.contains("no gold labels"), "empty gold → placeholder: " + section);
    }

    @Test
    void missingGoldColumns_throws() {
        String csv = "url,domain,title,lang,m1_is_article\nhttps://a.com,a.com,T,en,true\n";
        assertThrows(IOException.class, () -> BenchmarkScorer.computeSection(writeCsv(csv)));
    }

    @Test
    void appendToReport_appendsSection() throws IOException {
        Path dir = Files.createTempDirectory("scorer-md-test-");
        Path csv = dir.resolve("model-comparison-2026-01-01.csv");
        Path md  = dir.resolve("model-comparison-2026-01-01.md");

        Files.writeString(csv, HEADER
                + "https://a.com,a.com,T,en,true,Technology,true,Technology,General,1000,false,10.0\n",
                StandardCharsets.UTF_8);
        Files.writeString(md, "# Report\n\n## Some section\n\nContent.\n", StandardCharsets.UTF_8);

        BenchmarkScorer.appendToReport(csv);

        String result = Files.readString(md, StandardCharsets.UTF_8);
        assertTrue(result.contains("## Accuracy vs gold"), "section must be appended");
        assertTrue(result.contains("## Some section"),     "existing content must be preserved");
    }

    @Test
    void appendToReport_replacesExistingGoldSection() throws IOException {
        Path dir = Files.createTempDirectory("scorer-md-replace-");
        Path csv = dir.resolve("model-comparison-2026-01-01.csv");
        Path md  = dir.resolve("model-comparison-2026-01-01.md");

        Files.writeString(csv, HEADER
                + "https://a.com,a.com,T,en,true,Technology,true,Technology,General,1000,false,10.0\n",
                StandardCharsets.UTF_8);
        Files.writeString(md, "# Report\n\n## Accuracy vs gold\n\nOLD CONTENT\n",
                StandardCharsets.UTF_8);

        BenchmarkScorer.appendToReport(csv);

        String result = Files.readString(md, StandardCharsets.UTF_8);
        assertFalse(result.contains("OLD CONTENT"), "old gold section must be replaced");
        assertTrue(result.contains("## Accuracy vs gold"), "new gold section must be present");
    }

    @Test
    void parseCsvLine_quotedFields() {
        String[] fields = BenchmarkScorer.parseCsvLine("a,\"b,c\",\"d\"\"e\"");
        assertArrayEquals(new String[]{"a", "b,c", "d\"e"}, fields);
    }

    @Test
    void parseCsvLine_emptyFields() {
        String[] fields = BenchmarkScorer.parseCsvLine("a,,c");
        assertArrayEquals(new String[]{"a", "", "c"}, fields);
    }

    // ── External gold tests ───────────────────────────────────────────────────

    @Test
    void loadGoldLabels_parsesUrlGoldArtGoldCat() throws IOException {
        Path gold = Files.createTempFile("gold-", ".csv");
        Files.writeString(gold,
                "url,gold_is_article,gold_category\n" +
                "https://a.com/article,true,Technology\n" +
                "https://b.com/news?utm_source=x,false,\n",
                java.nio.charset.StandardCharsets.UTF_8);
        gold.toFile().deleteOnExit();

        java.util.Map<String, String[]> labels = BenchmarkScorer.loadGoldLabels(gold);
        // utm_ params stripped by normalizer
        assertTrue(labels.containsKey("https://a.com/article"), "a.com entry must be present");
        assertArrayEquals(new String[]{"true", "Technology"}, labels.get("https://a.com/article"));
    }

    @Test
    void computeSection_withExternalGold_matchesByNormalizedUrl() throws IOException {
        // Benchmark CSV has utm_ in URL; gold CSV has clean URL — normalizer must match them
        String benchHeader =
                "url,domain,title,lang,gold_is_article,gold_category," +
                "m1_is_article,m1_category,m1_subtopic,m1_latency_ms,m1_think_fallback,m1_tokens_per_second\n";
        Path csv = writeCsv(benchHeader +
                "https://a.com/article?utm_source=feed,a.com,T1,en,,,true,Technology,General,1000,false,10.0\n");

        Path gold = Files.createTempFile("gold-ext-", ".csv");
        Files.writeString(gold,
                "url,gold_is_article,gold_category\nhttps://a.com/article,true,Technology\n",
                java.nio.charset.StandardCharsets.UTF_8);
        gold.toFile().deleteOnExit();

        java.util.Map<String, String[]> labels = BenchmarkScorer.loadGoldLabels(gold);
        String section = BenchmarkScorer.computeSection(csv, labels);

        assertTrue(section.contains("100.0% (1/1)"),
                "gold from external file must match via URL normalization: " + section);
    }

    @Test
    void appendToReport_withGoldFile_writesSection() throws IOException {
        Path dir = Files.createTempDirectory("scorer-gold-test-");
        Path csv = dir.resolve("model-comparison-2026-01-01.csv");
        Path md  = dir.resolve("model-comparison-2026-01-01.md");
        Path gold = dir.resolve("gold.csv");

        Files.writeString(csv, HEADER +
                "https://a.com,a.com,T,en,,,true,Technology,General,1000,false,10.0\n",
                java.nio.charset.StandardCharsets.UTF_8);
        Files.writeString(md, "# Report\n\n", java.nio.charset.StandardCharsets.UTF_8);
        Files.writeString(gold,
                "url,gold_is_article,gold_category\nhttps://a.com,true,Technology\n",
                java.nio.charset.StandardCharsets.UTF_8);

        BenchmarkScorer.appendToReport(csv, gold);

        String result = Files.readString(md, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(result.contains("## Accuracy vs gold"), "section must be appended");
        assertTrue(result.contains("gold from external file"), "must note external gold source");
        assertTrue(result.contains("100.0% (1/1)"), "accuracy must be 100%: " + result);
    }
}
