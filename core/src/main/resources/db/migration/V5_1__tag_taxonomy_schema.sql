-- V5_1: Add taxonomy columns to tag, create region and tag_keyword tables.
--
-- Notes:
--   - SHOW INDEX FROM tag (pre-migration) shows only PRIMARY — no unique index on name.
--     @Column(unique=true) in Tag.java was never applied via Flyway; annotation will be removed.
--   - tag.kind DEFAULT 'ENTITY' means all 257 existing tags automatically become ENTITY.
--   - tag_key is NULL here; V5_2 fills it for existing ENTITYs; V5_3 adds NOT NULL + UNIQUE.
--   - region table is created empty; region rows are seeded in V5_4.
--   - FK tag.region → region is added in V5_3 (safe: all existing rows have region=NULL).
--   - FK article.article_region → region is added at end of V5_4 (after region rows exist).

CREATE TABLE `region` (
  `region_key` VARCHAR(30)  NOT NULL,
  `name`        VARCHAR(100) NOT NULL,
  PRIMARY KEY (`region_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `tag`
  ADD COLUMN `kind`        VARCHAR(20)  NOT NULL DEFAULT 'ENTITY',
  ADD COLUMN `parent_id`   INT          NULL,
  ADD COLUMN `region`      VARCHAR(30)  NULL,
  ADD COLUMN `tag_key`     VARCHAR(150) NULL,
  ADD COLUMN `description` VARCHAR(500) NULL,
  ADD CONSTRAINT `chk_tag_kind` CHECK (`kind` IN ('CATEGORY','SUBTOPIC','ENTITY'));

CREATE TABLE `tag_keyword` (
  `id`       INT          NOT NULL AUTO_INCREMENT,
  `tag_id`   INT          NOT NULL,
  `pattern`  VARCHAR(100) NOT NULL,
  `language` VARCHAR(5)   NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_tk_tag_lang_pat` (`tag_id`, `language`, `pattern`),
  CONSTRAINT `fk_tk_tag` FOREIGN KEY (`tag_id`) REFERENCES `tag`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
