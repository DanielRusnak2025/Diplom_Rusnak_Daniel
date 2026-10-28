-- V1_2: Remove duplicate-link articles; keep MIN(id) per link.
-- Transfers article_tag_percent (GREATEST percent) and report (more recent date wins).
-- UNIQUEs: article_tag_percent(article_id, tag_id), report(user_nick, article_id).

-- Step 1: Raise tag percent on canonical where duplicate has higher value.
UPDATE article_tag_percent can_atp
  JOIN (
    SELECT dups.canonical_id, dup_atp.tag_id, MAX(dup_atp.percent) AS max_pct
    FROM article_tag_percent dup_atp
    JOIN (
      SELECT a.id AS dup_id, grp.canonical_id
      FROM article a
      JOIN (SELECT link, MIN(id) AS canonical_id
            FROM article GROUP BY link HAVING COUNT(*) > 1) grp
        ON a.link = grp.link AND a.id <> grp.canonical_id
    ) dups ON dup_atp.article_id = dups.dup_id
    GROUP BY dups.canonical_id, dup_atp.tag_id
  ) merged
  ON can_atp.article_id = merged.canonical_id
 AND can_atp.tag_id    = merged.tag_id
SET can_atp.percent = GREATEST(can_atp.percent, merged.max_pct);

-- Step 2: Copy tags from duplicates absent on canonical.
INSERT INTO article_tag_percent (article_id, tag_id, percent)
SELECT dups.canonical_id, dup_atp.tag_id, MAX(dup_atp.percent)
FROM article_tag_percent dup_atp
JOIN (
  SELECT a.id AS dup_id, grp.canonical_id
  FROM article a
  JOIN (SELECT link, MIN(id) AS canonical_id
        FROM article GROUP BY link HAVING COUNT(*) > 1) grp
    ON a.link = grp.link AND a.id <> grp.canonical_id
) dups ON dup_atp.article_id = dups.dup_id
WHERE NOT EXISTS (
  SELECT 1 FROM article_tag_percent x
  WHERE x.article_id = dups.canonical_id AND x.tag_id = dup_atp.tag_id
)
GROUP BY dups.canonical_id, dup_atp.tag_id;

-- Step 3: Copy reports from duplicates where canonical has no report from that user.
INSERT INTO report (article_id, user_nick, date, rating, reason)
SELECT dups.canonical_id, dup_r.user_nick, dup_r.date, dup_r.rating, dup_r.reason
FROM report dup_r
JOIN (
  SELECT a.id AS dup_id, grp.canonical_id
  FROM article a
  JOIN (SELECT link, MIN(id) AS canonical_id
        FROM article GROUP BY link HAVING COUNT(*) > 1) grp
    ON a.link = grp.link AND a.id <> grp.canonical_id
) dups ON dup_r.article_id = dups.dup_id
WHERE NOT EXISTS (
  SELECT 1 FROM report x
  WHERE x.user_nick = dup_r.user_nick AND x.article_id = dups.canonical_id
);

-- Step 4: Handle report conflicts — if duplicate is more recent, overwrite canonical.
UPDATE report can_r
  JOIN report dup_r ON can_r.user_nick = dup_r.user_nick
  JOIN (
    SELECT a.id AS dup_id, grp.canonical_id
    FROM article a
    JOIN (SELECT link, MIN(id) AS canonical_id
          FROM article GROUP BY link HAVING COUNT(*) > 1) grp
      ON a.link = grp.link AND a.id <> grp.canonical_id
  ) dups ON dup_r.article_id = dups.dup_id
         AND can_r.article_id = dups.canonical_id
SET can_r.date   = dup_r.date,
    can_r.rating = dup_r.rating,
    can_r.reason = dup_r.reason
WHERE dup_r.date > can_r.date;

-- Step 5: Delete tags of duplicate articles.
DELETE atp FROM article_tag_percent atp
JOIN (
  SELECT a.id AS dup_id
  FROM article a
  JOIN (SELECT link, MIN(id) AS canonical_id
        FROM article GROUP BY link HAVING COUNT(*) > 1) grp
    ON a.link = grp.link AND a.id <> grp.canonical_id
) dups ON atp.article_id = dups.dup_id;

-- Step 6: Delete reports on duplicate articles.
DELETE r FROM report r
JOIN (
  SELECT a.id AS dup_id
  FROM article a
  JOIN (SELECT link, MIN(id) AS canonical_id
        FROM article GROUP BY link HAVING COUNT(*) > 1) grp
    ON a.link = grp.link AND a.id <> grp.canonical_id
) dups ON r.article_id = dups.dup_id;

-- Step 7: Delete duplicate articles.
DELETE a FROM article a
JOIN (SELECT link, MIN(id) AS canonical_id
      FROM article GROUP BY link HAVING COUNT(*) > 1) grp
  ON a.link = grp.link AND a.id <> grp.canonical_id;