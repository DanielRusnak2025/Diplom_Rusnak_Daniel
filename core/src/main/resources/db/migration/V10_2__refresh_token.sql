CREATE TABLE refresh_token (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    user_id         INT           NOT NULL,
    token_hash      CHAR(64)      NOT NULL,
    family_id       CHAR(36)      NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    expires_at      DATETIME(6)   NOT NULL,
    revoked_at      DATETIME(6)   NULL,
    replaced_by_id  BIGINT        NULL,
    CONSTRAINT pk_refresh_token PRIMARY KEY (id),
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_rt_user
        FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE,
    CONSTRAINT fk_rt_replaced_by
        FOREIGN KEY (replaced_by_id) REFERENCES refresh_token (id) ON DELETE SET NULL
);
