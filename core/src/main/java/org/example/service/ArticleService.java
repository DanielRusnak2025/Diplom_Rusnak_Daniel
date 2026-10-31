package org.example.service;

import org.example.Article;
import org.example.dao.ArticleDAO;
import org.example.dao.impl.ArticleDAOImpl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Сервис для работы со статьями.
 */
public class ArticleService {

    private final ArticleDAO articleDAO;

    public ArticleService() {
        this.articleDAO = new ArticleDAOImpl();
    }

    public ArticleService(ArticleDAO articleDAO) {
        this.articleDAO = articleDAO;
    }

    // ─────────────────────────────────────────────────────────────
    //  НОВЫЙ МЕТОД — ПОИСК ПО КЛЮЧЕВОМУ СЛОВУ + ДАТА (для нашего окна)
    // ─────────────────────────────────────────────────────────────
    /**
     * Поиск статей по частичному совпадению в названии или содержимом + опциональная дата
     *
     * @param keyword часть названия или содержимого (может быть null/пустым)
     * @param date    точная дата публикации (может быть null)
     * @return список подходящих статей
     */
    public List<Article> searchArticles(String keyword, LocalDate date) {
        String trimmedKeyword = (keyword == null || keyword.trim().isEmpty()) ? null : keyword.trim();

        Date sqlDate = null;
        if (date != null) {
            sqlDate = Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
        }

        return articleDAO.searchByKeywordAndDate(trimmedKeyword, sqlDate);
    }

    // ─────────────────────────────────────────────────────────────
    //  НОВЫЙ МЕТОД — ОБНОВЛЕНИЕ СТАТЬИ (для кнопки Save)
    // ─────────────────────────────────────────────────────────────
    /**
     * Обновляет статью и возвращает true/false в зависимости от успеха
     */
    public boolean updateArticle(Article article) {
        if (article == null || article.getId() == 0) {
            return false;
        }
        try {
            articleDAO.update(article);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Остальные методы
    // ─────────────────────────────────────────────────────────────

    public Article createArticle(String title, String link, String language,
                                 String region, String publisher) {
        Optional<Article> existing = articleDAO.findByLink(link);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Статья с таким URL уже существует: " + link);
        }

        Article article = new Article();
        article.setTitle(title);
        article.setLink(link);
        article.setArticleLanguage(language);
        article.setArticleRegion(region);
        article.setPublisher(publisher);
        article.setPublicationDate(new Date());

        return articleDAO.save(article);
    }

    public Article updateArticle(int articleId, String title, String language,
                                 String region, String publisher) {
        Article article = articleDAO.findById(articleId)
                .orElseThrow(() -> new IllegalArgumentException("Статья не найдена: " + articleId));

        if (title != null) article.setTitle(title);
        if (language != null) article.setArticleLanguage(language);
        if (region != null) article.setArticleRegion(region);
        if (publisher != null) article.setPublisher(publisher);

        return articleDAO.update(article);
    }

    public List<Article> getRecentArticlesWithTags(int limit) {
        return articleDAO.findRecentWithTags(limit);
    }

    public Optional<Article> getArticleById(int id) {
        return articleDAO.findById(id);
    }

    public Optional<Article> getArticleByLink(String link) {
        return articleDAO.findByLink(link);
    }

    public List<Article> getAllArticles() {
        return articleDAO.findAll();
    }

    public List<Article> getArticlesByLanguage(String language) {
        return articleDAO.findByLanguage(language);
    }

    public List<Article> getArticlesByRegion(String region) {
        return articleDAO.findByRegion(region);
    }

    public List<Article> getArticlesByPublisher(String publisher) {
        return articleDAO.findByPublisher(publisher);
    }

    public List<Article> getArticlesByLanguageAndRegion(String language, String region) {
        return articleDAO.findByLanguageAndRegion(language, region);
    }

    public List<Article> getRecentArticles(Date afterDate) {
        return articleDAO.findByPublicationDateAfter(afterDate);
    }

    public long getTotalArticles() {
        return articleDAO.count();
    }

    /**
     * Возвращает количество статей, добавленных в БД после указанного ID.
     * Используется для polling-уведомлений о новых статьях в MainScreen.
     */
    public int countNewArticles(int afterId) {
        return articleDAO.countNewerThan(afterId);
    }

    /**
     * Возвращает список ID статей, добавленных после указанного ID.
     * Используется для проверки процента лайков у новых статей.
     */
    public List<Integer> getArticleIdsNewerThan(int afterId) {
        return articleDAO.findIdsNewerThan(afterId);
    }

    /**
     * Удаляет статьи старше daysOld дней с readCount <= maxReadCount.
     * Каскадно удаляет связанные ArticleTagPercent и Report.
     *
     * @return количество удалённых статей
     */
    /** Загружает статьи по набору ID с тегами (для экрана истории). */
    public List<Article> getArticlesByIds(java.util.Set<Integer> ids) {
        return articleDAO.findByIds(ids);
    }

    public int deleteOldArticles(int daysOld) {
        List<Article> candidates = articleDAO.findOldArticles(daysOld);
        int deleted = 0;
        for (Article article : candidates) {
            try {
                articleDAO.deleteById(article.getId());
                deleted++;
            } catch (Exception e) {
                System.err.println("Не удалось удалить статью id=" + article.getId() + ": " + e.getMessage());
            }
        }
        return deleted;
    }

    public boolean deleteArticle(int articleId) {
        try {
            articleDAO.deleteById(articleId);
            return true;
        } catch (Exception e) {
            // можно логировать ошибку, если хочешь
            e.printStackTrace();
            return false;
        }
    }

    public List<Article> searchArticlesByKeyword(String keyword) {
        return articleDAO.findByKeyword(keyword);
    }
}