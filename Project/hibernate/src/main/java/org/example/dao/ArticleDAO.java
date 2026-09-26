package org.example.dao;

import org.example.Article;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * DAO для работы с сущностью Article.
 */
public interface ArticleDAO extends GenericDAO<Article, Integer> {

    /**
     * Находит статью по ссылке.
     *
     * @param link URL статьи
     * @return Optional с найденной статьёй или пустой Optional
     */
    Optional<Article> findByLink(String link);

    /**
     * Находит статьи по языку.
     *
     * @param language язык статьи
     * @return список статей
     */
    List<Article> findByLanguage(String language);

    /**
     * Находит статьи по региону.
     *
     * @param region регион
     * @return список статей
     */
    List<Article> findByRegion(String region);

    /**
     * Находит статьи по издателю.
     *
     * @param publisher название издателя
     * @return список статей
     */
    List<Article> findByPublisher(String publisher);

    /**
     * Находит статьи, опубликованные после указанной даты.
     *
     * @param date дата
     * @return список статей
     */
    List<Article> findByPublicationDateAfter(Date date);

    /**
     * Находит самые читаемые статьи.
     *
     * @param limit максимальное количество результатов
     * @return список статей
     */
    List<Article> findMostRead(int limit);

    /**
     * Находит статьи по языку и региону.
     *
     * @param language язык
     * @param region регион
     * @return список статей
     */
    List<Article> findByLanguageAndRegion(String language, String region);

    List<Article> findRecentWithTags(int limit);


    List<Article> searchByKeywordAndDate(String keyword, Date publicationDate);

    Optional<Article> findByTitle(String title);

    /** Ищет статьи, заголовок которых начинается с указанного префикса (case-insensitive). */
    List<Article> findByTitlePrefix(String prefix);

    /** Загружает статьи по набору ID (с тегами через JOIN FETCH). */
    List<Article> findByIds(Collection<Integer> ids);

    /**
     * Полнотекстовый поиск по заголовку и издателю (case-insensitive LIKE).
     */
    List<Article> findByKeyword(String keyword);

    /**
     * Находит статьи старше указанного количества дней с readCount <= maxReadCount.
     * Используется для автоматической очистки устаревшего непрочитанного контента.
     *
     * @param daysOld      минимальный возраст статьи в днях
     * @param maxReadCount максимальное количество прочтений (включительно)
     * @return список статей-кандидатов на удаление
     */
    List<Article> findOldUnread(int daysOld, int maxReadCount);

    /**
     * Считает количество статей с id > afterId.
     * Используется для определения, появились ли новые статьи с момента последней загрузки.
     */
    int countNewerThan(int afterId);

    /**
     * Возвращает список ID статей с id > afterId.
     * Используется для последующей проверки процента лайков по каждой новой статье.
     */
    List<Integer> findIdsNewerThan(int afterId);
}



