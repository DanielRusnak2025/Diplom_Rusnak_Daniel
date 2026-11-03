-- V12_1 — extend reject_reason CHECK constraint to include pre-filter reasons
-- ROOT_URL, NOT_FOUND, TEXT_TOO_SHORT are set by ArticlePreFilter before Ollama is called.

ALTER TABLE article_queue
    DROP CONSTRAINT chk_aq_reject,
    ADD CONSTRAINT chk_aq_reject CHECK (reject_reason IS NULL OR reject_reason IN (
        'NOT_ARTICLE','DUPLICATE','INVALID_URL','FETCH_FAILED',
        'BLOCKED_DOMAIN','LANGUAGE_NOT_SUPPORTED',
        'ROOT_URL','NOT_FOUND','TEXT_TOO_SHORT'));
