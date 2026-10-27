package org.example.dao;

import org.example.ArticleTagPercent;
import org.example.ArticleTagId;
import org.example.Article;
import org.example.Tag;
import java.util.List;
import java.util.Optional;

/**
 * DAO для работы с сущностью ArticleTagPercent.
 */
public interface ArticleTagPercentDAO extends GenericDAO<ArticleTagPercent, ArticleTagId> {

    /**
     * Находит все теги конкретной статьи.
     *
     * @param article статья
     * @return список связей статья-тег
     */
    List<ArticleTagPercent> findByArticle(Article article);

    /**
     * Находит все статьи с конкретным тегом.
     *
     * @param tag тег
     * @return список связей статья-тег
     */
    List<ArticleTagPercent> findByTag(Tag tag);

    /**
     * Находит связь по статье и тегу.
     *
     * @param article статья
     * @param tag тег
     * @return Optional с найденной связью или пустой Optional
     */
    Optional<ArticleTagPercent> findByArticleAndTag(Article article, Tag tag);

    /**
     * Находит топ-N тегов для статьи (по проценту релевантности).
     *
     * @param article статья
     * @param limit количество результатов
     * @return список связей, отсортированных по проценту
     */
    List<ArticleTagPercent> findTopTagsForArticle(Article article, int limit);

    /**
     * Находит статьи с процентом релевантности к тегу выше указанного.
     *
     * @param tag тег
     * @param minPercent минимальный процент
     * @return список связей
     */
    List<ArticleTagPercent> findArticlesWithTagPercentAbove(Tag tag, int minPercent);

    /**
     * Находит статьи по нескольким тегам (пересечение).
     *
     * @param tags список тегов
     * @return список статей, которые имеют все указанные теги
     */
    List<Article> findArticlesByTags(List<Tag> tags);
}