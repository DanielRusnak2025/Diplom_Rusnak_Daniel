package org.example.pipeline;

import org.example.util.UrlNormalizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a benchmark CSV with filled gold_is_article / gold_category columns
 * and computes per-model accuracy. Appends "## Accuracy vs gold" to the
 * matching .md report.
 */
public class BenchmarkScorer {

    record ModelAccuracy(String model,
                         int artCorrect, int artTotal,
                         int catCorrect, int catTotal) {
        double artPct() { return artTotal == 0 ? 0 : artCorrect * 100.0 / artTotal; }
        double catPct() { return catTotal == 0 ? 0 : catCorrect * 100.0 / catTotal; }
    }

    /**
     * Reads {@code csvPath}, computes accuracy vs gold, and appends the
     * "## Accuracy vs gold" section to the .md file in the same directory.
     */
    public static void appendToReport(Path csvPath) throws IOException {
        appendToReport(csvPath, null);
    }

    /**
     * Reads {@code csvPath} and an optional external {@code goldCsvPath}
     * (format: {@code url,gold_is_article,gold_category}), merges gold labels by
     * normalized URL, and appends "## Accuracy vs gold" to the matching .md.
     * When {@code goldCsvPath} is null, gold labels must be filled in the benchmark CSV itself.
     */
    public static void appendToReport(Path csvPath, Path goldCsvPath) throws IOException {
        String section = goldCsvPath != null
                ? computeSection(csvPath, loadGoldLabels(goldCsvPath))
                : computeSection(csvPath);
        String stem = csvPath.getFileName().toString();
        if (!stem.endsWith(".csv")) throw new IOException("Expected .csv path: " + csvPath);
        Path mdPath = csvPath.getParent().resolve(stem.substring(0, stem.length() - 4) + ".md");
        if (!Files.exists(mdPath)) throw new IOException("Matching .md not found: " + mdPath);

        String existing = Files.readString(mdPath, StandardCharsets.UTF_8);
        int goldIdx = existing.indexOf("\n## Accuracy vs gold");
        if (goldIdx >= 0) existing = existing.substring(0, goldIdx);
        Files.writeString(mdPath, existing + section, StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    /** Builds the "## Accuracy vs gold" markdown section from the CSV. */
    public static String computeSection(Path csvPath) throws IOException {
        return computeSection(csvPath, Map.of());
    }

    /**
     * Builds the section, optionally supplementing inline gold with an external gold map.
     * {@code externalGold} maps normalized URL → {gold_is_article, gold_category} (both strings).
     * External gold takes precedence over inline gold columns.
     */
    static String computeSection(Path csvPath, Map<String, String[]> externalGold) throws IOException {
        List<String[]> rows = parseCsv(csvPath);
        if (rows.isEmpty()) throw new IOException("CSV is empty: " + csvPath);
        String[] header = rows.get(0);

        int urlIdx     = indexOf(header, "url");
        int goldArtIdx = indexOf(header, "gold_is_article");
        int goldCatIdx = indexOf(header, "gold_category");
        if (goldArtIdx < 0 || goldCatIdx < 0)
            throw new IOException("CSV missing gold_is_article or gold_category column");

        List<String> modelNames = new ArrayList<>();
        for (String col : header) {
            if (col.endsWith("_is_article") && !col.equals("gold_is_article"))
                modelNames.add(col.substring(0, col.length() - "_is_article".length()));
        }
        if (modelNames.isEmpty()) throw new IOException("No model columns found in CSV");

        // Effective gold: merge inline + external (external wins)
        boolean hasExternalGold = !externalGold.isEmpty();
        long goldFilled = 0;
        for (String[] row : rows.subList(1, rows.size())) {
            String eff = effectiveGoldArt(row, urlIdx, goldArtIdx, externalGold);
            if (eff != null && !eff.isEmpty()) goldFilled++;
        }
        if (goldFilled == 0) return "\n## Accuracy vs gold\n\n*(no gold labels filled yet)*\n";

        // Rows with non-empty skip_reason were pre-filtered: pipeline decided not-article.
        int skipReasonIdx = indexOf(header, "skip_reason"); // -1 for older CSVs without this column

        List<ModelAccuracy> accuracies = new ArrayList<>();
        for (String model : modelNames) {
            int artIdx = indexOf(header, model + "_is_article");
            int catIdx = indexOf(header, model + "_category");
            int artCorr = 0, artTotal = 0, catCorr = 0, catTotal = 0;
            for (String[] row : rows.subList(1, rows.size())) {
                String goldArt = effectiveGoldArt(row, urlIdx, goldArtIdx, externalGold);
                if (goldArt == null || goldArt.isEmpty()) continue;
                String goldCat = effectiveGoldCat(row, urlIdx, goldCatIdx, externalGold);
                artTotal++;
                String modelArt = cell(row, artIdx).toLowerCase();
                // Pre-filtered by pipeline → pipeline decision = not-article
                if (skipReasonIdx >= 0 && !cell(row, skipReasonIdx).isEmpty()) {
                    modelArt = "false";
                }
                if (goldArt.equals(modelArt)) artCorr++;

                // Category is only comparable when both gold and model agree it's an article.
                if ("true".equals(goldArt) && "true".equals(modelArt)
                        && goldCat != null && !goldCat.isEmpty()) {
                    catTotal++;
                    if (cell(row, catIdx).equals(goldCat)) catCorr++;
                }
            }
            accuracies.add(new ModelAccuracy(model, artCorr, artTotal, catCorr, catTotal));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n## Accuracy vs gold\n\n");
        if (hasExternalGold) sb.append("*(gold from external file)*\n\n");
        sb.append("Gold labels filled: ").append(goldFilled).append(" / ")
          .append(rows.size() - 1).append(" rows\n\n");
        sb.append("| Model | is_article accuracy | main_category accuracy |\n");
        sb.append("|---|---|---|\n");
        for (ModelAccuracy a : accuracies) {
            String catCell = a.catTotal() == 0 ? "n/a"
                    : String.format(Locale.US, "%.1f%% (%d/%d)", a.catPct(), a.catCorrect(), a.catTotal());
            sb.append("| ").append(a.model())
              .append(" | ").append(String.format(Locale.US, "%.1f%% (%d/%d)",
                      a.artPct(), a.artCorrect(), a.artTotal()))
              .append(" | ").append(catCell)
              .append(" |\n");
        }
        return sb.toString();
    }

    /**
     * Loads gold labels from an external CSV (format: url,gold_is_article,gold_category).
     * Returns map: normalized URL → {gold_is_article, gold_category}.
     */
    static Map<String, String[]> loadGoldLabels(Path goldCsvPath) throws IOException {
        List<String[]> rows = parseCsv(goldCsvPath);
        if (rows.isEmpty()) throw new IOException("Gold CSV is empty: " + goldCsvPath);
        String[] header = rows.get(0);
        int urlIdx    = indexOf(header, "url");
        int artIdx    = indexOf(header, "gold_is_article");
        int catIdx    = indexOf(header, "gold_category");
        if (urlIdx < 0 || artIdx < 0 || catIdx < 0)
            throw new IOException("Gold CSV must have url, gold_is_article, gold_category columns");
        Map<String, String[]> result = new HashMap<>();
        for (String[] row : rows.subList(1, rows.size())) {
            String rawUrl = cell(row, urlIdx);
            if (rawUrl.isEmpty()) continue;
            String norm = tryNormalizeUrl(rawUrl);
            result.put(norm, new String[]{cell(row, artIdx), cell(row, catIdx)});
        }
        return result;
    }

    private static String effectiveGoldArt(String[] row, int urlIdx, int goldArtIdx,
                                            Map<String, String[]> external) {
        if (!external.isEmpty() && urlIdx >= 0) {
            String norm = tryNormalizeUrl(cell(row, urlIdx));
            String[] g = external.get(norm);
            if (g != null && !g[0].isEmpty()) return g[0].trim().toLowerCase();
        }
        return (goldArtIdx >= 0 && goldArtIdx < row.length) ? row[goldArtIdx].trim().toLowerCase() : null;
    }

    private static String effectiveGoldCat(String[] row, int urlIdx, int goldCatIdx,
                                            Map<String, String[]> external) {
        if (!external.isEmpty() && urlIdx >= 0) {
            String norm = tryNormalizeUrl(cell(row, urlIdx));
            String[] g = external.get(norm);
            if (g != null && !g[1].isEmpty()) return g[1].trim();
        }
        return (goldCatIdx >= 0 && goldCatIdx < row.length) ? row[goldCatIdx].trim() : null;
    }

    private static String tryNormalizeUrl(String url) {
        try { return UrlNormalizer.normalize(url); } catch (IllegalArgumentException e) { return url; }
    }

    // ── CSV parser (RFC 4180) ─────────────────────────────────────────────────

    static List<String[]> parseCsv(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        List<String[]> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            if (!line.isEmpty()) result.add(parseCsvLine(line));
        }
        return result;
    }

    static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuote) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"'); i++;
                    } else {
                        inQuote = false;
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"') { inQuote = true; }
                else if (c == ',') { fields.add(cur.toString()); cur.setLength(0); }
                else cur.append(c);
            }
        }
        fields.add(cur.toString());
        return fields.toArray(new String[0]);
    }

    private static int indexOf(String[] arr, String val) {
        for (int i = 0; i < arr.length; i++) if (val.equals(arr[i])) return i;
        return -1;
    }

    private static String cell(String[] row, int idx) {
        return (idx >= 0 && idx < row.length) ? row[idx].trim() : "";
    }
}
