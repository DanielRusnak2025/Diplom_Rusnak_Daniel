-- V7_1 — processed_email + article_queue
-- processed_email: deduplication log for incoming emails (populated by V8+ pipeline).
-- article_queue:   work queue for URL fetch/classify pipeline.

CREATE TABLE processed_email (
    id               INT          AUTO_INCREMENT PRIMARY KEY,
    message_id_hash  CHAR(64)     NOT NULL,
    sender_domain    VARCHAR(253) NOT NULL,
    received_at      DATETIME(6)  NOT NULL,
    processed_at     DATETIME(6)  NOT NULL,
    links_total      INT          NOT NULL DEFAULT 0,
    links_rejected   INT          NOT NULL DEFAULT 0,
    links_saved      INT          NOT NULL DEFAULT 0,
    CONSTRAINT uq_pe_hash UNIQUE KEY (message_id_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE article_queue (
    id            INT           AUTO_INCREMENT PRIMARY KEY,
    url           VARCHAR(2048) NOT NULL,
    url_hash      CHAR(64)      NOT NULL,
    email_id      INT           NULL,
    article_id    INT           NULL,
    status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    reject_reason VARCHAR(30)   NULL,
    attempts      INT           NOT NULL DEFAULT 0,
    last_error    VARCHAR(500)  NULL,
    lock_token    CHAR(36)      NULL,
    locked_until  DATETIME(6)   NULL,
    created_at    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    processed_at  DATETIME(6)   NULL,
    CONSTRAINT uq_aq_url_hash  UNIQUE KEY (url_hash),
    CONSTRAINT chk_aq_status   CHECK (status IN
        ('PENDING','PROCESSING','DONE','REJECTED','FAILED')),
    CONSTRAINT chk_aq_reject   CHECK (reject_reason IS NULL OR reject_reason IN (
        'NOT_ARTICLE','DUPLICATE','INVALID_URL','FETCH_FAILED',
        'BLOCKED_DOMAIN','LANGUAGE_NOT_SUPPORTED')),
    CONSTRAINT fk_aq_email     FOREIGN KEY (email_id)   REFERENCES processed_email(id) ON DELETE SET NULL,
    CONSTRAINT fk_aq_article   FOREIGN KEY (article_id) REFERENCES article(id)          ON DELETE SET NULL,
    INDEX idx_aq_status_created (status, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
