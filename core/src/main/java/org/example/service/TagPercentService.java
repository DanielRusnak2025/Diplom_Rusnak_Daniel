package org.example.service;

import org.example.*;
import org.example.dao.UserTagPercentDAO;
import org.example.dao.ArticleTagPercentDAO;
import org.example.dao.impl.UserTagPercentDAOImpl;
import org.example.util.HibernateUtil;
import org.example.dao.impl.ArticleTagPercentDAOImpl;
import org.hibernate.Session;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Сервис для работы с процентами интересов пользователей к тегам
 * и релевантностью статей к тегам.
 */
public class TagPercentService {

    private final UserTagPercentDAO userTagPercentDAO;
    private final ArticleTagPercentDAO articleTagPercentDAO;

    public TagPercentService() {
        this.userTagPercentDAO = new UserTagPercentDAOImpl();
        this.articleTagPercentDAO = new ArticleTagPercentDAOImpl();
    }

    public TagPercentService(UserTagPercentDAO userTagPercentDAO,
                             ArticleTagPercentDAO articleTagPercentDAO) {
        this.userTagPercentDAO = userTagPercentDAO;
        this.articleTagPercentDAO = articleTagPercentDAO;
    }

    // ═══════════════════════════════════════════════════════════
    // UserTagPercent (интересы пользователя)
    // ═══════════════════════════════════════════════════════════

    /**
     * Устанавливает процент интереса пользователя к тегу.
     * Если связь уже существует — обновляет, иначе создаёт новую.
     */
    public UserTagPercent setUserTagPercent(User user, Tag tag, int percent) {
        if (percent < -100 || percent > 100) {
            throw new IllegalArgumentException("Процент должен быть от -100 до 100");
        }

        Optional<UserTagPercent> existing = userTagPercentDAO.findByUserAndTag(user, tag);

        if (existing.isPresent()) {
            // Обновляем существующую связь
            UserTagPercent utp = existing.get();
            utp.setPercent(percent);
            return userTagPercentDAO.update(utp);
        } else {
            // Создаём новую связь
            UserTagPercent utp = new UserTagPercent(user, tag, percent);
            return userTagPercentDAO.save(utp);
        }
    }

    /**
     * Получает все теги пользователя, дедуплицированные по имени тега.
     */
    public List<UserTagPercent> getUserTags(User user) {
        List<UserTagPercent> all = userTagPercentDAO.findByUser(user);
        Map<String, UserTagPercent> deduped = new LinkedHashMap<>();
        for (UserTagPercent utp : all) {
            if (utp.getTag() == null) continue;
            String key = utp.getTag().getName().toLowerCase().trim();
            if (!deduped.containsKey(key)) {
                deduped.put(key, utp);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    /**
     * Получает топ-N тегов для пользователя.
     */
    public List<UserTagPercent> getTopUserTags(User user, int limit) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                            "SELECT utp FROM UserTagPercent utp " +
                                    "JOIN FETCH utp.tag " +
                                    "WHERE utp.user = :user " +
                                    "ORDER BY utp.percent DESC",
                            UserTagPercent.class
                    )
                    .setParameter("user", user)
                    .setMaxResults(limit)
                    .getResultList();
        }
    }

    /**
     * Получает всех пользователей с определённым тегом.
     */
    public List<UserTagPercent> getUsersWithTag(Tag tag) {
        return userTagPercentDAO.findByTag(tag);
    }

    /**
     * Удаляет связь пользователь-тег.
     */
    public void removeUserTag(User user, Tag tag) {
        UserTagId id = new UserTagId(user.getId(), tag.getId());
        userTagPercentDAO.deleteById(id);
    }

    // ═══════════════════════════════════════════════════════════
    // ArticleTagPercent (релевантность статьи к тегу)
    // ═══════════════════════════════════════════════════════════

    /**
     * Устанавливает процент релевантности статьи к тегу.
     * Если связь уже существует — обновляет, иначе создаёт новую.
     */
    public ArticleTagPercent setArticleTagPercent(Article article, Tag tag, int percent) {
        if (percent < 1 || percent > 100) {
            throw new IllegalArgumentException("Процент должен быть от 1 до 100");
        }

        Optional<ArticleTagPercent> existing = articleTagPercentDAO.findByArticleAndTag(article, tag);

        if (existing.isPresent()) {
            // Обновляем существующую связь
            ArticleTagPercent atp = existing.get();
            atp.setPercent(percent);
            return articleTagPercentDAO.update(atp);
        } else {
            // Создаём новую связь
            ArticleTagPercent atp = new ArticleTagPercent(article, tag, percent);
            return articleTagPercentDAO.save(atp);
        }
    }

