package org.example;

import jakarta.persistence.*;
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

    @Column(name = "article_language", length = 50)
    private String articleLanguage;

    @Column(name = "article_region", length = 100)
    private String articleRegion;

    @Temporal(TemporalType.DATE)
    @Column(name = "publication_date")
    private Date publicationDate;

    @Column(name = "read_count")
    private int readCount;

    @Column(length = 255)
    private String publisher;

    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ArticleTagPercent> articleTagPercents = new HashSet<>();

    @OneToMany(mappedBy = "article")
    private Set<Report> reports = new HashSet<>();

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

    public int getReadCount() {
        return readCount;
    }

    public void setReadCount(int readCount) {
        this.readCount = readCount;
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