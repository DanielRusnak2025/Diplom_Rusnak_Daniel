package org.example;

import jakarta.persistence.*;
import java.util.Date;

@Entity
@Table(name = "report",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_report_user_article",
               columnNames = {"user_id", "article_id"}
       ))
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Temporal(TemporalType.DATE)
    @Column(nullable = false)
    private Date date;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int rating;

    @Column(columnDefinition = "TEXT")
    private String reason;

    // ───────────────────────────────────────────────
    // Конструкторы
    // ───────────────────────────────────────────────

    public Report() {
    }

    public Report(Article article, User user, int rating, String reason) {
        this.article = article;
        this.user = user;
        this.rating = rating;
        this.reason = reason;
        this.date = new Date();           // текущая дата по умолчанию
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

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public Article getArticle() {
        return article;
    }

    public void setArticle(Article article) {
        this.article = article;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    // ───────────────────────────────────────────────
    // Удобные методы (опционально)
    // ───────────────────────────────────────────────

    /**
     * Устанавливает текущую дату автоматически
     */
    @PrePersist
    protected void onCreate() {
        if (this.date == null) {
            this.date = new Date();
        }
    }

}