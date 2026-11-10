-- Extend article_queue.reject_reason CHECK constraint to include DW_SECTION_PAGE.
-- Pattern: drop the existing named constraint, then re-add it with the new value.
ALTER TABLE article_queue
    DROP CONSTRAINT chk_aq_reject,
    ADD CONSTRAINT chk_aq_reject CHECK (reject_reason IS NULL OR reject_reason IN (
        'NOT_ARTICLE','DUPLICATE','INVALID_URL','FETCH_FAILED',
        'BLOCKED_DOMAIN','LANGUAGE_NOT_SUPPORTED',
        'ROOT_URL','NOT_FOUND','TEXT_TOO_SHORT','NEWSLETTER_HOST',
        'DW_SECTION_PAGE'));

-- Shorten big_tech_platforms Russian name.
UPDATE tag SET name_ru = 'Бигтех и платформы' WHERE tag_key = 'sub:technology/big_tech_platforms';
