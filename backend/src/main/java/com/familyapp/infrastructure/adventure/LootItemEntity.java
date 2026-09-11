package com.familyapp.infrastructure.adventure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "loot_item")
public class LootItemEntity {

    @Id
    @Column(length = 50)
    private String id;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 16)
    private String rarity;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "asset_key", nullable = false, length = 100)
    private String assetKey;

    @Column(nullable = false)
    private boolean active;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRarity() {
        return rarity;
    }

    public void setRarity(String rarity) {
        this.rarity = rarity;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAssetKey() {
        return assetKey;
    }

    public void setAssetKey(String assetKey) {
        this.assetKey = assetKey;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
