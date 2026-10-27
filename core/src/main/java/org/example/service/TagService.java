package org.example.service;

import org.example.Tag;
import org.example.dao.TagDAO;
import org.example.dao.impl.TagDAOImpl;

import java.util.List;
import java.util.Optional;

/**
 * Сервис для работы с тегами.
 */
public class TagService {

    private final TagDAO tagDAO;

    public TagService() {
        this.tagDAO = new TagDAOImpl();
    }

    public TagService(TagDAO tagDAO) {
        this.tagDAO = tagDAO;
    }

    // ═══════════════════════════════════════════════════════════
    // Создание и управление тегами
    // ═══════════════════════════════════════════════════════════

    /**
     * Создаёт новый тег.
     * Проверяет уникальность имени.
     */
    public Tag createTag(String name) {
        // Проверяем, не существует ли уже такой тег
        Optional<Tag> existing = tagDAO.findByName(name);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Тег с таким именем уже существует: " + name);
        }

        Tag tag = new Tag(name);
        return tagDAO.save(tag);
    }

    /**
     * Получает тег по имени или создаёт новый, если не существует.
     */
    public Tag getOrCreateTag(String name) {
        Optional<Tag> existing = tagDAO.findByName(name);
        if (existing.isPresent()) {
            return existing.get();
        }

        Tag tag = new Tag(name);
        return tagDAO.save(tag);
    }

    /**
     * Обновляет имя тега.
     */
    public Tag updateTagName(int tagId, String newName) {
        Tag tag = tagDAO.findById(tagId)
                .orElseThrow(() -> new IllegalArgumentException("Тег не найден: " + tagId));

        // Проверяем, не занято ли новое имя
        Optional<Tag> existing = tagDAO.findByName(newName);
        if (existing.isPresent() && existing.get().getId() != tagId) {
            throw new IllegalArgumentException("Тег с таким именем уже существует: " + newName);
        }

        tag.setName(newName);
        return tagDAO.update(tag);
    }

    // ═══════════════════════════════════════════════════════════
    // Поиск и получение данных
    // ═══════════════════════════════════════════════════════════

    public Optional<Tag> getTagById(int id) {
        return tagDAO.findById(id);
    }

    public Optional<Tag> getTagByName(String name) {
        return tagDAO.findByName(name);
    }

    public List<Tag> getAllTags() {
        return tagDAO.findAll();
    }

    public List<Tag> getTagsWithUsers() {
        return tagDAO.findTagsWithUsers();
    }

    public List<Tag> getTagsWithArticles() {
        return tagDAO.findTagsWithArticles();
    }

    public List<Tag> getMostPopularTags(int limit) {
        return tagDAO.findMostPopularTags(limit);
    }

    public long getTotalTags() {
        return tagDAO.count();
    }

    // ═══════════════════════════════════════════════════════════
    // Удаление
    // ═══════════════════════════════════════════════════════════

    /**
     * Удаляет тег.
     * ВНИМАНИЕ: каскадно удалятся все связанные UserTagPercent и ArticleTagPercent!
     */
    public void deleteTag(int tagId) {
        tagDAO.deleteById(tagId);
    }

    /**
     * Удаляет тег по имени.
     */
    public void deleteTagByName(String name) {
        Optional<Tag> tag = tagDAO.findByName(name);
        if (tag.isPresent()) {
            tagDAO.delete(tag.get());
        } else {
            throw new IllegalArgumentException("Тег не найден: " + name);
        }
    }
}