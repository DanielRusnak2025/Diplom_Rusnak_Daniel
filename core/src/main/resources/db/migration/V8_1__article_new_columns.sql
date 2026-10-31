ALTER TABLE article
  ADD COLUMN link_hash        CHAR(64)     NULL AFTER link,
  ADD COLUMN created_at       DATETIME(6)  NULL AFTER link_hash,
  ADD COLUMN classifier_model VARCHAR(100) NULL AFTER created_at,
  ADD COLUMN prompt_version   VARCHAR(20)  NULL AFTER classifier_model,
  ADD COLUMN taxonomy_version INT          NULL AFTER prompt_version;
