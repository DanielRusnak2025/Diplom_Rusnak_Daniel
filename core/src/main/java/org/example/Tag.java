package org.example;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "tag")
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(length = 100, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "kind", length = 20, nullable = false)
    private TagKind kind = TagKind.ENTITY;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Tag parent;

    @Column(length = 30)
    private String region;

    @Column(name = "tag_key", length = 150, nullable = false, unique = true)
    private String tagKey;

    @Column(length = 500)
    private String description;

    @OneToMany(mappedBy = "tag", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserTagPercent> userTagPercents = new HashSet<>();

    @OneToMany(mappedBy = "tag", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ArticleTagPercent> articleTagPercents = new HashSet<>();

    // ───────────────────────────────────────────────
    // Конструкторы
    // ───────────────────────────────────────────────

    public Tag() {
    }

    public Tag(String name) {
        this.name = name;
    }

    public TagKind getKind() { return kind; }
    public void setKind(TagKind kind) { this.kind = kind; }

    public Tag getParent() { return parent; }
    public void setParent(Tag parent) { this.parent = parent; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getTagKey() { return tagKey; }
    public void setTagKey(String tagKey) { this.tagKey = tagKey; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    // ───────────────────────────────────────────────
    // Геттеры и сеттеры
    // ───────────────────────────────────────────────

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<UserTagPercent> getUserTagPercents() {
        return userTagPercents;
    }

    public void setUserTagPercents(Set<UserTagPercent> userTagPercents) {
        this.userTagPercents = userTagPercents;
    }

    public Set<ArticleTagPercent> getArticleTagPercents() {
        return articleTagPercents;
    }

    public void setArticleTagPercents(Set<ArticleTagPercent> articleTagPercents) {
        this.articleTagPercents = articleTagPercents;
    }

    // ───────────────────────────────────────────────
    // Удобные методы для управления связями (рекомендуется)
    // ───────────────────────────────────────────────

    // Для articleTagPercents
    public void addArticleTagPercent(ArticleTagPercent atp) {
        this.articleTagPercents.add(atp);
        atp.setTag(this);
    }

    public void removeArticleTagPercent(ArticleTagPercent atp) {
        this.articleTagPercents.remove(atp);
        atp.setTag(null);
    }

    // Для userTagPercents
    public void addUserTagPercent(UserTagPercent utp) {
        this.userTagPercents.add(utp);
        utp.setTag(this);
    }

    public void removeUserTagPercent(UserTagPercent utp) {
        this.userTagPercents.remove(utp);
        utp.setTag(null);
    }

    // ───────────────────────────────────────────────
    // Полезные переопределения (для отладки и логов)
    // ───────────────────────────────────────────────

    @Override
    public String toString() {
        return "Tag{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", articleCount=" + (articleTagPercents != null ? articleTagPercents.size() : 0) +
                ", userCount=" + (userTagPercents != null ? userTagPercents.size() : 0) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tag tag = (Tag) o;
        return id == tag.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}