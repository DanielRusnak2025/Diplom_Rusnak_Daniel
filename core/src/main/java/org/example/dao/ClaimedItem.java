package org.example.dao;

public record ClaimedItem(int id, String url, Integer articleId, String lockToken) {}
