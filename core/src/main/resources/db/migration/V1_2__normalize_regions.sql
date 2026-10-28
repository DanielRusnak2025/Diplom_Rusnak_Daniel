-- V1_3: Normalize article_region free text to taxonomy keys.
-- After this migration scheduler.enabled must stay false until Stage 3
-- (old pipeline writes free-text region values).

-- Single-country / single-region mappings (unambiguous)
UPDATE article SET article_region = 'east_southeast_asia' WHERE article_region = 'China';
UPDATE article SET article_region = 'western_europe'      WHERE article_region = 'Germany';
UPDATE article SET article_region = 'eastern_europe'      WHERE article_region = 'Hungary';
UPDATE article SET article_region = 'middle_east'         WHERE article_region = 'Iran';
UPDATE article SET article_region = 'middle_east'         WHERE article_region = 'Israel';
UPDATE article SET article_region = 'middle_east'         WHERE article_region = 'Lebanon';
UPDATE article SET article_region = 'middle_east'         WHERE article_region = 'Middle East';
UPDATE article SET article_region = 'eastern_europe'      WHERE article_region = 'Moldova';
UPDATE article SET article_region = 'russia_central_asia' WHERE article_region = 'Russia';
UPDATE article SET article_region = 'russia_central_asia' WHERE article_region = 'Russia (the Amur region)';
UPDATE article SET article_region = 'eastern_europe'      WHERE article_region = 'Russia and Ukraine';
UPDATE article SET article_region = 'eastern_europe'      WHERE article_region = 'Transnistria';
UPDATE article SET article_region = 'north_america'       WHERE article_region = 'United States';
UPDATE article SET article_region = 'north_america'       WHERE article_region = 'United States of America';

-- 'Europe': three articles. The hantavirus/cruise-ship article is global;
-- the remaining two (lifestyle topics) are western_europe.
UPDATE article SET article_region = 'global'
WHERE article_region = 'Europe' AND title LIKE '%hantavirus%';
UPDATE article SET article_region = 'western_europe'
WHERE article_region = 'Europe';

-- Safety net: any value not yet mapped -> global
UPDATE article SET article_region = 'global'
WHERE article_region NOT IN (
    'north_america','latin_america','western_europe','northern_europe',
    'southern_europe','eastern_europe','russia_central_asia','middle_east',
    'africa','south_asia','east_southeast_asia','oceania','global');