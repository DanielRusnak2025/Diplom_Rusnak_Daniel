package org.example.ai;

import java.util.List;

public record TagInfo(int id, String name, String description, List<SubtopicInfo> subtopics) {
    public TagInfo {
        subtopics = subtopics == null ? List.of() : List.copyOf(subtopics);
    }
}
