package org.example.ai;

import java.util.List;

public record ArticleClassification(
    boolean isArticle,
    Integer mainCategoryId,
    Integer mainSubtopicId,
    List<ExtraAssignment> extra
) {
    public ArticleClassification {
        extra = extra == null ? List.of() : List.copyOf(extra);
    }

    public static ArticleClassification notAnArticle() {
        return new ArticleClassification(false, null, null, List.of());
    }
}
