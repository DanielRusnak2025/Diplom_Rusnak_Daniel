package org.example.dao;

import org.example.pipeline.EntityMatcher;
import org.example.test.TestDb;
import org.example.util.HibernateUtil;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(TestDb.class)
class EntityDictionaryTest {

    @Test
    void loadFromDatabase_returns366Entities() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            EntityMatcher m = EntityMatcher.loadFromDatabase(s);
            List<EntityMatcher.EntityData> entities = m.allEntities();
            assertEquals(366, entities.size(), "expected 366 entities from V11_2 seed");
        }
    }

    @Test
    void germanArticle_BundeskanzlerInBerlin_returnsWesternEurope() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            EntityMatcher m = EntityMatcher.loadFromDatabase(s);
            String region = m.matchRegion("Bundeskanzler in Berlin trifft EU-Vertreter", "", "de");
            assertEquals("western_europe", region);
        }
    }

    @Test
    void russianArticle_meetingInChisinau_returnsEasternEurope() {
        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            EntityMatcher m = EntityMatcher.loadFromDatabase(s);
            String region = m.matchRegion("Встреча в Кишинёве завершилась без результата", "", "ru");
            assertEquals("eastern_europe", region);
        }
    }
}
