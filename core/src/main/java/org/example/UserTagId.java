package org.example;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class UserTagId implements Serializable {

    private int userId;
    private int tagId;

    public UserTagId() {
    }

    public UserTagId(int userId, int tagId) {
        this.userId = userId;
        this.tagId = tagId;
    }

    // ───────────────────────────────────────────────
    // Геттеры и сеттеры
    // ───────────────────────────────────────────────

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getTagId() {
        return tagId;
    }

    public void setTagId(int tagId) {
        this.tagId = tagId;
    }

    // ───────────────────────────────────────────────
    // equals + hashCode (у тебя уже отлично написано)
    // ───────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserTagId)) return false;
        UserTagId that = (UserTagId) o;
        return tagId == that.tagId && userId == that.userId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, tagId);
    }
}