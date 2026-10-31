package org.example;

import jakarta.persistence.*;
import org.example.util.UrlNormalizer;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "article")
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(length = 500)
    private String title;

    @Column(length = 2000)
    private String link;

    @Column(name = "link_hash", length = 64)
    private String linkHash;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "classifier_model", length = 100)
    private String classifierModel;

    @Column(name = "prompt_version", length = 20)
    private String promptVersion;

    @Column(name = "taxonomy_version")
    private Integer taxonomyVersion;

    @Column(name = "article_language", length = 50)
    private String articleLanguage;

    @Column(name = "article_region", length = 100)
    private String articleRegion;

    @Temporal(TemporalType.DATE)
    @Column(name = "publication_date")
    private Date publicationDate;

    @Column(length = 255)
    private String publisher;

    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ArticleTagPercent> articleTagPercents = new HashSet<>();

    @OneToMany(mappedBy = "article")
    private Set<Report> reports = new HashSet<>();

    // ───────────────────────────────────────────────
    // Lifecycle callbacks
    // ───────────────────────────────────────────────

    @PrePersist
    void onPersist() {
        if (link != null) {
            linkHash = UrlNormalizer.hash(UrlNormalizer.normalize(link));
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        if (link != null) {
            linkHash = UrlNormalizer.hash(UrlNormalizer.normalize(link));
        }
    }

    // ───────────────────────────────────────────────
    // Геттеры и сеттеры
    // ───────────────────────────────────────────────

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getLinkHash() {
        return linkHash;
    }

    public void setLinkHash(String linkHash) {
        this.linkHash = linkHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getClassifierModel() {
        return classifierModel;
    }

    public void setClassifierModel(String classifierModel) {
        this.classifierModel = classifierModel;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public Integer getTaxonomyVersion() {
        return taxonomyVersion;
    }

    public void setTaxonomyVersion(Integer taxonomyVersion) {
        this.taxonomyVersion = taxonomyVersion;
    }

    public String getArticleLanguage() {
        return articleLanguage;
    }

    public void setArticleLanguage(String articleLanguage) {
        this.articleLanguage = articleLanguage;
    }

    public String getArticleRegion() {
        return articleRegion;
    }

    public void setArticleRegion(String articleRegion) {
        this.articleRegion = articleRegion;
    }

    public Date getPublicationDate() {
        return publicationDate;
    }

    public void setPublicationDate(Date publicationDate) {
        this.publicationDate = publicationDate;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public Set<ArticleTagPercent> getArticleTagPercents() {
        return articleTagPercents;
    }

    public void setArticleTagPercents(Set<ArticleTagPercent> articleTagPercents) {
        this.articleTagPercents = articleTagPercents;
    }

}