package org.example.pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
        String section = computeSection(csvPath);
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
        List<String[]> rows = parseCsv(csvPath);
        if (rows.isEmpty()) throw new IOException("CSV is empty: " + csvPath);
        String[] header = rows.get(0);

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

        long goldFilled = rows.stream().skip(1)
                .filter(r -> r.length > goldArtIdx && !r[goldArtIdx].isEmpty())
                .count();
        if (goldFilled == 0) return "\n## Accuracy vs gold\n\n*(no gold labels filled yet)*\n";

        List<ModelAccuracy> accuracies = new ArrayList<>();
        for (String model : modelNames) {
            int artIdx = indexOf(header, model + "_is_article");
            int catIdx = indexOf(header, model + "_category");
            int artCorr = 0, artTotal = 0, catCorr = 0, catTotal = 0;
            for (String[] row : rows.subList(1, rows.size())) {
                if (row.length <= goldArtIdx || row[goldArtIdx].isEmpty()) continue;
                artTotal++;
                String goldArt  = row[goldArtIdx].trim().toLowerCase();
                String modelArt = cell(row, artIdx).toLowerCase();
                if (goldArt.equals(modelArt)) artCorr++;

                // Category is only comparable when both gold and model agree it's an article.
                if ("true".equals(goldArt) && "true".equals(modelArt)
                        && row.length > goldCatIdx && !row[goldCatIdx].isEmpty()) {
                    catTotal++;
                    if (cell(row, catIdx).equals(row[goldCatIdx].trim())) catCorr++;
                }
            }
            accuracies.add(new ModelAccuracy(model, artCorr, artTotal, catCorr, catTotal));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n## Accuracy vs gold\n\n");
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
