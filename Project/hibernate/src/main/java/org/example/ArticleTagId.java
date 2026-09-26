package org.example;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ArticleTagId implements Serializable {

    private int articleId;
    private int tagId;

    public ArticleTagId() {
    }

    public ArticleTagId(int articleId, int tagId) {
        this.articleId = articleId;
        this.tagId = tagId;
    }

    // ───────────────────────────────────────────────
    // Геттеры и сеттеры
    // ───────────────────────────────────────────────

    public int getArticleId() {
        return articleId;
    }

    public void setArticleId(int articleId) {
        this.articleId = articleId;
    }

    public int getTagId() {
        return tagId;
    }

    public void setTagId(int tagId) {
        this.tagId = tagId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ArticleTagId)) return false;
        ArticleTagId that = (ArticleTagId) o;
        return articleId == that.articleId && tagId == that.tagId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(articleId, tagId);
    }
}