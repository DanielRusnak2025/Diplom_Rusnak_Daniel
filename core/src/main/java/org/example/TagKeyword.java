package org.example;

import jakarta.persistence.*;

@Entity
@Table(name = "tag_keyword")
public class TagKeyword {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;

    @Column(length = 100, nullable = false)
    private String pattern;

    @Column(length = 5, nullable = false)
    private String language;

    public TagKeyword() {}

    public int getId() { return id; }

    public Tag getTag() { return tag; }
    public void setTag(Tag tag) { this.tag = tag; }

    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
}
