package org.example.dao;

import org.example.UserTagPercent;
import org.example.UserTagId;
import org.example.User;
import org.example.Tag;
import java.util.List;
import java.util.Optional;

/**
 * DAO для работы с сущностью UserTagPercent.
 */
public interface UserTagPercentDAO extends GenericDAO<UserTagPercent, UserTagId> {

    /**
     * Находит все теги конкретного пользователя.
     *
     * @param user пользователь
     * @return список связей пользователь-тег
     */
    List<UserTagPercent> findByUser(User user);

    /**
     * Находит всех пользователей с конкретным тегом.
     *
     * @param tag тег
     * @return список связей пользователь-тег
     */
    List<UserTagPercent> findByTag(Tag tag);

    /**
     * Находит связь по пользователю и тегу.
     *
     * @param user пользователь
     * @param tag тег
     * @return Optional с найденной связью или пустой Optional
     */
    Optional<UserTagPercent> findByUserAndTag(User user, Tag tag);

    /**
     * Находит топ-N тегов для пользователя (по проценту).
     *
     * @param user пользователь
     * @param limit количество результатов
     * @return список связей, отсортированных по проценту
     */
    List<UserTagPercent> findTopTagsForUser(User user, int limit);

    /**
     * Находит пользователей с процентом интереса к тегу выше указанного.
     *
     * @param tag тег
     * @param minPercent минимальный процент
     * @return список связей
     */
    List<UserTagPercent> findUsersWithTagPercentAbove(Tag tag, int minPercent);
}