    /**
     * Получает теги статьи, дедуплицированные по имени тега (берём с наибольшим процентом).
     * Старые данные в БД могут содержать несколько записей для одного имени тега.
     */
    public List<ArticleTagPercent> getArticleTags(Article article) {
        List<ArticleTagPercent> all = articleTagPercentDAO.findByArticle(article);
        Map<String, ArticleTagPercent> deduped = new LinkedHashMap<>();
        for (ArticleTagPercent atp : all) {
            if (atp.getTag() == null) continue;
            String key = atp.getTag().getName().toLowerCase().trim();
            ArticleTagPercent existing = deduped.get(key);
            if (existing == null || atp.getPercent() > existing.getPercent()) {
                deduped.put(key, atp);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    /**
     * Получает топ-N тегов для статьи (самые релевантные).
     */
    public List<ArticleTagPercent> getTopArticleTags(Article article, int limit) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                            "SELECT atp FROM ArticleTagPercent atp " +
                                    "JOIN FETCH atp.tag " +
                                    "WHERE atp.article = :article " +
                                    "ORDER BY atp.percent DESC",
                            ArticleTagPercent.class
                    )
                    .setParameter("article", article)
                    .setMaxResults(limit)
                    .getResultList();
        }
    }

    /**
     * Получает все статьи с определённым тегом.
     */
    public List<ArticleTagPercent> getArticlesWithTag(Tag tag) {
        return articleTagPercentDAO.findByTag(tag);
    }

