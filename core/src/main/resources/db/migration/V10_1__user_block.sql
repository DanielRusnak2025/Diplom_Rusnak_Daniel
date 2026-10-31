CREATE TABLE user_block (
    id          INT           NOT NULL AUTO_INCREMENT,
    user_id     INT           NOT NULL,
    block_type  VARCHAR(10)   NOT NULL,
    tag_id      INT           NULL,
    region_key  VARCHAR(30)   NULL,
    publisher   VARCHAR(255)  NULL,
    created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    block_key   VARCHAR(310)  GENERATED ALWAYS AS (
                    CONCAT(block_type, ':', COALESCE(CAST(tag_id AS CHAR(10)), region_key, publisher, ''))
                ) VIRTUAL NOT NULL,
    CONSTRAINT pk_user_block PRIMARY KEY (id),
    CONSTRAINT fk_ub_user
        FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE,
    CONSTRAINT fk_ub_tag
        FOREIGN KEY (tag_id) REFERENCES tag (id),
    CONSTRAINT fk_ub_region
        FOREIGN KEY (region_key) REFERENCES region (region_key),
    CONSTRAINT uq_user_block
        UNIQUE (user_id, block_key),
    CONSTRAINT chk_ub_block_type
        CHECK (block_type IN ('TAG', 'REGION', 'PUBLISHER')),
    CONSTRAINT chk_ub_target
        CHECK (
            (block_type = 'TAG'       AND tag_id IS NOT NULL     AND region_key IS NULL AND publisher IS NULL)
         OR (block_type = 'REGION'    AND region_key IS NOT NULL  AND tag_id IS NULL    AND publisher IS NULL)
         OR (block_type = 'PUBLISHER' AND publisher IS NOT NULL   AND tag_id IS NULL    AND region_key IS NULL)
        )
);
