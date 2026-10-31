ALTER TABLE article
  MODIFY  link_hash  CHAR(64)    NOT NULL,
  ADD     UNIQUE KEY uq_article_link_hash (link_hash),
  MODIFY  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  ADD     INDEX idx_article_created (created_at),
  DROP    COLUMN read_count;
