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

    public Region() {}

    public String getRegionKey() { return regionKey; }
    public void setRegionKey(String regionKey) { this.regionKey = regionKey; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
