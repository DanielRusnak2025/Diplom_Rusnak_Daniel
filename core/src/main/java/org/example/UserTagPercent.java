package org.example;

import jakarta.persistence.*;

@Entity
@Table(name = "user_tag_percent")
public class UserTagPercent {

    @EmbeddedId
    private UserTagId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("tagId")
    @JoinColumn(name = "tag_id", insertable = false, updatable = false)
    private Tag tag;

    @Column(nullable = false)
    private int percent;

    // ───────────────────────────────────────────────
    // Конструкторы
    // ───────────────────────────────────────────────

    public UserTagPercent() {
    }

    public UserTagPercent(User user, Tag tag, int percent) {
        this.user = user;
        this.tag = tag;
        this.percent = percent;
        this.id = new UserTagId(user.getId(), tag.getId());
    }

    // ───────────────────────────────────────────────
    // Геттеры и сеттеры
    // ───────────────────────────────────────────────

    public UserTagId getId() {
        return id;
    }

    public void setId(UserTagId id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
        // Поддержка bidirectional связи (обновляем ключ)
        if (user != null && this.id != null) {
            this.id.setUserId(user.getId());
        }
    }

    public Tag getTag() {
        return tag;
    }

    public void setTag(Tag tag) {
        this.tag = tag;
        // Поддержка bidirectional связи
        if (tag != null && this.id != null) {
            this.id.setTagId(tag.getId());
        }
    }

    public int getPercent() {
        return percent;
    }

    public void setPercent(int percent) {
        this.percent = percent;
    }

    // ───────────────────────────────────────────────
    // Удобные методы (рекомендуются)
    // ───────────────────────────────────────────────

    /**
     * Устанавливает процент с проверкой допустимого диапазона
     */
    public void setPercentSafe(int percent) {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("Percent must be between 0 and 100");
        }
        this.percent = percent;
    }

    /**
     * Удобный способ изменить процент и сразу сохранить связь
     */
    public static UserTagPercent of(User user, Tag tag, int percent) {
        return new UserTagPercent(user, tag, percent);
    }
}