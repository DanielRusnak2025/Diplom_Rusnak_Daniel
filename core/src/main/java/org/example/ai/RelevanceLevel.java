package org.example.ai;

public enum RelevanceLevel {
    HIGH(3), MEDIUM(2), LOW(1);

    public final int weight;

    RelevanceLevel(int weight) { this.weight = weight; }
}
