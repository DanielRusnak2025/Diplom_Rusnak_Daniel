package org.example.dao;

import org.example.Tag;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DAO для работы с сущностью Tag.
 */
public interface TagDAO extends GenericDAO<Tag, Integer> {

    /**
     * Находит тег по имени.
     *
     * @param name название тега
     * @return Optional с найденным тегом или пустой Optional
     */
    Optional<Tag> findByName(String name);

    /**
     * Загружает теги по списку имён одним запросом.
     * Ключ карты — имя тега в нижнем регистре.
     */
    Map<String, Tag> findByNames(List<String> names);

    /**
     * Находит теги, у которых есть хотя бы одна связь с пользователями.
     *
     * @return список тегов
     */
    List<Tag> findTagsWithUsers();

    /**
     * Находит теги, у которых есть хотя бы одна связь со статьями.
     *
     * @return список тегов
     */
    List<Tag> findTagsWithArticles();

    /**
     * Находит самые популярные теги по количеству статей.
     *
     * @param limit максимальное количество результатов
     * @return список тегов
     */
    List<Tag> findMostPopularTags(int limit);
}