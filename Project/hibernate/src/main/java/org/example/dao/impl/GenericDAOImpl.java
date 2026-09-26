package org.example.dao.impl;

import org.example.dao.GenericDAO;
import org.example.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;

import java.util.List;
import java.util.Optional;

/**
 * Базовая реализация GenericDAO для работы с Hibernate.
 * Абстрактный класс — нельзя создать напрямую, только через наследников.
 *
 * @param <T>  Тип сущности (User, Tag, Article и т.д.)
 * @param <ID> Тип первичного ключа (String, Integer, составной ключ)
 */
public abstract class GenericDAOImpl<T, ID> implements GenericDAO<T, ID> {

    // Класс сущности (например, User.class, Tag.class)
    private final Class<T> entityClass;

    /**
     * Конструктор принимает класс сущности.
     * Например: new UserDAOImpl() передаст User.class
     */
    protected GenericDAOImpl(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    // ═══════════════════════════════════════════════════════════
    // CREATE (сохранение)
    // ═══════════════════════════════════════════════════════════

    @Override
    public T save(T entity) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.persist(entity); // сохраняем объект
            transaction.commit();    // подтверждаем транзакцию
            return entity;
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback(); // откатываем изменения при ошибке
            }
            e.printStackTrace();
            throw new RuntimeException("Ошибка при сохранении " + entityClass.getSimpleName(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // UPDATE (обновление)
    // ═══════════════════════════════════════════════════════════

    @Override
    public T update(T entity) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            T merged = session.merge(entity); // обновляем объект
            transaction.commit();
            return merged;
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            e.printStackTrace();
            throw new RuntimeException("Ошибка при обновлении " + entityClass.getSimpleName(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // DELETE (удаление объекта)
    // ═══════════════════════════════════════════════════════════

    @Override
    public void delete(T entity) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.remove(entity); // удаляем объект
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            e.printStackTrace();
            throw new RuntimeException("Ошибка при удалении " + entityClass.getSimpleName(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // DELETE BY ID (удаление по ID)
    // ═══════════════════════════════════════════════════════════

    @Override
    public void deleteById(ID id) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();

            // Находим объект по ID
            T entity = session.find(entityClass, id);
            if (entity != null) {
                session.remove(entity); // удаляем, если нашли
            }

            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) {
                transaction.rollback();
            }
            e.printStackTrace();
            throw new RuntimeException("Ошибка при удалении по ID " + entityClass.getSimpleName(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // FIND BY ID (поиск по ID)
    // ═══════════════════════════════════════════════════════════

    @Override
    public Optional<T> findById(ID id) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            T entity = session.find(entityClass, id);
            return Optional.ofNullable(entity); // возвращаем Optional (может быть null)
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    // ═══════════════════════════════════════════════════════════
    // FIND ALL (получить все записи)
    // ═══════════════════════════════════════════════════════════

    @Override
    public List<T> findAll() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // HQL запрос: "FROM User" (не SQL!)
            String hql = "FROM " + entityClass.getSimpleName();
            Query<T> query = session.createQuery(hql, entityClass);
            return query.getResultList();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Ошибка при получении всех " + entityClass.getSimpleName(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // EXISTS BY ID (проверка существования)
    // ═══════════════════════════════════════════════════════════

    @Override
    public boolean existsById(ID id) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            T entity = session.find(entityClass, id);
            return entity != null;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════
    // COUNT (подсчёт записей)
    // ═══════════════════════════════════════════════════════════

    @Override
    public long count() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // HQL запрос для подсчёта
            String hql = "SELECT COUNT(e) FROM " + entityClass.getSimpleName() + " e";
            Query<Long> query = session.createQuery(hql, Long.class);
            return query.getSingleResult();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
}