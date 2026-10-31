CREATE TABLE entity (
    id          INT          NOT NULL AUTO_INCREMENT,
    entity_key  VARCHAR(80)  NOT NULL,
    kind        VARCHAR(20)  NOT NULL,
    region_key  VARCHAR(30)  NULL,
    country_id  INT          NULL,
    CONSTRAINT pk_entity      PRIMARY KEY (id),
    CONSTRAINT uq_entity_key  UNIQUE (entity_key),
    CONSTRAINT ck_entity_kind CHECK (kind IN ('COUNTRY','PLACE','ORGANISATION','REGION_NAME')),
    CONSTRAINT ck_entity_refs CHECK (
        (kind = 'PLACE'  AND country_id IS NOT NULL AND region_key IS NULL) OR
        (kind != 'PLACE' AND region_key IS NOT NULL AND country_id IS NULL)
    ),
    CONSTRAINT fk_entity_region
        FOREIGN KEY (region_key) REFERENCES region (region_key),
    CONSTRAINT fk_entity_country
        FOREIGN KEY (country_id) REFERENCES entity (id),
    INDEX idx_entity_kind   (kind),
    INDEX idx_entity_region (region_key)
);

CREATE TABLE entity_alias (
    id          INT          NOT NULL AUTO_INCREMENT,
    entity_id   INT          NOT NULL,
    language    VARCHAR(5)   NULL,
    pattern     VARCHAR(150) NOT NULL,
    is_acronym  BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_entity_alias     PRIMARY KEY (id),
    CONSTRAINT uq_entity_alias     UNIQUE (entity_id, language, pattern),
    CONSTRAINT fk_alias_entity
        FOREIGN KEY (entity_id) REFERENCES entity (id) ON DELETE CASCADE,
    INDEX idx_alias_entity   (entity_id),
    INDEX idx_alias_language (language)
);
