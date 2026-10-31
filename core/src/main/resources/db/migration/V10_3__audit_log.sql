CREATE TABLE audit_log (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    actor_user_id   INT           NULL,
    action          VARCHAR(50)   NOT NULL,
    target_type     VARCHAR(30)   NULL,
    target_id       VARCHAR(64)   NULL,
    details         VARCHAR(1000) NULL,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_audit_log PRIMARY KEY (id),
    CONSTRAINT fk_al_actor
        FOREIGN KEY (actor_user_id) REFERENCES user (id) ON DELETE SET NULL,
    INDEX idx_audit_log_created (created_at),
    INDEX idx_audit_log_actor (actor_user_id)
);
