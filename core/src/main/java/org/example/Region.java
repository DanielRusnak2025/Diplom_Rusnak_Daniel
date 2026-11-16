package org.example;

import jakarta.persistence.*;

@Entity
@Table(name = "region")
public class Region {

    @Id
    @Column(name = "region_key", length = 30)
    private String regionKey;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(name = "name_ru", length = 100)
    private String nameRu;

    public Region() {}

    public String getRegionKey() { return regionKey; }
    public void setRegionKey(String regionKey) { this.regionKey = regionKey; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getNameRu() { return nameRu; }
    public void setNameRu(String nameRu) { this.nameRu = nameRu; }
}
