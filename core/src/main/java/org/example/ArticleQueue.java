package org.example;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "article_queue")
public class ArticleQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(length = 2048, nullable = false)
    private String url;

    @Column(name = "url_hash", length = 64, nullable = false, unique = true)
    private String urlHash;

    @Column(name = "email_id")
    private Integer emailId;

    @Column(name = "article_id")
    private Integer articleId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 20, nullable = false)
    private QueueStatus status = QueueStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "reject_reason", length = 30)
    private RejectReason rejectReason;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "lock_token", length = 36)
    private String lockToken;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    // DB DEFAULT CURRENT_TIMESTAMP(6); Hibernate never inserts/updates this column.
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public ArticleQueue() {}

    public int getId()                   { return id; }
    public String getUrl()               { return url; }
    public String getUrlHash()           { return urlHash; }
    public Integer getEmailId()          { return emailId; }
    public Integer getArticleId()        { return articleId; }
    public QueueStatus getStatus()       { return status; }
    public RejectReason getRejectReason(){ return rejectReason; }
    public int getAttempts()             { return attempts; }
    public String getLastError()         { return lastError; }
    public String getLockToken()         { return lockToken; }
    public LocalDateTime getLockedUntil(){ return lockedUntil; }
    public LocalDateTime getCreatedAt()  { return createdAt; }
    public LocalDateTime getProcessedAt(){ return processedAt; }

    public void setUrl(String v)              { this.url = v; }
    public void setUrlHash(String v)          { this.urlHash = v; }
    public void setEmailId(Integer v)         { this.emailId = v; }
    public void setArticleId(Integer v)       { this.articleId = v; }
    public void setStatus(QueueStatus v)      { this.status = v; }
    public void setRejectReason(RejectReason v){ this.rejectReason = v; }
    public void setAttempts(int v)            { this.attempts = v; }
    public void setLastError(String v)        { this.lastError = v; }
    public void setLockToken(String v)        { this.lockToken = v; }
    public void setLockedUntil(LocalDateTime v){ this.lockedUntil = v; }
    public void setProcessedAt(LocalDateTime v){ this.processedAt = v; }
}
