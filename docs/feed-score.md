# Feed Score & matchPercent

## Raw score

```
score = SUM(atp.percent * utp.percent)
```

Summed over the LEFT JOIN of `article_tag_percent` (article's tag weights) and
`user_tag_percent` (user's interest weights) on `tag_id`. This JOIN includes ALL
tag kinds (CATEGORY and SUBTOPIC), because rating feedback via `TagPercentService`
writes `user_tag_percent` rows for every tag kind an article carries.

`score` is used for feed ordering only.

## matchPercent (0–100, nullable)

```
matchPercent = category_score / MAX(utp.percent over user's CATEGORY interests)
               clamped to 0..100
```

Where `category_score` is the same dot-product restricted to CATEGORY tags:

```
category_score = SUM(atp.percent * utp.percent)  [CATEGORY tags only]
```

Denominator is the user's **maximum** CATEGORY weight, not the sum.
This means 100 % = article is fully (100 %) in the user's strongest category.

Returns `null` when the user has no CATEGORY interests (denominator = 0).

### Why CATEGORY-only for matchPercent?

`user_tag_percent` can hold SUBTOPIC rows (created when a user rates an article,
since `applyWeightSteps` iterates all article tags). Using the all-tag `score` as
the numerator would inflate `matchPercent` beyond what CATEGORY-only interests justify.
`category_score` keeps the display metric aligned with the user's declared topic interests.

### Examples

| Article cat % | User cat weight | User max cat weight | matchPercent |
|:---:|:---:|:---:|:---:|
| 60 | 50 | 100 | 30 |
| 100 | 75 | 75 | 100 |
| any | — | 0 | null |

### Why MAX, not SUM as denominator?

Using SUM as denominator would divide by a value that grows with the number of
interest categories, making articles that happen to match a niche secondary
interest look weaker than they are. MAX anchors the scale to the user's
strongest interest: a perfect match there always scores 100 %.
