package org.example;

import jakarta.persistence.*;

@jakarta.persistence.Entity
@Table(name = "entity_alias")
public class EntityAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entity_id", nullable = false)
    private Entity entity;

    @Column(name = "language", length = 5)
    private String language;

    @Column(name = "pattern", length = 150, nullable = false)
    private String pattern;

    @Column(name = "is_acronym", nullable = false)
    private boolean acronym;

    public EntityAlias() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Entity getEntity() { return entity; }
    public void setEntity(Entity entity) { this.entity = entity; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }

    public boolean isAcronym() { return acronym; }
    public void setAcronym(boolean acronym) { this.acronym = acronym; }
}
