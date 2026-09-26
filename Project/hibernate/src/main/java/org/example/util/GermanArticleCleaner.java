package org.example.util;

import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;
import org.example.Article;
import org.example.service.ArticleService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * One-time utility: finds and deletes all articles whose title is in German.
 * Detection uses two signals:
 *   1. articleLanguage field contains a German variant ("german", "de", "deu", etc.)
 *   2. Lingua offline detector identifies the title as Language.GERMAN
 *
 * Run from IntelliJ: right-click -> Run 'GermanArticleCleaner.main()'
 * Or via Maven: mvn exec:java -Dexec.mainClass="org.example.util.GermanArticleCleaner"
 */
public class GermanArticleCleaner {

    private static final Set<String> GERMAN_LANG_VARIANTS = Set.of(
            "german", "ger", "deu", "de", "deutsch"
    );

    private static final LanguageDetector DETECTOR = LanguageDetectorBuilder
            .fromLanguages(
                    Language.ENGLISH, Language.GERMAN, Language.RUSSIAN,
                    Language.FRENCH,  Language.SPANISH, Language.ITALIAN,
                    Language.PORTUGUESE, Language.ROMANIAN, Language.UKRAINIAN,
                    Language.ARABIC,  Language.HEBREW, Language.CHINESE, Language.JAPANESE
            )
            .build();

    public static void main(String[] args) {
        System.out.println("=== GermanArticleCleaner starting ===");

        ArticleService articleService = new ArticleService();
        List<Article> all = articleService.getAllArticles();
        System.out.println("Total articles in DB: " + all.size());

        List<Article> toDelete = new ArrayList<>();

        for (Article a : all) {
            String reason = detectGermanReason(a);
            if (reason != null) {
                toDelete.add(a);
                System.out.println("[GERMAN] id=" + a.getId()
                        + " | lang=" + a.getArticleLanguage()
                        + " | reason=" + reason
                        + " | title=" + a.getTitle());
            }
        }

        System.out.println("\nFound " + toDelete.size() + " German article(s) to delete.");

        if (toDelete.isEmpty()) {
            System.out.println("Nothing to delete.");
            HibernateUtil.shutdown();
            return;
        }

        int deleted = 0;
        for (Article a : toDelete) {
            boolean ok = articleService.deleteArticle(a.getId());
            if (ok) {
                deleted++;
                System.out.println("  Deleted id=" + a.getId() + " title=" + a.getTitle());
            } else {
                System.err.println("  FAILED to delete id=" + a.getId());
            }
        }

        System.out.println("\n=== Done. Deleted " + deleted + " / " + toDelete.size() + " articles. ===");
        HibernateUtil.shutdown();
    }

    /**
     * Returns a non-null reason string if the article is considered German, null otherwise.
     */
    private static String detectGermanReason(Article a) {
        String lang = a.getArticleLanguage();
        if (lang != null && GERMAN_LANG_VARIANTS.contains(lang.trim().toLowerCase())) {
            return "language-field=" + lang;
        }

        String title = a.getTitle();
        if (title != null && !title.isBlank()) {
            Language detected = DETECTOR.detectLanguageOf(title);
            if (detected == Language.GERMAN) {
                return "lingua-detected=GERMAN";
            }
        }

        return null;
    }
}
