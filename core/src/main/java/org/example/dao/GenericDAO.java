package org.example.dao;

import java.util.List;
import java.util.Optional;

/**
 * Базовый интерфейс для всех DAO.
 *
 * @param <T>  Тип сущности (например, User, Tag, Article)
 * @param <ID> Тип первичного ключа (например, String для User, Integer для Tag)
 */
public interface GenericDAO<T, ID> {

    /**
     * Сохраняет новую сущность в базу данных.
     *
     * @param entity объект для сохранения
     * @return сохранённый объект (с присвоенным ID, если он был auto-generated)
     */
    T save(T entity);

    /**
     * Обновляет существующую сущность в базе данных.
     *
     * @param entity объект для обновления
     * @return обновлённый объект
     */
    T update(T entity);

    /**
     * Удаляет сущность из базы данных.
     *
     * @param entity объект для удаления
     */
    void delete(T entity);

    /**
     * Удаляет сущность по её ID.
     *
     * @param id идентификатор сущности
     */
    void deleteById(ID id);

    /**
     * Находит сущность по ID.
     *
     * @param id идентификатор
     * @return Optional с найденной сущностью или пустой Optional
     */
    Optional<T> findById(ID id);

    /**
     * Получает все сущности данного типа.
     *
     * @return список всех сущностей
     */
    List<T> findAll();

    /**
     * Проверяет, существует ли сущность с данным ID.
     *
     * @param id идентификатор
     * @return true, если существует
     */
    boolean existsById(ID id);

    /**
     * Возвращает количество всех сущностей данного типа.
     *
     * @return количество записей
     */
    long count();
}