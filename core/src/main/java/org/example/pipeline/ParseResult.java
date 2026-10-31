package org.example.pipeline;

public record ParseResult(
        String title,
        String publisher,
        String publishedDate,
        String canonicalUrl,
        String textForAi
) {}
