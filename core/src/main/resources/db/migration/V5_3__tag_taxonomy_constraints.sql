-- V5_3: Tighten tag_key, add FK constraints.
--
-- Preconditions (verified after V5_2):
--   - tag_key is filled for all 257 ENTITY rows (no NULLs, no duplicates).
--   - tag.region is NULL for all 257 ENTITYs → fk_tag_region is safe to add.
--   - region table is empty here; FK on article.article_region added in V5_4 after seed.
--
-- fk_tag_parent has no ON DELETE clause to avoid MySQL error 3823
-- (CHECK constraint on same table columns + FK with ON DELETE = parser conflict).

ALTER TABLE `tag` MODIFY COLUMN `tag_key` VARCHAR(150) NOT NULL;
ALTER TABLE `tag` ADD UNIQUE INDEX `uq_tag_key` (`tag_key`);

ALTER TABLE `tag`
  ADD CONSTRAINT `fk_tag_parent`
    FOREIGN KEY (`parent_id`) REFERENCES `tag`(`id`);

ALTER TABLE `tag`
  ADD CONSTRAINT `fk_tag_region`
    FOREIGN KEY (`region`) REFERENCES `region`(`region_key`);

ALTER TABLE `tag`
  ADD CONSTRAINT `chk_tag_parent`
    CHECK (
      (`kind` = 'SUBTOPIC' AND `parent_id` IS NOT NULL) OR
      (`kind` != 'SUBTOPIC' AND `parent_id` IS NULL)
    );
