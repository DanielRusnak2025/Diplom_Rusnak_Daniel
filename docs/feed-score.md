# Feed Score & matchPercent

## Raw score

```
score = SUM(atp.percent * utp.percent)
```

Summed over the LEFT JOIN of `article_tag_percent` (article's tag weights) and
`user_tag_percent` (user's interest weights) on `tag_id`. Because `user_tag_percent`
stores only CATEGORY interests, the sum covers only CATEGORY tags.

## matchPercent (0–100, nullable)

```
matchPercent = score / MAX(utp.percent over user's CATEGORY interests)
               clamped to 0..100
```

Denominator is the user's **maximum** CATEGORY weight, not the sum.
This means 100 % = article is fully (100 %) in the user's strongest category.

Returns `null` when the user has no interests (denominator = 0).

### Examples

| Article tag % | User weight for tag | User max weight | matchPercent |
|:---:|:---:|:---:|:---:|
| 60 | 50 | 100 | 30 |
| 100 | 75 | 75 | 100 |
| any | — | 0 | null |

### Why MAX, not SUM?

Using SUM as denominator would divide by a value that grows with the number of
interest categories, making articles that happen to match a niche secondary
interest look weaker than they are. MAX anchors the scale to the user's
strongest interest: a perfect match there always scores 100 %.
