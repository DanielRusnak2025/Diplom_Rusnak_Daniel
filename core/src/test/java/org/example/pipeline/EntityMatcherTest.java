package org.example.pipeline;

import org.example.pipeline.EntityMatcher.AliasData;
import org.example.pipeline.EntityMatcher.EntityData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EntityMatcherTest {

    private static EntityData country(int id, String region, String... enAliases) {
        List<AliasData> al = new java.util.ArrayList<>();
        for (String p : enAliases) al.add(new AliasData("en", p, false));
        return new EntityData(id, region, al);
    }

    private static EntityData withAcronym(int id, String region, String acronym) {
        return new EntityData(id, region, List.of(new AliasData(null, acronym, true)));
    }

    private static EntityData ruAlias(int id, String region, String... ruAliases) {
        List<AliasData> al = new java.util.ArrayList<>();
        for (String p : ruAliases) al.add(new AliasData("ru", p, false));
        return new EntityData(id, region, al);
    }

    // 1. Simple alias match — Germany appears in title
    @Test
    void germanInTitle_returnsWesternEurope() {
        EntityMatcher m = new EntityMatcher(List.of(
                country(1, "western_europe", "Germany", "German*")));
        assertEquals("western_europe", m.matchRegion("Germany debates its future", "", "en"));
    }

    // 2. Longest match wins — Papua New Guinea should NOT trigger Guinea
    @Test
    void longestMatchWins_papuaNewGuineaBeatsGuinea() {
        EntityMatcher m = new EntityMatcher(List.of(
                country(1, "east_southeast_asia", "Papua New Guinea"),
                country(2, "africa",              "Guinea")));
        // only Papua New Guinea should vote
        assertEquals("east_southeast_asia",
                m.matchRegion("", "Papua New Guinea signed the treaty", "en"));
    }

    // 3a. Acronym "US" (uppercase) matches correctly
    @Test
    void acronymUS_matchesUppercase() {
        EntityMatcher m = new EntityMatcher(List.of(
                withAcronym(1, "north_america", "US")));
        assertEquals("north_america", m.matchRegion("US announces new policy", "", "en"));
    }

    // 3b. Lowercase "us" does NOT match acronym "US"
    @Test
    void acronymUS_doesNotMatchLowercase() {
        EntityMatcher m = new EntityMatcher(List.of(
                withAcronym(1, "north_america", "US")));
        // "us" is not the acronym "US"
        assertEquals("global", m.matchRegion("us is also important", "", "en"));
    }

    // 4. Acronyms skipped in all-caps segment
    @Test
    void allCapsSegment_skipAcronyms() {
        EntityMatcher m = new EntityMatcher(List.of(
                withAcronym(1, "north_america", "WHO")));
        assertEquals("global", m.matchRegion("WHO WON THE MATCH", "", "en"));
    }

    // 5. Tie → global
    @Test
    void tieRegions_returnsGlobal() {
        EntityMatcher m = new EntityMatcher(List.of(
                country(1, "western_europe", "Germany"),
                country(2, "latin_america",  "Brazil")));
        // 1 text vote each → tie
        assertEquals("global",
                m.matchRegion("", "Germany and Brazil signed a deal", "en"));
    }

    // 6. Global-region entity (e.g. NATO) does not vote → global
    @Test
    void globalRegionEntity_doesNotVote() {
        EntityMatcher m = new EntityMatcher(List.of(
                new EntityData(1, "global", List.of(
                        new AliasData(null, "NATO", true)))));
        assertEquals("global", m.matchRegion("NATO summit in Brussels", "", "en"));
    }

    // 7. Russian text about Chisinau → eastern_europe
    @Test
    void russianTextChisinau_returnsEasternEurope() {
        // Chisinau is a place in Moldova; resolved region = eastern_europe
        EntityMatcher m = new EntityMatcher(List.of(
                ruAlias(1, "eastern_europe", "Кишинёв*")));
        assertEquals("eastern_europe",
                m.matchRegion("Переговоры в Кишинёве завершились", "", "ru"));
    }
}
