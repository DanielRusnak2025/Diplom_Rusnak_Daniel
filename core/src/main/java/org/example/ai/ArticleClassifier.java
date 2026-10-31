package org.example.ai;

import java.util.Collection;

public interface ArticleClassifier {
    ArticleClassification classify(String text, Collection<TagInfo> taxonomy)
            throws ClassificationException;
}
