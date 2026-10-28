-- V1_4: Normalize article_language full words to ISO 639-1 codes.
-- Current distinct values: English (42), Russian (29), German (2), Chinese (1).
UPDATE article SET article_language = 'en' WHERE article_language = 'English';
UPDATE article SET article_language = 'ru' WHERE article_language = 'Russian';
UPDATE article SET article_language = 'de' WHERE article_language = 'German';
UPDATE article SET article_language = 'zh' WHERE article_language = 'Chinese';