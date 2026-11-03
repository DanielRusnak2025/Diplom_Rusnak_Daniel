package org.example.pipeline;

import org.example.KnownEntity;
import org.example.EntityKind;
import org.hibernate.Session;

import java.util.*;

/**
 * Matches geographic entities / organisations in article text to determine a region.
 *
 * The dictionary is loaded once from DB via the constructor.  Aliases are filtered
 * per article language; acronyms are applied for every language but are skipped
 * when the searched segment is all-caps (e.g. a headline like "WHO WON").
 *
 * Voting: title mention ×2, text mention ×1; each entity contributes at most once per
 * segment even if it appears multiple times.  "global" region does not vote.
 * Tie or zero votes → "global".
 */
public final class EntityMatcher {

    public record AliasData(String language, String pattern, boolean acronym) {}

    /** regionKey is the effective voting region: for PLACE it is already the country's region. */
    public record EntityData(int id, String regionKey, List<AliasData> aliases) {}

    private final List<EntityData> entities;

    public EntityMatcher(List<EntityData> entities) {
        this.entities = List.copyOf(entities);
    }

    /**
     * Loads the full entity dictionary from the DB in one query and returns a ready matcher.
     * For PLACE entities the region is resolved via their country row.
     */
    public static EntityMatcher loadFromDatabase(Session session) {
        List<KnownEntity> rows = session.createQuery(
                "SELECT DISTINCT e FROM KnownEntity e " +
                "LEFT JOIN FETCH e.aliases " +
                "LEFT JOIN FETCH e.country",
                KnownEntity.class).list();

        List<EntityData> data = rows.stream().map(e -> {
            String regionKey = (e.getKind() == EntityKind.PLACE)
                    ? e.getCountry().getRegionKey()
                    : e.getRegionKey();
            List<AliasData> al = e.getAliases().stream()
                    .map(a -> new AliasData(a.getLanguage(), a.getPattern(), a.isAcronym()))
                    .toList();
            return new EntityData(e.getId(), regionKey, al);
        }).toList();

        return new EntityMatcher(data);
    }

    // ── public API ────────────────────────────────────────────────────────────

    public List<EntityData> allEntities() { return entities; }

    public String matchRegion(String title, String text, String language) {
        Map<String, Integer> votes = new HashMap<>();

        Set<Integer> inTitle = matchedEntities(title, language);
        Set<Integer> inText  = matchedEntities(text,  language);

        for (int i = 0; i < entities.size(); i++) {
            EntityData e = entities.get(i);
            if ("global".equals(e.regionKey())) continue;
            int v = 0;
            if (inTitle.contains(i)) v += 2;
            if (inText.contains(i))  v += 1;
            if (v > 0) votes.merge(e.regionKey(), v, Integer::sum);
        }

        if (votes.isEmpty()) return "global";
        int max = Collections.max(votes.values());
        List<String> winners = votes.entrySet().stream()
                .filter(en -> en.getValue() == max)
                .map(Map.Entry::getKey)
                .toList();
        return winners.size() == 1 ? winners.get(0) : "global";
    }

    // ── per-segment matching ──────────────────────────────────────────────────

    private Set<Integer> matchedEntities(String segment, String language) {
        if (segment == null || segment.isBlank()) return Set.of();

        String[] normToks = KeywordMatcher.tokenize(KeywordMatcher.normalize(segment));
        String[] rawToks  = tokenizeRaw(segment);
        boolean  allCaps  = isAllCaps(segment);

        // alias pass (normalized tokens, language-filtered)
        List<int[]> aliasCandidates = new ArrayList<>();
        for (int i = 0; i < entities.size(); i++) {
            for (AliasData a : entities.get(i).aliases()) {
                if (a.acronym()) continue;
                if (!language.equalsIgnoreCase(a.language())) continue;
                addNormMatches(aliasCandidates, normToks, a.pattern(), i);
            }
        }

        // acronym pass (raw tokens, case-sensitive)
        List<int[]> acronymCandidates = new ArrayList<>();
        if (!allCaps) {
            for (int i = 0; i < entities.size(); i++) {
                for (AliasData a : entities.get(i).aliases()) {
                    if (!a.acronym()) continue;
                    addRawMatches(acronymCandidates, rawToks, a.pattern(), i);
                }
            }
        }

        Set<Integer> matched = new HashSet<>(resolveOverlaps(aliasCandidates));
        matched.addAll(resolveOverlaps(acronymCandidates));
        return matched;
    }

    // ── match finders ─────────────────────────────────────────────────────────

    private static void addNormMatches(List<int[]> out,
                                       String[] normTokens,
                                       String pattern,
                                       int entityIdx) {
        String[] pw = KeywordMatcher.normalize(pattern).split("\\s+");
        if (pw.length == 0 || (pw.length == 1 && pw[0].isEmpty())) return;
        outer:
        for (int s = 0; s <= normTokens.length - pw.length; s++) {
            for (int k = 0; k < pw.length; k++) {
                if (!wordMatches(normTokens[s + k], pw[k])) continue outer;
            }
            out.add(new int[]{s, s + pw.length, entityIdx});
        }
    }

    private static void addRawMatches(List<int[]> out,
                                      String[] rawTokens,
                                      String acronym,
                                      int entityIdx) {
        for (int s = 0; s < rawTokens.length; s++) {
            if (rawTokens[s].equals(acronym)) {
                out.add(new int[]{s, s + 1, entityIdx});
            }
        }
    }

    // ── overlap resolution ────────────────────────────────────────────────────

    /**
     * Greedy longest-match-wins over a list of [start, end, entityIdx] candidates
     * all referring to the same token array.  Returns matched entity indices.
     */
    private static Set<Integer> resolveOverlaps(List<int[]> candidates) {
        if (candidates.isEmpty()) return Set.of();
        // sort: by start ascending, then by length descending (longest first)
        candidates.sort(Comparator.comparingInt((int[] c) -> c[0])
                                  .thenComparingInt(c -> -(c[1] - c[0])));
        Set<Integer> matched = new HashSet<>();
        int lastEnd = -1;
        for (int[] c : candidates) {
            if (c[0] >= lastEnd) {
                matched.add(c[2]);
                lastEnd = c[1];
            }
        }
        return matched;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static boolean wordMatches(String token, String patternWord) {
        if (patternWord.startsWith("*") && patternWord.endsWith("*") && patternWord.length() > 2) {
            return token.contains(patternWord.substring(1, patternWord.length() - 1));
        }
        if (patternWord.endsWith("*")) {
            String prefix = patternWord.substring(0, patternWord.length() - 1);
            return !prefix.isEmpty() && token.startsWith(prefix);
        }
        return token.equals(patternWord);
    }

    private static String[] tokenizeRaw(String text) {
        return Arrays.stream(text.split("[^\\p{L}\\p{N}]+"))
                     .filter(s -> !s.isEmpty())
                     .toArray(String[]::new);
    }

    private static boolean isAllCaps(String text) {
        long letters = text.chars().filter(Character::isLetter).count();
        if (letters == 0) return false;
        long upper   = text.chars().filter(Character::isLetter)
                           .filter(Character::isUpperCase).count();
        return upper == letters;
    }
}
