package org.example;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.ArrayList;
import java.util.List;

@jakarta.persistence.Entity
@Table(name = "entity")
public class Entity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "entity_key", length = 80, nullable = false, unique = true)
    private String entityKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "kind", length = 20, nullable = false)
    private EntityKind kind;

    @Column(name = "region_key", length = 30)
    private String regionKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "country_id")
    private Entity country;

    @OneToMany(mappedBy = "entity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EntityAlias> aliases = new ArrayList<>();

    public Entity() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getEntityKey() { return entityKey; }
    public void setEntityKey(String entityKey) { this.entityKey = entityKey; }

    public EntityKind getKind() { return kind; }
    public void setKind(EntityKind kind) { this.kind = kind; }

    public String getRegionKey() { return regionKey; }
    public void setRegionKey(String regionKey) { this.regionKey = regionKey; }

    public Entity getCountry() { return country; }
    public void setCountry(Entity country) { this.country = country; }

    public List<EntityAlias> getAliases() { return aliases; }
    public void setAliases(List<EntityAlias> aliases) { this.aliases = aliases; }
}