    /**
     * Получает статьи с высокой релевантностью к тегу.
     */
    public List<ArticleTagPercent> getHighlyRelevantArticles(Tag tag, int minPercent) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                            "SELECT atp FROM ArticleTagPercent atp " +
                                    "JOIN FETCH atp.article " +
                                    "WHERE atp.tag = :tag AND atp.percent >= :minPercent " +
                                    "ORDER BY atp.percent DESC",
                            ArticleTagPercent.class
                    )
                    .setParameter("tag", tag)
                    .setParameter("minPercent", minPercent)
                    .getResultList();
        }
    }

    /**
     * Находит статьи, которые имеют все указанные теги.
     */
    public List<Article> findArticlesByMultipleTags(List<Tag> tags) {
        return articleTagPercentDAO.findArticlesByTags(tags);
    }

    /**
     * Удаляет связь статья-тег.
     */
    public void removeArticleTag(Article article, Tag tag) {
        ArticleTagId id = new ArticleTagId(article.getId(), tag.getId());
        articleTagPercentDAO.deleteById(id);
    }

    // ═══════════════════════════════════════════════════════════
    // Персонализация и скоринг
    // ═══════════════════════════════════════════════════════════

    /**
     * Загружает интересы пользователя по тегам в виде Map<tagId, percent>.
     * Эффективно: один запрос к БД для всех тегов.
     */
    public Map<Integer, Integer> getUserTagMap(User user) {
        List<UserTagPercent> userTags = userTagPercentDAO.findByUser(user);
        Map<Integer, Integer> map = new HashMap<>();
        for (UserTagPercent utp : userTags) {
            if (utp.getTag() != null) {
                map.put(utp.getTag().getId(), utp.getPercent());
            }
        }
        return map;
    }

    /**
     * Вычисляет персональный скор статьи для пользователя.
     * Формула: Score = (Σ (U[tag] * A[tag])) / (Σ |A[tag]|)
     * Диапазон: от -100 до +100.
     *
     * @param article    статья (с предзагруженными articleTagPercents)
     * @param userTagMap интересы пользователя: tagId → percent (-100..100)
     * @return целое число от -100 до +100
     */
    public int calculateArticleScore(Article article, Map<Integer, Integer> userTagMap) {
        Collection<ArticleTagPercent> articleTags = article.getArticleTagPercents();
        if (articleTags == null || articleTags.isEmpty()) return 0;

        long numerator   = 0;
        long denominator = 0;
        for (ArticleTagPercent atp : articleTags) {
            if (atp.getTag() == null) continue;
            int aPercent = atp.getPercent();                          // 1..100
            int uPercent = userTagMap.getOrDefault(atp.getTag().getId(), 0); // -100..100
            numerator   += (long) uPercent * aPercent;
            denominator += Math.abs(aPercent);                        // всегда > 0
        }

        if (denominator == 0) return 0;
        return (int) Math.round((double) numerator / denominator);
    }

    // ═══════════════════════════════════════════════════════════
    // Комплексные операции
    // ═══════════════════════════════════════════════════════════

    /**
     * Корректирует UserTagPercent пользователя по каждому тегу статьи.
     *
     * direction = +1 (Like):     delta = round(articleTagPercent / 2.0), min 1
     * direction = -1 (Dislike):  delta = -round(articleTagPercent / 2.0), max -1
     * direction = -2 (Complaint): то же, что -1
     * direction =  0 (Neutral):  delta = 0, веса не меняются
     *
     * Все операции в одной транзакции. Значения сглаживаются у границ ±100.
     */
    public void adjustUserTagWeights(Article article, User user, int direction) {
        List<ArticleTagPercent> articleTags = articleTagPercentDAO.findByArticle(article);
        if (articleTags == null || articleTags.isEmpty()) return;

        // Жалоба меняет веса как дизлайк
        int effective = (direction == -2) ? -1 : direction;

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            session.beginTransaction();
            try {
                for (ArticleTagPercent atp : articleTags) {
                    Tag tag = atp.getTag();
                    if (tag == null || atp.getPercent() <= 0) continue;

                    int delta = computeDelta(effective, atp.getPercent());
                    if (delta == 0) continue; // нейтральная оценка — не трогать запись

                    UserTagId utpId = new UserTagId(user.getId(), tag.getId());
                    UserTagPercent utp = session.find(UserTagPercent.class, utpId);
                    int current = (utp != null) ? utp.getPercent() : 0;

                    int newValue = smoothClamp(current, delta);

                    if (utp != null) {
                        utp.setPercent(newValue); // managed entity — dirty checking сохранит сам
                    } else {
                        session.persist(new UserTagPercent(user, tag, newValue));
                    }
                }
                session.getTransaction().commit();
            } catch (Exception e) {
                session.getTransaction().rollback();
                throw e;
            }
        }
    }

    /**
     * Вычисляет величину изменения веса по типу реакции и весу тега в статье.
     * direction: +1=Like, -1=Dislike, 0=Neutral
     * Возвращает 0 при нейтральной оценке — вызывающий код должен пропустить обновление.
     */
    static int computeDelta(int direction, int articlePercent) {
        if (direction == 1) {
            return Math.max(1, (int) Math.round(articlePercent / 2.0));
        } else if (direction == -1) {
            return Math.min(-1, -(int) Math.round(articlePercent / 2.0));
        }
        return 0; // neutral: no weight change
    }

    /**
     * Применяет delta к текущему весу со сглаживанием у границ ±100.
     *
     * Формула (при движении к ближайшей границе, |raw| > ZONE_START):
     *   s = max(|current|, ZONE_START)
     *   f = s + round((|raw| − s) × ZONE_COMPRESSION × (100 − s) / 20)
     * Коэффициент (100 − s)/20 убывает с глубиной зоны: s=80 → 1.0; s=90 → 0.5; s=95 → 0.25.
     * Гарантированный минимальный шаг ±1: если f ≤ |current| → f = |current| + 1.
     * При движении от границы или вне зоны — полный шаг с ограничением ±100.
     *
     * Примеры (delta=+25): 50→75 (вне зоны), 70→86, 85→93, 95→98, 98→99.
     * Из current=90: +5→91, +10→92, +15→93, +20→94 (каждый delta даёт свой результат).
     * Dislike от +85: 85−25=60 (полный шаг, движение от границы).
     * Результат всегда в [−100, 100].
     */
    static int smoothClamp(int current, int delta) {
        if (delta == 0) return current;
        int raw = current + delta;

        if (delta > 0 && raw > ZONE_START) {
            int s = Math.max(current, ZONE_START);
            double cf = ZONE_COMPRESSION * (100.0 - s) / 20.0;
            int smoothed = s + (int) Math.round((raw - s) * cf);
            if (smoothed <= current && current < 100) smoothed = current + 1;
            return Math.min(100, smoothed);
        }
        if (delta < 0 && raw < -ZONE_START) {
            int absCur = Math.abs(current);
            int absRaw = Math.abs(raw);
            int s = Math.max(absCur, ZONE_START);
            double cf = ZONE_COMPRESSION * (100.0 - s) / 20.0;
            int absSmoothed = s + (int) Math.round((absRaw - s) * cf);
            if (absSmoothed <= absCur && absCur < 100) absSmoothed = absCur + 1;
            return -Math.min(100, absSmoothed);
        }
        return Math.max(-100, Math.min(100, raw));
    }

    private static final int    ZONE_START       = 80;
    private static final double ZONE_COMPRESSION = 0.4;

    /**
     * Рекомендует статьи для пользователя на основе его интересов.
     * (Упрощённая версия - можно доработать алгоритм)
     */
    public List<Article> recommendArticlesForUser(User user, int limit) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<UserTagPercent> userTopTags = session.createQuery(
                            "SELECT utp FROM UserTagPercent utp " +
                                    "JOIN FETCH utp.tag " +
                                    "WHERE utp.user = :user " +
                                    "ORDER BY utp.percent DESC",
                            UserTagPercent.class
                    )
                    .setParameter("user", user)
                    .setMaxResults(3)
                    .getResultList();

            if (userTopTags.isEmpty()) return List.of();

            Tag topTag = userTopTags.get(0).getTag();

            List<ArticleTagPercent> relevantArticles = session.createQuery(
                            "SELECT atp FROM ArticleTagPercent atp " +
                                    "JOIN FETCH atp.article " +
                                    "WHERE atp.tag = :tag AND atp.percent > :minPercent " +
                                    "ORDER BY atp.percent DESC",
                            ArticleTagPercent.class
                    )
                    .setParameter("tag", topTag)
                    .setParameter("minPercent", 50)
                    .getResultList();

            return relevantArticles.stream()
                    .map(ArticleTagPercent::getArticle)
                    .limit(limit)
                    .toList();
        }
    }
}