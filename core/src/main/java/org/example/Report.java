package org.example;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "reason_code", length = 30)
    private ReportReason reasonCode;

    public Report() {
    }

    public Report(Article article, User user, int rating) {
        this.article = article;
        this.user = user;
        this.rating = rating;
        this.date = new Date();
    }

    public Report(Article article, User user, int rating, ReportReason reasonCode) {
        this.article = article;
        this.user = user;
        this.rating = rating;
        this.reasonCode = reasonCode;
        this.date = new Date();
    }

    @PrePersist
    protected void onCreate() {
        if (this.date == null) {
            this.date = new Date();
        }
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Date getDate() { return date; }
    public void setDate(Date date) { this.date = date; }

    public Article getArticle() { return article; }
    public void setArticle(Article article) { this.article = article; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }

    public ReportReason getReasonCode() { return reasonCode; }
    public void setReasonCode(ReportReason reasonCode) { this.reasonCode = reasonCode; }
}
