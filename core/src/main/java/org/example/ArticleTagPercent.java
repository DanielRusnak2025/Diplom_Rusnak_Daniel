package org.example;

import jakarta.persistence.*;

@Entity
@Table(name = "article_tag_percent")
public class ArticleTagPercent {

    @EmbeddedId
    private ArticleTagId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("articleId")
    @JoinColumn(name = "article_id", insertable = false, updatable = false)
    private Article article;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("tagId")
    @JoinColumn(name = "tag_id", insertable = false, updatable = false)
    private Tag tag;

    @Column(nullable = false)
    private int percent;

    // ───────────────────────────────────────────────
    // Конструкторы
    // ───────────────────────────────────────────────

    public ArticleTagPercent() {
    }

    public ArticleTagPercent(Article article, Tag tag, int percent) {
        this.article = article;
        this.tag = tag;
        this.percent = percent;
        this.id = new ArticleTagId(article.getId(), tag.getId());
    }

    // ───────────────────────────────────────────────
    // Геттеры и сеттеры
    // ───────────────────────────────────────────────

    public ArticleTagId getId() {
        return id;
    }

    public void setId(ArticleTagId id) {
        this.id = id;
    }

    public Article getArticle() {
        return article;
    }

    public void setArticle(Article article) {
        this.article = article;
        // Поддержка bidirectional связи (опционально, но полезно)
        if (article != null && this.id != null) {
            this.id.setArticleId(article.getId());
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
    // Удобные методы (опционально, но очень рекомендуются)
    // ───────────────────────────────────────────────

    public void updatePercent(int newPercent) {
        if (newPercent < 0 || newPercent > 100) {
            throw new IllegalArgumentException("Percent must be between 0 and 100");
        }
        this.percent = newPercent;
    }
}