package org.example;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class UserTagId implements Serializable {

    private String nick;
    private int tagId;

    public UserTagId() {
    }

    public UserTagId(String nick, int tagId) {
        this.nick = nick;
        this.tagId = tagId;
    }

    // ───────────────────────────────────────────────
    // Геттеры и сеттеры
    // ───────────────────────────────────────────────

    public String getNick() {
        return nick;
    }

    public void setNick(String nick) {
        this.nick = nick;
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
        return tagId == that.tagId && nick.equals(that.nick);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nick, tagId);
    }
}